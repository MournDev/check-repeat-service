package com.abin.checkrepeatservice.schedule;

import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.common.statemachine.CheckTaskStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 查重任务定时任务
 * 负责清理超时任务、状态监控等
 *
 * <p><b>关于开关：</b>本类原有的两个写库任务在加上 {@code @EnableScheduling}
 * 之前从未执行过（应用上没有该注解）。为了不让上线瞬间就开始按历史数据改库，
 * 它们默认关闭，需要显式打开：</p>
 * <ul>
 *   <li>{@code schedule.check-task.timeout-scan-enabled}（默认 false）——会把
 *       {@code check_status='checking'} 且已超 1 小时的任务判为失败，**会写库**</li>
 *   <li>{@code schedule.check-task.cleanup-enabled}（默认 false）——目前只打日志，
 *       但语义是清理旧任务，同样先关着</li>
 * </ul>
 * <p>{@link #taskStatusStatistics()} 只打 debug 日志、不碰库，未加开关。</p>
 */
@Component
@Slf4j
public class CheckTaskScheduler {

    @Autowired
    private CheckTaskStateMachine stateMachine;

    /** 超时任务扫描开关，默认关：开启后会把超时任务标记为失败（写库） */
    @Value("${schedule.check-task.timeout-scan-enabled:false}")
    private boolean timeoutScanEnabled;

    /** 旧任务清理开关，默认关 */
    @Value("${schedule.check-task.cleanup-enabled:false}")
    private boolean cleanupEnabled;

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
     * 每天凌晨2点清理已完成很久的任务数据
     * 保留最近30天的数据
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    public void cleanupOldTasks() {
        if (!cleanupEnabled) {
            log.debug("旧任务清理已关闭（schedule.check-task.cleanup-enabled=false），跳过");
            return;
        }
        try {
            log.info("开始清理旧查重任务数据...");
            
            String condition = "check_status IN ('" + CheckTaskStatusEnum.COMPLETED.getCode() + 
                             "','" + CheckTaskStatusEnum.FAILURE.getCode() + 
                             "','" + CheckTaskStatusEnum.CANCELLED.getCode() + 
                             "') AND end_time < DATE_SUB(NOW(), INTERVAL 30 DAY)";
            
            // 这里可以根据需要决定是物理删除还是软删除
            // 暂时只记录日志，不实际删除数据
            log.info("计划清理满足条件的任务数据：{}", condition);
            
        } catch (Exception e) {
            log.error("清理旧任务数据失败", e);
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