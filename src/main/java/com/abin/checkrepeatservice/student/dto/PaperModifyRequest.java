package com.abin.checkrepeatservice.student.dto;

import lombok.Data;

/**
 * 申请修改已通过论文请求DTO
 */
@Data
public class PaperModifyRequest {
    private Long paperId;
    private String reason; // 修改原因
}