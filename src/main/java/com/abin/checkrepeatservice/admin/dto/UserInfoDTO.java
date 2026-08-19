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
    private String roleName;
    private Integer status;
    private String collegeName;
    private String major;
    private String grade;
    private String className;
    private LocalDateTime createTime;
    private LocalDateTime lastLoginTime;
}