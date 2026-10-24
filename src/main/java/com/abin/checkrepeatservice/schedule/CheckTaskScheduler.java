package com.abin.checkrepeatservice.schedule;

import com.abin.checkrepeatservice.admin.dto.PerformanceConfigDTO;
import com.abin.checkrepeatservice.admin.service.SystemConfigService;
import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.common.statemachine.CheckTaskStateMachine;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.student.event.CheckTaskCreatedEvent;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 查重任务定时任务
 * 负责清理超时任务、状态监控等
 */
@Component
@Slf4j
public class CheckTaskScheduler {

    @Autowired
    private CheckTaskStateMachine stateMachine;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private CheckTaskMapper checkTaskMapper;

    // M4.1：超时失败的任务同样返还已扣额度
    @Autowired
    private com.abin.checkrepeatservice.admin.service.CollegeQuotaService collegeQuotaService;

    /** 超时任务扫描开关，默认开（S13 起）：把超时任务标记为失败（写库）；application.yml 可覆盖关闭 */
    @Value("${schedule.check-task.timeout-scan-enabled:true}")
    private boolean timeoutScanEnabled;

    /** checking 任务超时阈值（毫秒），默认 1 小时 */
    @Value("${check.task.timeout:3600000}")
    private long checkingTimeoutMs;

    /** pending 任务排队超时阈值（毫秒），默认 15 分钟 */
    @Value("${check.task.pending-timeout:900000}")
    private long pendingTimeoutMs;

    /** pending 超时后允许重新投递的最大次数，默认 1 次；超过后置 failure 并返还额度 */
    @Value("${check.task.pending-max-retry:1}")
    private int pendingMaxRetry;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** 多实例防重：Redisson 未装配时退化为无锁（条件更新仍保证幂等） */
    @Autowired(required = false)
    private RedissonClient redissonClient;

    private static final String TIMEOUT_SCAN_LOCK = "scheduler:check-task-timeout";

    /**
     * 上次自动清理时间。初始化为应用启动时间（而不是 null），
     * 保证首次清理在启动 cleanupInterval 小时后才发生——沿用本类
     * 「不让上线瞬间就按历史数据改库」的保守约定。
     */
    private volatile LocalDateTime lastCleanupAt = LocalDateTime.now();

    /**
     * 每 5 分钟执行一次自愈扫描（阶段 3）：
     * <ol>
     *   <li><b>checking 超时</b>：start_time 早于 timeout 阈值 → CHECKING -> FAILURE + 返还额度；</li>
     *   <li><b>pending 超时</b>：create_time 早于 pending 阈值且重试次数未达上限 →
     *       条件递增 retry_count 并重新投递 CheckTaskCreatedEvent；已达上限 → PENDING -> FAILURE + 返还额度。</li>
     * </ol>
     * 所有状态变更走 transitionStatus 条件抢占，返还走 refundForFailedTask 幂等闸门；
     * 多实例用 Redisson 锁串行化，抢不到锁的实例直接跳过本轮。
     */
    @Scheduled(fixedRate = 300000) // 5分钟执行一次
    public void checkTimeoutTasks() {
        if (!timeoutScanEnabled) {
            log.debug("超时任务扫描已关闭（schedule.check-task.timeout-scan-enabled=false），跳过");
            return;
        }

        RLock lock = redissonClient == null ? null : redissonClient.getLock(TIMEOUT_SCAN_LOCK);
        boolean locked = false;
        try {
            if (lock != null) {
                // waitTime=0：抢不到立即返回，不阻塞调度线程；leaseTime 覆盖本轮扫描耗时
                locked = lock.tryLock(0, 4, TimeUnit.MINUTES);
                if (!locked) {
                    log.info("另一实例正在执行查重超时扫描，跳过本轮");
                    return;
                }
            }

            LocalDateTime now = LocalDateTime.now();
            recoverCheckingTimeouts(now);
            recoverPendingTimeouts(now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("查重超时扫描获取分布式锁被中断，跳过本轮");
        } catch (Exception e) {
            log.error("检查超时任务失败", e);
        } finally {
            if (lock != null && locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 回收执行中超时任务：逐任务 CHECKING -> FAILURE（条件抢占），
     * 只有真正抢到状态的一方执行额度返还，避免与消费端成功态竞态。
     */
    private void recoverCheckingTimeouts(LocalDateTime now) {
        long timeoutMillis = Math.max(60_000L, checkingTimeoutMs);
        LocalDateTime deadline = now.minus(Duration.ofMillis(timeoutMillis));

        List<CheckTask> timeoutTasks = checkTaskMapper.selectList(new LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getCheckStatus, CheckTaskStatusEnum.CHECKING.getCode())
                .lt(CheckTask::getStartTime, deadline));

        int failed = 0;
        for (CheckTask task : timeoutTasks) {
            boolean transitioned = stateMachine.transitionStatus(
                    task.getId(),
                    CheckTaskStatusEnum.CHECKING,
                    CheckTaskStatusEnum.FAILURE,
                    "任务执行超时（超过" + (timeoutMillis / 60000) + "分钟）");
            if (transitioned) {
                failed++;
                collegeQuotaService.refundForFailedTask(task.getId());
            }
        }
        if (failed > 0) {
            log.warn("发现{}个 checking 超时任务，已置失败并返还额度", failed);
        }
    }

    /**
     * 回收排队超时任务：
     * 未达重试上限的任务条件递增 retry_count 后重新投递事件（自愈）；
     * 已达上限的任务 PENDING -> FAILURE 并返还额度（止损）。
     */
    private void recoverPendingTimeouts(LocalDateTime now) {
        long timeoutMillis = Math.max(60_000L, pendingTimeoutMs);
        int maxRetry = Math.max(0, pendingMaxRetry);
        LocalDateTime deadline = now.minus(Duration.ofMillis(timeoutMillis));

        List<CheckTask> stalledTasks = checkTaskMapper.selectList(new LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getCheckStatus, CheckTaskStatusEnum.PENDING.getCode())
                .lt(CheckTask::getCreateTime, deadline));

        int requeued = 0;
        int failed = 0;
        for (CheckTask task : stalledTasks) {
            int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();

            if (retry < maxRetry) {
                // 条件递增作为「补偿抢占」：只有仍为 pending 且仍超时的那一方能成功
                int rows = checkTaskMapper.update(null, new LambdaUpdateWrapper<CheckTask>()
                        .eq(CheckTask::getId, task.getId())
                        .eq(CheckTask::getCheckStatus, CheckTaskStatusEnum.PENDING.getCode())
                        .lt(CheckTask::getCreateTime, deadline)
                        .setSql("retry_count = IFNULL(retry_count, 0) + 1")
                        .setSql("version = version + 1"));
                if (rows > 0) {
                    requeued++;
                    // 监听器为 AFTER_COMMIT + fallbackExecution=true，无事务时立即异步执行
                    eventPublisher.publishEvent(new CheckTaskCreatedEvent(
                            this, task.getId(), task.getPaperId()));
                    log.warn("pending 任务排队超时，已重新投递事件 - taskId={}, retry={}",
                            task.getId(), retry + 1);
                }
                continue;
            }

            boolean transitioned = stateMachine.transitionStatus(
                    task.getId(),
                    CheckTaskStatusEnum.PENDING,
                    CheckTaskStatusEnum.FAILURE,
                    "任务排队超时（超过" + (timeoutMillis / 60000) + "分钟未执行）");
            if (transitioned) {
                failed++;
                collegeQuotaService.refundForFailedTask(task.getId());
            }
        }
        if (requeued > 0 || failed > 0) {
            log.warn("pending 超时处理完成：重新投递={}，置失败={}", requeued, failed);
        }
    }

    /**
     * 自动清理 tick：每小时自检一次，按「性能配置」（system_config 表 performance 键）动态执行。
     *
     * <p>生效链路：配置页保存（autoCleanup/cleanupInterval）→ 存入 system_config 表 →
     * 本 tick 每次实时读库判断，<b>保存即生效、无需重启</b>：</p>
     * <ul>
     *   <li>autoCleanup=false 或读配置失败 → 跳过本轮（fail-safe，绝不写库）</li>
     *   <li>距上次清理不足 cleanupInterval 小时 → 跳过</li>
     *   <li>满足条件 → 软删除 30 天前的终态（completed/failure/cancelled）查重任务
     *       （CheckTask 继承 BaseEntity 的 @TableLogic，delete 走 UPDATE is_deleted=1，
     *       可恢复、不物理删数）</li>
     * </ul>
     */
    @Scheduled(fixedDelay = 3600000) // 每小时自检一次
    public void autoCleanupTick() {
        PerformanceConfigDTO config;
        try {
            config = systemConfigService.getPerformanceConfig().getData();
        } catch (Exception e) {
            log.warn("自动清理读取性能配置失败，跳过本轮: {}", e.getMessage());
            return;
        }
        if (config == null || !Boolean.TRUE.equals(config.getAutoCleanup())) {
            log.debug("自动清理已关闭（性能配置 autoCleanup=false），跳过");
            return;
        }
        long intervalHours = config.getCleanupInterval() == null ? 24 : config.getCleanupInterval();
        long hoursSinceLast = Duration.between(lastCleanupAt, LocalDateTime.now()).toHours();
        if (hoursSinceLast < intervalHours) {
            log.debug("距上次自动清理{}小时，未到周期{}小时，跳过", hoursSinceLast, intervalHours);
            return;
        }
        lastCleanupAt = LocalDateTime.now();
        cleanupOldTasks();
    }

    /**
     * 清理 30 天前的终态查重任务（软删除）。
     * 由 {@link #autoCleanupTick()} 按性能配置周期调用；另保留每日凌晨 2 点
     * 的兜底触发，避免 tick 因应用重启导致 lastCleanupAt 被重置而长期不清理。
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点兜底执行
    public void cleanupOldTasks() {
        // 兜底路径也要尊重 autoCleanup 开关
        PerformanceConfigDTO config;
        try {
            config = systemConfigService.getPerformanceConfig().getData();
        } catch (Exception e) {
            log.warn("兜底清理读取性能配置失败，跳过: {}", e.getMessage());
            return;
        }
        if (config == null || !Boolean.TRUE.equals(config.getAutoCleanup())) {
            log.debug("自动清理已关闭（性能配置 autoCleanup=false），兜底清理跳过");
            return;
        }
        try {
            LocalDateTime retentionLine = LocalDateTime.now().minusDays(30);
            LambdaUpdateWrapper<CheckTask> wrapper = new LambdaUpdateWrapper<>();
            wrapper.in(CheckTask::getCheckStatus,
                            CheckTaskStatusEnum.COMPLETED.getCode(),
                            CheckTaskStatusEnum.FAILURE.getCode(),
                            CheckTaskStatusEnum.CANCELLED.getCode())
                   .lt(CheckTask::getEndTime, retentionLine);

            // @TableLogic 逻辑删除：UPDATE check_task SET is_deleted=1 WHERE ... AND is_deleted=0
            int deleted = checkTaskMapper.delete(wrapper);
            lastCleanupAt = LocalDateTime.now();
            if (deleted > 0) {
                log.info("自动清理完成：软删除{}个30天前的终态查重任务", deleted);
            } else {
                log.info("自动清理完成：无满足条件（30天前的终态）的查重任务");
            }
        } catch (Exception e) {
            log.error("清理旧查重任务数据失败", e);
        }
    }

    /**
     * 每30秒统计一次任务状态
     */
    @Scheduled(fixedRate = 30000)
    public void taskStatusStatistics() {
        try {
            // 可以在这里收集各种统计指标，如：
            // - 各状态任务数量
            // - 平均执行时间
            // - 成功率等
            log.debug("任务状态统计检查完成");
        } catch (Exception e) {
            log.error("任务状态统计失败", e);
        }
    }
}
