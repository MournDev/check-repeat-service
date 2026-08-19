package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 删除分配记录请求DTO
 */
@Data
public class DeleteAssignmentRecordsDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 记录ID列表
     */
    private List<String> ids;
}