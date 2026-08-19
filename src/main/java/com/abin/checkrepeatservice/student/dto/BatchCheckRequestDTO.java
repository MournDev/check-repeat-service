package com.abin.checkrepeatservice.student.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 批量查重请求 DTO
 */
@Data
@Builder
public class BatchCheckRequestDTO {
    
    /**
     * 论文 ID 列表
     */
    private List<Long> paperIds;
}
