package com.abin.checkrepeatservice.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 登录请求实体：封装用户名/密码参数
 */
@Data
public class LoginReq {
    /**
     * 登录账号（学生用学号，教师用工号，管理员自定义）
     */
    @NotBlank(message = "登录账号不能为空")
    private String username;

    /**
     * 登录密码（明文，后端BCrypt校验）
     */
    @NotBlank(message = "登录密码不能为空")
    private String password;

}
