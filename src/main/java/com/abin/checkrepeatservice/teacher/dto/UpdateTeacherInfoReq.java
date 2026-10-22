package com.abin.checkrepeatservice.teacher.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateTeacherInfoReq {
    /** 用户ID */
    @NotNull(message = "用户ID不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /** 真实姓名 */
    private String realName;

    /** 工号 */
    private String username;

    /** 职称 */
    private String title;

    /** 所属学院 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long collegeId;

    /**
     * 研究方向 —— **学科领域 code 列表**（如 ["ENG-AI","ENG-SE"]），不再是 7 个硬编码短码。
     *
     * 取值域与论文的 subject_code、阈值的 subject 维度完全一致，都由 subject 表定义。
     * 落库去 teacher_subject 关联表，同时把学科中文名拼成 CSV 缓存进
     * sys_user.research_direction。
     *
     * null 与 [] 语义不同：null = 本次不涉及该字段（原样保留），[] = 明确清空。
     * 回显走 getInfoByUserId，从 teacher_subject 取 code，不解析那份 CSV 缓存。
     */
    private List<String> researchFields;

    /**
     * 仅回显时下发：research_direction 缓存里**认不出对应学科**的旧值（迁移遗留短码，
     * 如 machine_learning / database）。前端据此显示「已失效」提示。
     *
     * 不参与入参 —— 教师保存时以 researchFields 为准，这一项被忽略；一旦保存，
     * 缓存列会按 researchFields 重算，这些旧值自然清除。所以要先提示再让教师动手，
     * 不能静默丢弃（存量 7 个旧短码里只有 2 个能映射到学科）。
     */
    private List<String> legacyResearchFields;

    /** 个人简介 */
    private String introduce;

    /** 联系方式 */
    private String phone;

    /** 邮箱地址 */
    private String email;

    /**
     * 邮件通知开关（可选）：true-接收业务邮件，false-只收站内信。
     * 不传（null）时保持原值，避免教师只改资料却把偏好覆盖掉。
     */
    private Boolean notifyEmail;

    /** 办公地点 */
    private String office;

    /** 办公时间 */
    private String officeHours;

    /** 最大审核数量 */
    private Integer maxReviewCount;

    /** 审核期限 */
    private Integer reviewDeadline;
}
