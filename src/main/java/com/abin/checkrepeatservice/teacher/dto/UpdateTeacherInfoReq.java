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

    /** 研究方向 */
    private List<String> researchFields;

    /** 个人简介 */
    private String introduce;

    /** 联系方式 */
    private String phone;

    /** 邮箱地址 */
    private String email;

    /** 办公地点 */
    private String office;

    /** 办公时间 */
    private String officeHours;

    /** 最大审核数量 */
    private Integer maxReviewCount;

    /** 审核期限 */
    private Integer reviewDeadline;
}
