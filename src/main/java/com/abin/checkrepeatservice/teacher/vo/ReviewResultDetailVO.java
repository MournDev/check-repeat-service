package com.abin.checkrepeatservice.teacher.vo;

import lombok.Data;
import java.util.List;

/**
 * 审核结果详情VO
 */
@Data
public class ReviewResultDetailVO {
    
    /**
     * 成功数量
     */
    private Integer successCount;
    
    /**
     * 失败数量
     */
    private Integer failedCount;
    
    /**
     * 详细信息列表
     */
    private List<ReviewDetailVO> details;
    
    /**
     * 审核详情VO
     */
    @Data
    public static class ReviewDetailVO {
        /**
         * 论文ID
         */
        private String paperId;
        
        /**
         * 状态：success/error
         */
        private String status;
        
        /**
         * 错误信息（如果有）
         */
        private String errorMessage;
    }
}