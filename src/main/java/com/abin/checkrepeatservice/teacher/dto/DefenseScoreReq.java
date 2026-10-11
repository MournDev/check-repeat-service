package com.abin.checkrepeatservice.teacher.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 答辩评分录入请求（论文全生命周期 P3，教师端 POST /api/teacher/defense/score）。
 *
 * 校验规则：
 *  - recordId 必填（要更新哪条 defense_record）；
 *  - result 必填，取值 pass / fail / postpone；
 *  - result=pass 时 score 与 grade 必填（用于回写 final_score）；
 *  - result=fail/postpone 时 score/grade 可空（缓辩无成绩，fail 留作记录）；
 *  - 线下答辩由组长/秘书代录，attachmentId 可空（线下扫描件由附件单独管理）。
 */
@Data
public class DefenseScoreReq {

    /**
     * 答辩记录ID（关联defense_record.id）
     */
    @NotNull(message = "答辩记录ID不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long recordId;

    /**
     * 综合得分（0-100）
     */
    @DecimalMin(value = "0", message = "得分不能低于0")
    @DecimalMax(value = "100", message = "得分不能超过100")
    private BigDecimal score;

    /**
     * 等级：优 / 良 / 中 / 及格 / 不及格
     */
    @Pattern(regexp = "优|良|中|及格|不及格", message = "等级只能是 优 / 良 / 中 / 及格 / 不及格")
    private String grade;

    /**
     * 结论：pass-通过 / fail-不通过 / postpone-缓辩
     */
    @NotNull(message = "结论不能为空")
    @Pattern(regexp = "pass|fail|postpone", message = "结论只能是 pass / fail / postpone")
    private String result;

    /**
     * 答辩记录（可空，纯文本）
     */
    @Size(max = 2000, message = "答辩记录长度不能超过2000字符")
    private String comment;

    /**
     * 线下答辩表扫描件（关联file_info.id，可空）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long attachmentId;
}
