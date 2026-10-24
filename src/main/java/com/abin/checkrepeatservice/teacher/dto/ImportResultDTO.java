package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

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
     * 导入成功学生的初始密码（username -> 明文初始密码）。
     * 仅本次响应回传，库中只存 BCrypt 密文；管理员需在首次登录后引导学生改密。
     */
    private Map<String, String> initialPasswords;
    
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