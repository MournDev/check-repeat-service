package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.vo.RoleCreateReq;
import com.abin.checkrepeatservice.admin.vo.RoleUpdateReq;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.SysRole;

import java.util.List;
import java.util.Map;

/**
 * 管理员权限管理服务接口
 */
public interface AdminPermissionService {
    
    /**
     * 获取角色列表
     */
    Result<List<SysRole>> getRoleList();
    
    /**
     * 创建角色
     */
    Result<Map<String, Object>> createRole(RoleCreateReq createReq);
    
    /**
     * 更新角色
     */
    Result<String> updateRole(Long roleId, RoleUpdateReq updateReq);
    
    /**
     * 删除角色
     */
    Result<String> deleteRole(Long roleId);
    
    /**
     * 获取权限树
     */
    Result<List<Map<String, Object>>> getPermissionTree();
    
    /**
     * 分配用户角色
     */
    Result<String> assignUserRole(Long userId, Long roleId);
    
    /**
     * 检查角色编码唯一性
     */
    boolean isRoleCodeExists(String roleCode, Long excludeRoleId);
    
    /**
     * 检查角色是否被用户使用
     */
    boolean isRoleInUse(Long roleId);
}