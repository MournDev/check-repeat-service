package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 重新分配请求DTO
 */
@Data
public class ReassignTeacherDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 原记录ID
     */
    private String recordId;
    
    /**
     * 新教师ID
     */
    private String newTeacherId;
    
    /**
     * 重新分配原因
     */
    private String reason;
}