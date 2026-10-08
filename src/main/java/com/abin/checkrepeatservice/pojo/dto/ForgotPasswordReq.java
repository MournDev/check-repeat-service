package com.abin.checkrepeatservice.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordReq {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "邮箱格式不正确")
    private String email;

    /**
     * 邮箱验证码，由 /api/auth/send-password-reset-code 下发。
     * 不加 @Pattern/@Size：格式不对时走「验证码错误或已过期」这条友好提示，
     * 而不是被参数校验直接打回。
     */
    @NotBlank(message = "验证码不能为空")
    private String verificationCode;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度必须在6-50位之间")
    private String newPassword;
}
