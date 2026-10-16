package com.abin.checkrepeatservice.student.listener;

import com.abin.checkrepeatservice.student.event.PaperSubmittedEvent;
import com.abin.checkrepeatservice.student.service.Impl.PaperInfoServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 论文提交后「自动分配导师 + 触发查重」异步监听器（架构修复 P0）。
 *
 * <p>修复点：</p>
 * <ul>
 *   <li>独立 bean 调用 Service 公共方法，调用经过 Spring 代理，@Async 真正生效，
 *       不再同步占用提交请求的 Tomcat 线程与 HikariCP 连接；</li>
 *   <li>AFTER_COMMIT：仅在提交事务成功落库后执行；事务回滚则不分配、不查重；</li>
 *   <li>统一走查重线程池 checkTaskExecutor，受并发上限与监控约束。</li>
 * </ul>
 */
@Component
@Slf4j
public class PaperSubmissionAllocationListener {

    @Resource
    private PaperInfoServiceImpl paperInfoService;

    @Async("checkTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaperSubmitted(PaperSubmittedEvent event) {
        log.info("论文提交事务已提交，开始异步分配导师并触发查重 - 论文ID: {}, 学生ID: {}",
                event.getPaperId(), event.getStudentId());
        paperInfoService.allocateAdvisorAndCheck(event.getPaperId(), event.getStudentId());
    }
}
