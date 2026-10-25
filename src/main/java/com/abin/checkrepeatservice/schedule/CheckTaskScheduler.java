package com.abin.checkrepeatservice.schedule;

import com.abin.checkrepeatservice.admin.dto.PerformanceConfigDTO;
import com.abin.checkrepeatservice.admin.service.SystemConfigService;
import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.service.CheckTaskFailureRecoveryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private SystemConfigService systemConfigService;

    @Autowired
    private CheckTaskMapper checkTaskMapper;

    // 超时/排队失败统一走监听器同一套恢复：置失败、退额度、回滚论文、通知学生
    @Autowired
    private CheckTaskFailureRecoveryService failureRecoveryService;

    /** 超时任务扫描开关，默认开（S13 起）：把超时任务标记为失败（写库）；application.yml 可覆盖关闭 */
    @Value("${schedule.check-task.timeout-scan-enabled:true}")
    private boolean timeoutScanEnabled;

    /** checking 任务超时阈值（毫秒），默认 20 分钟 */
    @Value("${check.task.timeout:1200000}")
    private long checkingTimeoutMs;

    /** pending 任务滞留观察阈值（毫秒），默认 2 分钟；仅用于积压告警，不再据此判失败 */
    @Value("${check.task.pending-timeout:120000}")
    private long pendingTimeoutMs;

    /**
     * pending 任务的绝对截止时间（毫秒），默认 24 小时。
     * 只有排队超过该时长才置 failure 并返还额度；容量饱和导致的小时级排队属于正常现象。
     */
    @Value("${check.task.pending-deadline-ms:86400000}")
    private long pendingDeadlineMs;

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
     *   <li><b>pending 滞留</b>：超过 pending-timeout 只记录积压数量（由
     *       {@code CheckTaskDispatchScheduler} 按容量继续捞取），
     *       只有超过 pending-deadline-ms（默认 24h）才 PENDING -> FAILURE + 返还额度。</li>
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
            String reason = "任务执行超时（超过" + (timeoutMillis / 60000) + "分钟）";
            if (failureRecoveryService.failTaskAndRestorePaper(task.getId(), reason)) {
                failed++;
            }
        }
        if (failed > 0) {
            log.warn("发现{}个 checking 超时任务，已置失败、返还额度并恢复论文状态", failed);
        }
    }

    /**
     * pending 任务滞留观察 + 绝对截止时间止损。
     *
     * <p>派发器每 5 秒都会按容量捞取 pending，正常的容量饱和表现为“合法排队”，
     * 不能按 2 分钟就判失败（否则 5000 份的截止日场景会集体失败退额度）。
     * 因此这里做两件事：</p>
     * <ol>
     *   <li>超过 pending-timeout（默认 2 分钟）：只统计并告警积压数量；</li>
     *   <li>超过 pending-deadline-ms（默认 24 小时）：才 PENDING -> FAILURE 并返还额度。</li>
     * </ol>
     */
    private void recoverPendingTimeouts(LocalDateTime now) {
        long staleMillis = Math.max(60_000L, pendingTimeoutMs);
        long deadlineMillis = Math.max(staleMillis, pendingDeadlineMs);
        LocalDateTime staleBefore = now.minus(Duration.ofMillis(staleMillis));
        LocalDateTime absoluteDeadline = now.minus(Duration.ofMillis(deadlineMillis));

        // 1) 绝对截止时间：真正的“永远排不上”才止损
        List<CheckTask> expiredTasks = checkTaskMapper.selectList(new LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getCheckStatus, CheckTaskStatusEnum.PENDING.getCode())
                .lt(CheckTask::getCreateTime, absoluteDeadline));

        int failed = 0;
        for (CheckTask task : expiredTasks) {
            String reason = "任务排队超过" + (deadlineMillis / 60000) + "分钟仍未执行";
            if (failureRecoveryService.failTaskAndRestorePaper(task.getId(), reason)) {
                failed++;
            }
        }
        if (failed > 0) {
            log.warn("pending 任务超过绝对截止时间，已置失败、返还额度并恢复论文状态：{} 个", failed);
        }

        // 2) 滞留观察：只做告警，任务继续留在 pending 由派发器捞取
        Long staleCount = checkTaskMapper.selectCount(new LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getCheckStatus, CheckTaskStatusEnum.PENDING.getCode())
                .lt(CheckTask::getCreateTime, staleBefore));
        if (staleCount != null && staleCount > 0) {
            log.warn("pending 任务滞留超过 {} 秒：{} 个（执行容量饱和时属正常排队）",
                    staleMillis / 1000, staleCount);
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
