package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

/**
 * 导出请求DTO
 */
@Data
public class ExportRequestDTO {
    /**
     * 教师ID
     */
    private Long teacherId;
    
    /**
     * 导出格式
     */
    private String format;
    
    /**
     * 搜索条件
     */
    private String search;
    
    /**
     * 状态筛选
     */
    private String status;
    
    /**
     * 学院筛选
     */
    private String college;
}