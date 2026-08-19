package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 撤销分配请求DTO
 */
@Data
public class RevokeAssignmentDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 记录ID
     */
    private String recordId;
    
    /**
     * 撤销原因
     */
    private String reason;
}