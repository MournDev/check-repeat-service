package com.abin.checkrepeatservice.teacher.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量论文审核DTO
 */
@Data
@Schema(description = "批量论文审核请求参数")
public class BatchReviewDTO {

    /**
     * 论文ID数组
     */
    @NotEmpty(message = "论文ID列表不能为空")
    @Schema(description = "论文ID数组")
    private List<Long> paperIds;

    /**
     * 审核状态：APPROVED/REJECTED
     */
    @NotNull(message = "审核状态不能为空")
    @Schema(description = "审核状态：APPROVED/REJECTED")
    private String reviewStatus;

    /**
     * 审核意见
     */
    @Schema(description = "审核意见")
    private String reviewOpinion;

    /**
     * 审核附件URL
     */
    @Schema(description = "审核附件URL")
    private String reviewAttach;
}