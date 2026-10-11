package com.abin.checkrepeatservice.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 多级初审投票请求（论文全生命周期 P2，教师端 POST /api/teacher/workflow-review/{paperId}/vote）。
 */
@Data
public class WorkflowVoteReq {

    /**
     * 投票结果：pass-通过 / reject-驳回
     */
    @NotBlank(message = "投票结果不能为空")
    @Pattern(regexp = "pass|reject", message = "投票结果只能是 pass 或 reject")
    private String result;

    /**
     * 审批意见（可空）
     */
    @Size(max = 1000, message = "审批意见长度不能超过1000字符")
    private String opinion;
}
