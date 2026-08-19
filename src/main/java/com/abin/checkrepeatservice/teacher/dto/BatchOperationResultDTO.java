package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;
import java.util.List;

/**
 * 批量操作结果DTO
 */
@Data
public class BatchOperationResultDTO {
    /**
     * 成功数量
     */
    private Integer successCount;
    
    /**
     * 失败数量
     */
    private Integer failCount;
    
    /**
     * 失败详情
     */
    private List<FailureDetail> failures;
    
    /**
     * 失败详情内部类
     */
    @Data
    public static class FailureDetail {
        /**
         * 序号
         */
        private Integer index;
        
        /**
         * 失败原因
         */
        private String reason;
    }
}