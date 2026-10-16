package com.abin.checkrepeatservice.student.listener;

import com.abin.checkrepeatservice.student.event.EnhancedCheckTaskExecuteEvent;
import com.abin.checkrepeatservice.student.service.Impl.EnhancedCheckTaskServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 增强查重任务异步执行监听器（架构修复 P0）。
 *
 * <p>独立 bean 调用 Service 公共方法保证 @Async 代理生效；AFTER_COMMIT 保证任务记录与
 * 论文状态落库后再开始执行，避免异步线程读不到未提交数据。</p>
 */
@Component
@Slf4j
public class EnhancedCheckTaskExecuteListener {

    @Resource
    private EnhancedCheckTaskServiceImpl enhancedCheckTaskService;

    @Async("checkTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExecute(EnhancedCheckTaskExecuteEvent event) {
        log.info("增强查重任务事务已提交，开始异步执行 - taskId: {}", event.getTaskId());
        enhancedCheckTaskService.doExecuteCheckTask(event.getTaskId(), event.getEngineTypes());
    }
}
