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

    /** 专业名称 */
    private String major;

    /** 学院名称 */
    private String collegeName;

    /** 班级名称 */
    private String className;

    /** 个人简介 */
    private String introduce;
}

