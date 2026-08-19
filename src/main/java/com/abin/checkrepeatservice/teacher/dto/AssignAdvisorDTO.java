package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

/**
 * 分配导师请求DTO
 */
@Data
public class AssignAdvisorDTO {
    /**
     * 导师ID
     */
    private Long advisorId;
    
    /**
     * 导师姓名
     */
    private String advisorName;
}