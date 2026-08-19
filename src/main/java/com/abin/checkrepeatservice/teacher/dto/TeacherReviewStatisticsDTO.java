package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 教师审核历史统计响应DTO
 */
@Data
public class TeacherReviewStatisticsDTO {
    
    /**
     * 统计记录列表
     */
    private List<ReviewRecordItem> records;
    
    /**
     * 总记录数
     */
    private Long total;
    
    /**
     * 统计信息
     */
    private Statistics statistics;
    
    /**
     * 审核记录项
     */
    @Data
    public static class ReviewRecordItem {
        /**
         * 论文ID
         */
        private String paperId;
        
        /**
         * 论文标题
         */
        private String paperTitle;
        
        /**
         * 学生姓名
         */
        private String studentName;
        
        /**
         * 审核状态
         */
        private Integer reviewStatus;
        
        /**
         * 审核时间
         */
        private LocalDateTime reviewTime;
        
        /**
         * 处理耗时（格式：X天Y小时）
         */
        private String processingTime;
    }
    
    /**
     * 统计信息
     */
    @Data
    public static class Statistics {
        /**
         * 总审核数
         */
        private Integer totalReviewed;
        
        /**
         * 通过数
         */
        private Integer approved;
        
        /**
         * 拒绝数
         */
        private Integer rejected;
        
        /**
         * 修改后通过数
         */
        private Integer revised;
    }
}