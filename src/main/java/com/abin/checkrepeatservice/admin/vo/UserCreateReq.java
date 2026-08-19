package com.abin.checkrepeatservice.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户创建请求VO
 */
@Data
public class UserCreateReq {
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度必须在6-50位之间")
    private String password;
    
    @NotBlank(message = "真实姓名不能为空")
    private String realName;
    
    @NotNull(message = "角色ID不能为空")
    private Long roleId;
    
    private String email;
    private String phone;
    
    @NotNull(message = "用户类型不能为空")
    private  String userType;
    
    private String collegeName;
    private String major;
    private String grade;
    private String className;
}