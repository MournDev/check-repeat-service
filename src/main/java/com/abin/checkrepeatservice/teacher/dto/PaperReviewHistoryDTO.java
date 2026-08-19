package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 论文审核历史响应DTO
 */
@Data
public class PaperReviewHistoryDTO {
    
    /**
     * 论文ID
     */
    private String paperId;
    
    /**
     * 论文标题
     */
    private String paperTitle;
    
    /**
     * 审核历史记录列表
     */
    private List<ReviewHistoryRecord> history;
    
    /**
     * 审核历史记录
     */
    @Data
    public static class ReviewHistoryRecord {
        /**
         * 审核记录ID
         */
        private String reviewId;
        
        /**
         * 审核教师姓名
         */
        private String reviewerName;
        
        /**
         * 审核状态（1-通过，2-不通过，3-修改后通过）
         */
        private Integer reviewStatus;
        
        /**
         * 审核意见
         */
        private String reviewOpinion;
        
        /**
         * 审核时间
         */
        private LocalDateTime reviewTime;
        
        /**
         * 论文版本
         */
        private String version;
        
        /**
         * 审核状态描述
         */
        private String reviewStatusDesc;
    }
}