package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminPermissionService;
import com.abin.checkrepeatservice.admin.vo.RoleCreateReq;
import com.abin.checkrepeatservice.admin.vo.RoleUpdateReq;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SysRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

/**
 * 管理员权限管理控制器
 * 职责：接收HTTP请求，参数校验，调用服务层，返回响应结果
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class AdminPermissionController {

    @Resource
    private AdminPermissionService adminPermissionService;

    /**
     * 获取角色列表
     */
    @GetMapping("/roles/list")
    public Result<List<SysRole>> getRoleList() {
        log.info("接收获取角色列表请求");
        try {
            return adminPermissionService.getRoleList();
        } catch (Exception e) {
            log.error("获取角色列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取角色列表失败: " + e.getMessage());
        }
    }

    /**
     * 创建角色
     */
    @PostMapping("/roles/create")
    public Result<Map<String, Object>> createRole(@Valid @RequestBody RoleCreateReq createReq) {
        log.info("接收创建角色请求: roleName={}", createReq.getRoleName());
        try {
            return adminPermissionService.createRole(createReq);
        } catch (Exception e) {
            log.error("创建角色失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "创建角色失败: " + e.getMessage());
        }
    }

    /**
     * 更新角色
     */
    @PutMapping("/roles/{roleId}")
    public Result<String> updateRole(@PathVariable Long roleId,
                                     @Valid @RequestBody RoleUpdateReq updateReq) {
        log.info("接收更新角色请求: roleId={}", roleId);
        try {
            return adminPermissionService.updateRole(roleId, updateReq);
        } catch (Exception e) {
            log.error("更新角色失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新角色失败: " + e.getMessage());
        }
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/roles/{roleId}")
    public Result<String> deleteRole(@PathVariable Long roleId) {
        log.info("接收删除角色请求: roleId={}", roleId);
        try {
            return adminPermissionService.deleteRole(roleId);
        } catch (Exception e) {
            log.error("删除角色失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除角色失败: " + e.getMessage());
        }
    }

    /**
     * 获取权限树
     */
    @GetMapping("/permissions/tree")
    public Result<List<Map<String, Object>>> getPermissionTree() {
        log.info("接收获取权限树请求");
        try {
            return adminPermissionService.getPermissionTree();
        } catch (Exception e) {
            log.error("获取权限树失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取权限树失败: " + e.getMessage());
        }
    }

    /**
     * 分配用户角色
     */
    @PostMapping("/users/{userId}/assign-role")
    public Result<String> assignUserRole(@PathVariable Long userId,
                                         @RequestBody Map<String, Long> requestBody) {
        Long roleId = requestBody.get("roleId");
        log.info("接收分配用户角色请求: userId={}, roleId={}", userId, roleId);
        try {
            return adminPermissionService.assignUserRole(userId, roleId);
        } catch (Exception e) {
            log.error("分配用户角色失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "分配用户角色失败: " + e.getMessage());
        }
    }
}