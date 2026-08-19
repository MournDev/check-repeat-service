package com.abin.checkrepeatservice.admin.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 角色创建请求VO
 */
@Data
public class RoleCreateReq {
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
    
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;
    
    private String description;
}