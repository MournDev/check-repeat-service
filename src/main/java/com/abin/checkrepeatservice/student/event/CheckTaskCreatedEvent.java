package com.abin.checkrepeatservice.student.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 查重任务创建事件
 * 用于解耦任务创建和任务执行
 */
@Getter
public class CheckTaskCreatedEvent extends ApplicationEvent {
    
    /**
     * 查重任务 ID
     */
    private final Long taskId;
    
    /**
     * 论文 ID
     */
    private final Long paperId;
    
    public CheckTaskCreatedEvent(Object source, Long taskId, Long paperId) {
        super(source);
        this.taskId = taskId;
        this.paperId = paperId;
    }
}
