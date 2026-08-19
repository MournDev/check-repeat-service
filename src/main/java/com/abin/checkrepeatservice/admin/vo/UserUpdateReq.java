package com.abin.checkrepeatservice.admin.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户更新请求VO
 */
@Data
public class UserUpdateReq {
    private String realName;
    private String email;
    private String phone;
    private String collegeName;
    private String major;
    private String grade;
    private String className;
    
    @NotNull(message = "状态不能为空")
    private Integer status;
}