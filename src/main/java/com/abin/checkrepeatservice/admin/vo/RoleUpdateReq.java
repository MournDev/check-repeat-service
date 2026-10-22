package com.abin.checkrepeatservice.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 角色更新请求VO
 */
@Data
public class RoleUpdateReq {
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
    
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;
    
    private String description;

    /**
     * 数据范围（可选，null 表示不修改）：ALL/COLLEGE/MAJOR/TEACHER/SELF
     */
    @Pattern(regexp = "^(ALL|COLLEGE|MAJOR|TEACHER|SELF)$", message = "数据范围取值非法")
    private String dataScope;
}