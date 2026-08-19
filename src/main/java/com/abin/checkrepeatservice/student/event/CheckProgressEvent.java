package com.abin.checkrepeatservice.student.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.context.ApplicationEvent;

/**
 * 查重进度更新事件
 * 用于实时推送查重进度到前端
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CheckProgressEvent extends ApplicationEvent {
    
    /**
     * 查重任务 ID
     */
    private final Long taskId;
    
    /**
     * 论文 ID
     */
    private final Long paperId;
    
    /**
     * 当前阶段
     */
    private final String stage;
    
    /**
     * 进度百分比 (0-100)
     */
    private final Integer percent;
    
    /**
     * 进度消息
     */
    private final String message;
    
    /**
     * 预估剩余时间（秒）
     */
    private final Integer estimatedRemainingSeconds;
    
    public CheckProgressEvent(Object source, Long taskId, Long paperId, 
                             String stage, Integer percent, String message, 
                             Integer estimatedRemainingSeconds) {
        super(source);
        this.taskId = taskId;
        this.paperId = paperId;
        this.stage = stage;
        this.percent = percent;
        this.message = message;
        this.estimatedRemainingSeconds = estimatedRemainingSeconds;
    }
    
    /**
     * 静态 builder 方法
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Object source;
        private Long taskId;
        private Long paperId;
        private String stage;
        private Integer percent;
        private String message;
        private Integer estimatedRemainingSeconds;
        
        public Builder source(Object source) {
            this.source = source;
            return this;
        }
        
        public Builder taskId(Long taskId) {
            this.taskId = taskId;
            return this;
        }
        
        public Builder paperId(Long paperId) {
            this.paperId = paperId;
            return this;
        }
        
        public Builder stage(String stage) {
            this.stage = stage;
            return this;
        }
        
        public Builder percent(Integer percent) {
            this.percent = percent;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder estimatedRemainingSeconds(Integer estimatedRemainingSeconds) {
            this.estimatedRemainingSeconds = estimatedRemainingSeconds;
            return this;
        }
        
        public CheckProgressEvent build() {
            return new CheckProgressEvent(
                source, taskId, paperId, stage, percent, message, estimatedRemainingSeconds
            );
        }
    }
}
