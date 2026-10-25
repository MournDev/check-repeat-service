package com.abin.checkrepeatservice.student.listener;

import com.abin.checkrepeatservice.student.event.PaperSubmittedEvent;
import com.abin.checkrepeatservice.student.service.Impl.PaperInfoServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.RejectedExecutionException;

/**
 * 论文提交后「自动分配导师 + 触发查重」异步监听器（架构修复 P0）。
 *
 * <p>修复点：</p>
 * <ul>
 *   <li>AFTER_COMMIT：仅在提交事务成功落库后执行；事务回滚则不分配、不查重；</li>
 *   <li>依赖独立线程池 {@code paperSubmitExecutor}（轻量 DB/通知任务），
 *       与查重执行池 {@code checkTaskExecutor} 隔离——查重队列打满时，
 *       论文提交链路不会跟着排队或报错；</li>
 *   <li>投递被拒绝时显式捕获并回落到提交线程执行：该路径只有导师分配与
 *       “创建查重任务事件”，不包含 Tika 解析/全库比对，回落是可控的，
 *       且保证论文已落库后“导师分配不会无声丢失”。</li>
 * </ul>
 */
@Component
@Slf4j
public class PaperSubmissionAllocationListener {

    @Resource
    private PaperInfoServiceImpl paperInfoService;

    @Resource(name = "paperSubmitExecutor")
    private ThreadPoolTaskExecutor paperSubmitExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaperSubmitted(PaperSubmittedEvent event) {
        try {
            paperSubmitExecutor.execute(() -> runAllocation(event));
        } catch (RejectedExecutionException e) {
            log.warn("论文提交后置队列已满，回落到提交线程执行轻量分配 - paperId: {}, studentId: {}",
                    event.getPaperId(), event.getStudentId(), e);
            runAllocation(event);
        }
    }

    private void runAllocation(PaperSubmittedEvent event) {
        try {
            log.info("论文提交事务已提交，开始异步分配导师并触发查重 - 论文ID: {}, 学生ID: {}",
                    event.getPaperId(), event.getStudentId());
            paperInfoService.allocateAdvisorAndCheck(event.getPaperId(), event.getStudentId());
        } catch (Exception e) {
            // 后置任务异常不得反向影响已提交的论文事务
            log.error("论文提交后置任务执行失败 - 论文ID: {}", event.getPaperId(), e);
        }
    }
}
