package com.abin.checkrepeatservice.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 答辩批次新建/编辑请求（论文全生命周期 P3，管理端 POST/PUT /api/admin/defense/batch/**）。
 *
 * batch_type 决定批次流转：
 *  - mid_term（中期）：安排时若论文当前为 reviewing/mid_defense，由 Service 经阶段门推进到 mid_defense；
 *  - final（终期）：评阅全部完成且中期通过后安排，成绩提交后回写 final_score 并推进 defense_passed。
 */
@Data
public class DefenseBatchReq {

    /**
     * 批次编号（新建必填，唯一；编辑时不可改）
     */
    @NotBlank(message = "批次编号不能为空")
    @Size(max = 32, message = "批次编号长度不能超过32字符")
    private String batchNo;

    /**
     * 批次类型：mid_term-中期答辩 / final-终期答辩
     */
    @NotBlank(message = "批次类型不能为空")
    @Pattern(regexp = "mid_term|final", message = "批次类型只能是 mid_term 或 final")
    private String batchType;

    /**
     * 学院ID（可空表示全校批次）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long collegeId;

    /**
     * 答辩时间
     */
    @NotNull(message = "答辩时间不能为空")
    private LocalDateTime defenseDate;

    /**
     * 地点：线下填教室、线上填会议链接
     */
    @Size(max = 128, message = "地点长度不能超过128字符")
    private String location;

    /**
     * 答辩方式：online-线上 / offline-线下
     */
    @NotBlank(message = "答辩方式不能为空")
    @Pattern(regexp = "online|offline", message = "答辩方式只能是 online 或 offline")
    private String mode;

    /**
     * 答辩组长ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long leaderId;

    /**
     * 答辩秘书ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long secretaryId;

    /**
     * 备注（可空）
     */
    @Size(max = 255, message = "备注长度不能超过255字符")
    private String remark;
}
