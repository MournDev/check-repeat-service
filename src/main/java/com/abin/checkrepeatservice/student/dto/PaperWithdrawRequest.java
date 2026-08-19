package com.abin.checkrepeatservice.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 论文撤回请求 DTO
 */
@Data
public class PaperWithdrawRequest {
    private Long paperId;
    
    /**
     * 撤回原因分类（必填）
     */
    @NotBlank(message = "请选择撤回原因类型")
    private String withdrawReasonType; // PERSONAL(个人原因), FORMAT(格式问题), CONTENT(内容问题), OTHER(其他)
    
    /**
     * 详细原因描述（可选）
     */
    @Size(max = 500, message = "原因描述不能超过 500 字符")
    private String reasonDetail;
}