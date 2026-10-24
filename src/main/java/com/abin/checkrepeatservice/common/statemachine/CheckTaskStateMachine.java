package com.abin.checkrepeatservice.common.statemachine;

import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 查重任务状态机（阶段 2 加固）。
 *
 * <p>核心原则：状态流转一律走「条件抢占」——单条 UPDATE 同时携带
 * {@code id + 期望状态} 条件，以影响行数判定是否抢占成功。不再先 select 再
 * updateById 全量回写，因此天然避免并发覆盖与旧实体状态回写。</p>
 *
 * <p>版本轴：条件 UPDATE 同时执行 {@code version = version + 1}，与实体级
 * {@code updateById} 的乐观锁（@Version）共用同一条版本序列；任何在状态变化前
 * 读取的实体，之后再做 updateById 都会因版本不匹配而失败。</p>
 */
@Component
@Slf4j
public class CheckTaskStateMachine {

    private static final int FAIL_REASON_MAX_LENGTH = 500;

    @Autowired
    private CheckTaskMapper checkTaskMapper;

    /**
     * 异步消费入口：抢占 PENDING -> CHECKING。
     *
     * <p>只有影响行数为 1 的线程能拿到执行权；重复事件、已被取消或已被超时扫描
     * 改写的任务都会抢占失败，直接返回，实现天然幂等。</p>
     *
     * @return true=抢占成功，可以开始解析/比对；false=放弃本次执行
     */
    public boolean claimForExecution(Long taskId) {
        return transitionStatus(taskId,
                CheckTaskStatusEnum.PENDING,
                CheckTaskStatusEnum.CHECKING,
                "开始执行查重");
    }

    /**
     * 条件状态流转。
     *
     * @param taskId         任务 ID
     * @param expectedStatus 期望的当前状态（作为 UPDATE 条件）
     * @param targetStatus   目标状态
     * @param reason         失败原因（仅在目标为 FAILURE 时落库）
     * @return true=抢占并更新成功；false=状态已被其他线程改变或流转非法
     */
    public boolean transitionStatus(Long taskId,
                                    CheckTaskStatusEnum expectedStatus,
                                    CheckTaskStatusEnum targetStatus,
                                    String reason) {
        if (taskId == null || expectedStatus == null || targetStatus == null
                || !expectedStatus.canTransitionTo(targetStatus)) {
            log.warn("非法状态流转请求：taskId={}, {} -> {}",
                    taskId,
                    expectedStatus == null ? "null" : expectedStatus.getCode(),
                    targetStatus == null ? "null" : targetStatus.getCode());
            return false;
        }

        LambdaUpdateWrapper<CheckTask> wrapper = new LambdaUpdateWrapper<CheckTask>()
                .eq(CheckTask::getId, taskId)
                .eq(CheckTask::getCheckStatus, expectedStatus.getCode())
                .set(CheckTask::getCheckStatus, targetStatus.getCode())
                .setSql("version = version + 1");

        if (targetStatus == CheckTaskStatusEnum.CHECKING) {
            wrapper.set(CheckTask::getStartTime, LocalDateTime.now());
        }
        if (targetStatus.isFinalStatus()) {
            wrapper.set(CheckTask::getEndTime, LocalDateTime.now());
        }
        if (targetStatus == CheckTaskStatusEnum.FAILURE
                || targetStatus == CheckTaskStatusEnum.CANCELLED) {
            wrapper.set(CheckTask::getFailReason, truncateReason(reason));
        }

        int rows = checkTaskMapper.update(null, wrapper);
        if (rows == 0) {
            log.info("状态抢占失败（已被并发改写）：taskId={}, 期望={}, 目标={}",
                    taskId, expectedStatus.getCode(), targetStatus.getCode());
            return false;
        }

        log.info("状态流转成功：taskId={}, {} -> {}, reason={}",
                taskId, expectedStatus.getCode(), targetStatus.getCode(), reason);
        return true;
    }

    /**
     * 超时任务批量回收已由 CheckTaskScheduler 改为「逐任务条件流转」：
     * CHECKING -> FAILURE 走 transitionStatus，保证与消费端/取消端互斥，
     * 且只有真正抢到状态的一方执行额度返还。
     */

    private String truncateReason(String reason) {
        if (reason == null) {
            return "";
        }
        return reason.length() > FAIL_REASON_MAX_LENGTH
                ? reason.substring(0, FAIL_REASON_MAX_LENGTH)
                : reason;
    }
}
