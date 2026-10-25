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
 * 论文提交后「自动分配导师」异步监听器。
 *
 * <p>查重触发点已统一收敛：学生提交只负责分配导师，查重必须等导师确认接收后
 * 由 {@code TeacherAssignmentServiceImpl#confirmAssignment} 触发。</p>
 *
 * <p>修复点：</p>
 * <ul>
 *   <li>AFTER_COMMIT：仅在提交事务成功落库后执行；事务回滚则不分配；</li>
 *   <li>依赖独立线程池 {@code paperSubmitExecutor}（轻量 DB/通知任务），
 *       与查重执行池 {@code checkTaskExecutor} 隔离；</li>
 *   <li>投递被拒绝时显式捕获并回落到提交线程执行：该路径只有导师分配，
 *       不包含 Tika 解析/全库比对，回落是可控的。</li>
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
            log.info("论文提交事务已提交，开始异步分配导师 - 论文ID: {}, 学生ID: {}",
                    event.getPaperId(), event.getStudentId());
            paperInfoService.allocateAdvisor(event.getPaperId(), event.getStudentId());
        } catch (Exception e) {
            // 后置任务异常不得反向影响已提交的论文事务
            log.error("论文提交后置任务执行失败 - 论文ID: {}", event.getPaperId(), e);
        }
    }
}
