package com.abin.checkrepeatservice.teacher.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 评阅提交请求（论文全生命周期 P2，教师端 POST /api/teacher/review-assign/{id}/submit）。
 *
 * 校验规则由 Service 按 method 分流：
 *  - online：score 与 comment 必填（系统内直接评）；
 *  - offline：attachmentId 必填（扫描件佐证），score/comment 可空回填。
 */
@Data
public class ReviewSubmitReq {

    /**
     * 评分（0-100）
     */
    @DecimalMin(value = "0", message = "评分不能低于0")
    @DecimalMax(value = "100", message = "评分不能超过100")
    private BigDecimal score;

    /**
     * 评阅意见
     */
    @Size(max = 2000, message = "评阅意见长度不能超过2000字符")
    private String comment;

    /**
     * 线下评阅表扫描件（关联file_info.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long attachmentId;
}
