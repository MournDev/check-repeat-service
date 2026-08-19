package com.abin.checkrepeatservice.student.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 批量查重结果 DTO
 */
@Data
@Builder
public class BatchCheckResultDTO {
    
    /**
     * 总数量
     */
    private Integer totalCount;
    
    /**
     * 成功数量
     */
    private Integer successCount;
    
    /**
     * 失败数量
     */
    private Integer failedCount;
    
    /**
     * 成功列表
     */
    private List<TaskResult> successList;
    
    /**
     * 失败列表
     */
    private List<TaskResult> failedList;
    
    /**
     * 预估总时长（秒）
     */
    private Integer estimatedTotalTime;
    
    /**
     * 单个任务结果
     */
    @Data
    @Builder
    public static class TaskResult {
        
        /**
         * 论文 ID
         */
        private Long paperId;
        
        /**
         * 任务 ID
         */
        private Long taskId;
        
        /**
         * 是否成功
         */
        private Boolean success;
        
        /**
         * 消息
         */
        private String message;
        
        /**
         * 预估时间（秒）
         */
        private Integer estimatedTime;
        
        /**
         * 排队位置
         */
        private Integer queuePosition;
        
        /**
         * 等待时间（秒）
         */
        private Integer waitTime;
    }
}
