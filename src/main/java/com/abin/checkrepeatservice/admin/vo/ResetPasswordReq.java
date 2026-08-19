package com.abin.checkrepeatservice.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求VO
 */
@Data
public class ResetPasswordReq {
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度必须在6-50位之间")
    private String newPassword;
}