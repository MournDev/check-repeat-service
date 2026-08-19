package com.abin.checkrepeatservice.common.enums;

import lombok.Getter;

/**
 * 查重引擎类型枚举
 */
@Getter
public enum CheckEngineTypeEnum {
    
    /**
     * 本地查重引擎 - 基于SimHash和余弦相似度
     */
    LOCAL("local", "本地查重引擎", 1),
    
    /**
     * 第三方API引擎 - 如知网、维普等
     */
    THIRD_PARTY("third_party", "第三方查重引擎", 2),
    
    /**
     * 深度学习引擎 - 基于BERT等语义模型
     */
    DEEP_LEARNING("deep_learning", "深度学习引擎", 3);
    
    private final String code;
    private final String description;
    private final Integer order;
    
    CheckEngineTypeEnum(String code, String description, Integer order) {
        this.code = code;
        this.description = description;
        this.order = order;
    }
    
    public static CheckEngineTypeEnum fromCode(String code) {
        for (CheckEngineTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的查重引擎类型: " + code);
    }
}