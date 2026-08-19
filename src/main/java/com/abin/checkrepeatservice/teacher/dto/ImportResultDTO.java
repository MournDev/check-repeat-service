package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;
import java.util.List;

/**
 * 导入结果DTO
 */
@Data
public class ImportResultDTO {
    /**
     * 成功导入数量
     */
    private Integer successCount;
    
    /**
     * 失败数量
     */
    private Integer failCount;
    
    /**
     * 失败详情
     */
    private List<ImportFailure> failures;
    
    /**
     * 导入失败详情
     */
    @Data
    public static class ImportFailure {
        /**
         * 行号
         */
        private Integer row;
        
        /**
         * 失败原因
         */
        private String reason;
    }
}