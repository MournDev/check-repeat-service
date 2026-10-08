package com.abin.checkrepeatservice.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息DTO
 */
@Data
public class UserInfoDTO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String userType;

    /**
     * 角色ID（sys_role.id）。管理端编辑弹窗要拿它回显「人员类型」，之前 DTO 里没有这个
     * 字段，导致编辑时那一项永远是空的。
     *
     * 强制 STRING：除 1001/2001/3001 外，系统角色是雪花ID（19 位，如 SUPER_ADMIN 的
     * 2060661728760434689），超过 JS 的 2^53 会丢末位。前端 el-option 的 value 本来
     * 也就是字符串（'1001'），统一成字符串两边才对得上。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long roleId;

    private String roleName;
    private Integer status;
    private String collegeName;
    private String major;
    private String grade;
    private String className;
    private LocalDateTime createTime;
    private LocalDateTime lastLoginTime;
}