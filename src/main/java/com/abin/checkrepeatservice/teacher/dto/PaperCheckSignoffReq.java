package com.abin.checkrepeatservice.teacher.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 查重报告导师签核请求（论文全生命周期 P5，教师端 POST /api/teacher/papers/{paperId}/check-signoff）。
 *
 * 校验规则：
 *  - paperId 由路径变量注入（控制器赋值），DTO 不做必填校验：@Valid 先于控制器方法体执行，
 *    若在此加 @NotNull，未在请求体重复传 paperId 的调用方会被误拦；
 *  - qualified 必填且只能是 1（合格放行）/ 0（不合格退回），取值域在服务层校验；
 *  - guidanceScore 统一必填（0-100）：它同时是成绩加权合成（ScoreComposeService）的
 *    "指导分"数据源，不合格签核也必须给出评分，否则终期合成会缺数据源（40914）；
 *  - comment 可空，≤500 字符，服务层做 XSS 清洗。
 */
@Data
public class PaperCheckSignoffReq {

    /**
     * 论文ID（关联paper_info.id），由控制器从路径变量注入，请求体无需传
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    /**
     * 签核结论：1-合格放行 / 0-不合格退回修改
     */
    @NotNull(message = "签核结论不能为空")
    private Integer qualified;

    /**
     * 指导评分（0-100，统一必填：成绩加权合成的指导分数据源）
     */
    @NotNull(message = "指导评分不能为空")
    @DecimalMin(value = "0", message = "指导评分不能低于0")
    @DecimalMax(value = "100", message = "指导评分不能超过100")
    private BigDecimal guidanceScore;

    /**
     * 签核意见（可空，纯文本，入库前做 XSS 清洗）
     */
    @Size(max = 500, message = "签核意见长度不能超过500字符")
    private String comment;
}
