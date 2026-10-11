package com.abin.checkrepeatservice.schedule;

import com.abin.checkrepeatservice.admin.dto.PerformanceConfigDTO;
import com.abin.checkrepeatservice.admin.service.SystemConfigService;
import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.common.statemachine.CheckTaskStateMachine;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

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

    /** 超时任务扫描开关，默认关：开启后会把超时任务标记为失败（写库） */
    @Value("${schedule.check-task.timeout-scan-enabled:false}")
    private boolean timeoutScanEnabled;

    /**
     * 上次自动清理时间。初始化为应用启动时间（而不是 null），
     * 保证首次清理在启动 cleanupInterval 小时后才发生——沿用本类
     * 「不让上线瞬间就按历史数据改库」的保守约定。
     */
    private volatile LocalDateTime lastCleanupAt = LocalDateTime.now();

    /**
     * 每5分钟检查一次超时的查重任务
     * 将超过1小时仍在执行中的任务标记为失败
     */
    @Scheduled(fixedRate = 300000) // 5分钟执行一次
    public void checkTimeoutTasks() {
        if (!timeoutScanEnabled) {
            log.debug("超时任务扫描已关闭（schedule.check-task.timeout-scan-enabled=false），跳过");
            return;
        }
        try {
            log.info("开始检查超时查重任务...");

            String condition = "check_status = '" + CheckTaskStatusEnum.CHECKING.getCode() +
                             "' AND start_time < DATE_SUB(NOW(), INTERVAL 1 HOUR)";

            int affected = stateMachine.batchTransitionStatus(
                CheckTaskStatusEnum.FAILURE,
                "任务执行超时（超过1小时）",
                condition
            );

            if (affected > 0) {
                log.warn("发现{}个超时查重任务，已标记为失败", affected);
            }

        } catch (Exception e) {
            log.error("检查超时任务失败", e);
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
