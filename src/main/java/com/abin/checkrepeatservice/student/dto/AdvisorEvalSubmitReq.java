package com.abin.checkrepeatservice.student.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 学生提交导师评价请求（论文全生命周期 P6a）。
 *
 * 5 个维度统一必填 1-5 星（Likert 量表）；tags 为字典 advisor_eval_tag 的
 * dict_value 列表（后端做白名单校验）；comment 选填，落库前 jsoup 纯文本清洗。
 */
@Data
public class AdvisorEvalSubmitReq {

    /**
     * 论文ID（必须是本人论文，且状态为 defense_passed/finalized）
     */
    @NotNull(message = "论文ID不能为空")
    private Long paperId;

    /**
     * 指导及时性（1-5星）
     */
    @NotNull(message = "指导及时性评分不能为空")
    @Min(value = 1, message = "评分范围为1-5星")
    @Max(value = 5, message = "评分范围为1-5星")
    private Integer scoreTimeliness;

    /**
     * 反馈质量（1-5星）
     */
    @NotNull(message = "反馈质量评分不能为空")
    @Min(value = 1, message = "评分范围为1-5星")
    @Max(value = 5, message = "评分范围为1-5星")
    private Integer scoreQuality;

    /**
     * 责任心态度（1-5星）
     */
    @NotNull(message = "责任心态度评分不能为空")
    @Min(value = 1, message = "评分范围为1-5星")
    @Max(value = 5, message = "评分范围为1-5星")
    private Integer scoreAttitude;

    /**
     * 专业能力（1-5星）
     */
    @NotNull(message = "专业能力评分不能为空")
    @Min(value = 1, message = "评分范围为1-5星")
    @Max(value = 5, message = "评分范围为1-5星")
    private Integer scoreProfessional;

    /**
     * 总体满意度（1-5星）
     */
    @NotNull(message = "总体满意度评分不能为空")
    @Min(value = 1, message = "评分范围为1-5星")
    @Max(value = 5, message = "评分范围为1-5星")
    private Integer scoreOverall;

    /**
     * 预设标签（字典 advisor_eval_tag 的 dict_value，可多选，最多7个）
     */
    @Size(max = 7, message = "标签最多选择7个")
    private List<String> tags;

    /**
     * 文字评语（选填，≤500字）
     */
    @Size(max = 500, message = "评语长度不能超过500字符")
    private String comment;
}
