package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 版本对比结果DTO
 */
@Data
public class VersionCompareResult {
    
    @Data
    public static class VersionInfo {
        private Long id;
        private Integer version;
        private BigDecimal similarityRate;
        private Integer wordCount;
    }
    
    @Data
    public static class DiffItem {
        private String field; // 字段名
        private Object before; // 修改前的值
        private Object after; // 修改后的值
        private Object change; // 变化量
    }
    
    private VersionInfo versionA;
    private VersionInfo versionB;
    private java.util.List<DiffItem> diffData;
}