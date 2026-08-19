package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;
import java.util.List;

/**
 * 批量删除请求DTO
 */
@Data
public class BatchDeleteDTO {
    /**
     * 要删除的学生ID数组
     */
    private List<Long> studentIds;
}