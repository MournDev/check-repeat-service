package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminPermissionService;
import com.abin.checkrepeatservice.admin.vo.RoleCreateReq;
import com.abin.checkrepeatservice.admin.vo.RoleUpdateReq;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SysRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 *
 * 职责分离（P1）：角色的写操作（增/改/删）收归超级管理员 —— 角色是鉴权链的锚，
 * 普通管理员不应能改写权限模型本身；角色列表与权限树保持 ADMIN 可读（用户管理
 * 页需要），用户-角色改派保持 ADMIN 可调（服务层守护栏已按目标角色把关）。
 * 类上的 hasAuthority("ADMIN") 与 SUPER_ADMIN 的关系由 RoleHierarchy 层级保证。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
@Tag(name = "权限管理", description = "角色管理/权限树/用户角色分配")
public class AdminPermissionController {

    @Resource
    private AdminPermissionService adminPermissionService;

    /**
     * 获取角色列表
     */
    @Operation(summary = "获取角色列表", description = "查询系统全部角色")
    @GetMapping("/roles/list")
    public Result<List<SysRole>> getRoleList() {
        log.info("接收获取角色列表请求");
        return adminPermissionService.getRoleList();
    }

    /**
     * 创建角色（仅超级管理员）
     */
    @Operation(summary = "创建角色", description = "创建新角色（仅超级管理员）")
    @PostMapping("/roles/create")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @OperationLog(type = "admin_role_create", description = "管理员创建角色", recordResult = true)
    public Result<Map<String, Object>> createRole(
            @Parameter(description = "角色创建请求体", required = true)
            @Valid @RequestBody RoleCreateReq createReq) {
        log.info("接收创建角色请求: roleName={}", createReq.getRoleName());
        return adminPermissionService.createRole(createReq);
    }

    /**
     * 更新角色（仅超级管理员）
     */
    @Operation(summary = "更新角色", description = "更新角色信息（仅超级管理员）")
    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @OperationLog(type = "admin_role_update", description = "管理员更新角色", recordResult = true)
    public Result<String> updateRole(
            @Parameter(description = "角色ID", required = true)
            @PathVariable Long roleId,
            @Parameter(description = "角色更新请求体", required = true)
            @Valid @RequestBody RoleUpdateReq updateReq) {
        log.info("接收更新角色请求: roleId={}", roleId);
        return adminPermissionService.updateRole(roleId, updateReq);
    }

    /**
     * 删除角色（仅超级管理员）
     */
    @Operation(summary = "删除角色", description = "删除指定角色（仅超级管理员）")
    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @OperationLog(type = "admin_role_delete", description = "管理员删除角色")
    public Result<String> deleteRole(
            @Parameter(description = "角色ID", required = true)
            @PathVariable Long roleId) {
        log.info("接收删除角色请求: roleId={}", roleId);
        return adminPermissionService.deleteRole(roleId);
    }

    /**
     * 获取权限树
     */
    @Operation(summary = "获取权限树", description = "查询系统权限树结构")
    @GetMapping("/permissions/tree")
    public Result<List<Map<String, Object>>> getPermissionTree() {
        log.info("接收获取权限树请求");
        return adminPermissionService.getPermissionTree();
    }

    /**
     * 分配用户角色
     */
    @Operation(summary = "分配用户角色", description = "为指定用户分配角色")
    @PostMapping("/users/{userId}/assign-role")
    @OperationLog(type = "admin_user_role_assign", description = "管理员分配用户角色")
    public Result<String> assignUserRole(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "角色分配请求体：roleId 角色ID", required = true)
            @RequestBody Map<String, Long> requestBody) {
        Long roleId = requestBody.get("roleId");
        log.info("接收分配用户角色请求: userId={}, roleId={}", userId, roleId);
        return adminPermissionService.assignUserRole(userId, roleId);
    }
}