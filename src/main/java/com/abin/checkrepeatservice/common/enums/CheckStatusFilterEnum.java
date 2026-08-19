package com.abin.checkrepeatservice.common.enums;

import lombok.Getter;

/**
 * 查重状态筛选枚举
 * 用于前端查重状态筛选功能
 */
@Getter
public enum CheckStatusFilterEnum {
    
    /**
     * 未查重 - 论文尚未进行任何查重
     */
    NOT_CHECKED("not_checked", "未查重"),
    
    /**
     * 校内查重 - 使用本地查重引擎进行查重
     */
    SCHOOL_CHECK("school_check", "校内查重"),
    
    /**
     * 第三方查重 - 使用第三方API进行查重
     */
    THIRD_PARTY_CHECK("third_party_check", "第三方查重"),
    
    /**
     * 已完成 - 查重已完成（无论使用哪种引擎）
     */
    COMPLETED("completed", "已完成");
    
    private final String code;
    private final String description;
    
    CheckStatusFilterEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public static CheckStatusFilterEnum fromCode(String code) {
        for (CheckStatusFilterEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的查重状态筛选类型: " + code);
    }
    
    /**
     * 验证筛选代码是否有效
     */
    public static boolean isValidCode(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        
        for (CheckStatusFilterEnum status : values()) {
            if (status.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }
}