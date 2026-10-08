package com.abin.checkrepeatservice.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserInfoReq {
    /** 用户ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 真实姓名 */
    private String realName;

    /** 用户名 */
    private String username;

    /** 手机号码 */
    private String phone;

    /** 邮箱地址 */
    private String email;

    /** 年级名称 */
    private String grade;

    /**
     * 学院ID（关联 college.id）。
     * 传了就以此为准反查学院名一起落库；不传（或查不到）则退化为只存 {@link #collegeName}，
     * 兼容前端自由输入、以及库里只有学院名的历史数据。
     */
    private Long collegeId;

    /** 学院名称 */
    private String collegeName;

    /**
     * 专业ID（关联 major.id）。
     * 语义同 {@link #collegeId}，不校验专业是否真属于 {@link #collegeId} ——
     * 存量数据里学院与专业本就可能对不上，别让保存被历史脏数据卡住。
     */
    private Long majorId;

    /** 专业名称 */
    private String major;

    /** 班级名称 */
    private String className;

    /** 个人简介 */
    private String introduce;
}

