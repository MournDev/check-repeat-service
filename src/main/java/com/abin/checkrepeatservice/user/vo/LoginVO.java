package com.abin.checkrepeatservice.user.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录响应 VO（仅返回前端所需的非敏感信息）
 */
@Data
public class LoginVO {
    /** JWT令牌（前端存储，后续请求携带） */
    private String token;

    /** 用户ID（雪花ID，用于前端关联业务数据） */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /** 角色编码（用于前端权限控制，如 STUDENT/TEACHER/ADMIN） */
    private String roleCode;

    /** 登录账号（用户名） */
    private String username;

    /** 真实姓名（前端展示用） */
    private String realName;

    /**
     * 专业ID（关联 major.id）
     * 个人中心的下拉框靠它回显选中项，因此必须与 {@link #major} 一起下发；
     * 与 userId 同理按字符串下发，19 位雪花ID 过 JS 会被截掉末位。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long majorId;

    /** 专业名称（前端展示用） */
    private String major;

    /** 学院ID（关联 college.id），同 {@link #majorId} */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long collegeId;

    /** 学院名称（前端展示用） */
    private String collegeName;

    /** 年级名称（前端展示用） */
    private String grade;

    /** 班级名称（前端展示用） */
    private String className;

    /** 手机号码（前端展示用） */
    private String phone;

    /** 邮箱地址（前端展示用） */
    private String email;

    /** 邮箱验证状态（前端展示用） */
    private Integer emailVerified;

    /** 个人简介（前端展示用） */
    private String introduce;

    /** 令牌过期时间（毫秒时间戳，前端可提前刷新令牌） */
    private Long expireTime;

    /** 头像地址（前端展示用） */
    private String avatar;

    /** 最后登录时间（展示用） */
    private LocalDateTime lastLoginTime;

}
