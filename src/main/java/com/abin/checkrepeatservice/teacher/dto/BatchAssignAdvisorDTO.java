package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量分配导师请求DTO
 */
@Data
public class BatchAssignAdvisorDTO {
    /**
     * 学生ID数组
     */
    private List<Long> studentIds;
    
    /**
     * 导师ID
     */
    private Long advisorId;
    
    /**
     * 导师姓名
     */
    private String advisorName;
}