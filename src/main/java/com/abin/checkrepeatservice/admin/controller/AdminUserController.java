package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.UserInfoDTO;
import com.abin.checkrepeatservice.admin.service.AdminUserService;
import com.abin.checkrepeatservice.admin.vo.UserCreateReq;
import com.abin.checkrepeatservice.admin.vo.UserUpdateReq;
import com.abin.checkrepeatservice.admin.vo.BatchDeleteReq;
import com.abin.checkrepeatservice.admin.vo.ResetPasswordReq;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SysLoginLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import java.util.Map;

/**
 * 管理员用户管理控制器
 * 职责：接收HTTP请求，参数校验，调用服务层，返回响应结果
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
@Tag(name = "用户管理", description = "用户分页/新增/编辑/删除/状态/密码与登录历史")
public class AdminUserController {

    @Resource
    private AdminUserService adminUserService;

    /**
     * 获取用户列表（分页）
     */
    @Operation(summary = "获取用户列表", description = "分页查询用户，支持用户类型、状态与关键字筛选")
    @GetMapping("/list")
    public Result<Page<UserInfoDTO>> getUserList(
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "用户类型（可选）")
            @RequestParam(required = false) String userType,
            @Parameter(description = "用户状态（可选）")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "搜索关键字（可选）")
            @RequestParam(required = false) String keyword) {
        
        log.info("接收获取用户列表请求: page={}, size={}, userType={}, status={}, keyword={}",
                page, size, userType, status, keyword);
        return adminUserService.getUserList(page, size, userType, status, keyword);
    }

    /**
     * 创建新用户
     */
    @Operation(summary = "创建新用户", description = "创建学生/教师/管理员账号")
    @PostMapping("/create")
    @OperationLog(type = "admin_user_create", description = "管理员创建用户", recordResult = true)
    public Result<Map<String, Object>> createUser(
            @Parameter(description = "用户创建请求体", required = true)
            @Valid @RequestBody UserCreateReq createReq) {
        log.info("接收创建用户请求: username={}", createReq.getUsername());
        return adminUserService.createUser(createReq);
    }

    /**
     * 更新用户信息
     */
    @Operation(summary = "更新用户信息", description = "更新指定用户的信息")
    @PutMapping("/{userId}")
    @OperationLog(type = "admin_user_update", description = "管理员更新用户信息")
    public Result<String> updateUser(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "用户更新请求体", required = true)
            @Valid @RequestBody UserUpdateReq updateReq) {
        log.info("接收更新用户信息请求: userId={}", userId);
        return adminUserService.updateUser(userId, updateReq);
    }

    /**
     * 删除用户
     */
    @Operation(summary = "删除用户", description = "删除指定用户")
    @DeleteMapping("/{userId}")
    @OperationLog(type = "admin_user_delete", description = "管理员删除用户")
    public Result<String> deleteUser(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId) {
        log.info("接收删除用户请求: userId={}", userId);
        return adminUserService.deleteUser(userId);
    }

    /**
     * 批量删除用户
     */
    @Operation(summary = "批量删除用户", description = "按用户ID列表批量删除用户")
    @PostMapping("/batch-delete")
    @OperationLog(type = "admin_user_batch_delete", description = "管理员批量删除用户")
    public Result<String> batchDeleteUsers(
            @Parameter(description = "批量删除请求体：userIds 用户ID列表", required = true)
            @Valid @RequestBody BatchDeleteReq batchReq) {
        log.info("接收批量删除用户请求: userIds={}", batchReq.getUserIds());
        return adminUserService.batchDeleteUsers(batchReq);
    }

    /**
     * 启用/禁用用户
     */
    @Operation(summary = "启用/禁用用户", description = "更新指定用户的启用状态")
    @PutMapping("/{userId}/status")
    @OperationLog(type = "admin_user_status_update", description = "管理员更新用户状态")
    public Result<String> updateUserStatus(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "状态更新请求体：status 用户状态", required = true)
            @RequestBody Map<String, Integer> requestBody) {
        Integer status = requestBody.get("status");
        log.info("接收更新用户状态请求: userId={}, status={}", userId, status);
        return adminUserService.updateUserStatus(userId, status);
    }

    /**
     * 重置用户密码
     */
    @Operation(summary = "重置用户密码", description = "重置指定用户的密码")
    @PutMapping("/{userId}/reset-password")
    @OperationLog(type = "admin_user_password_reset", description = "管理员重置用户密码")
    public Result<String> resetPassword(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "重置密码请求体", required = true)
            @Valid @RequestBody ResetPasswordReq resetReq) {
        log.info("接收重置用户密码请求: userId={}", userId);
        return adminUserService.resetPassword(userId, resetReq);
    }

    /**
     * 获取用户详细信息
     */
    @Operation(summary = "获取用户详细信息", description = "查询指定用户的详细信息")
    @GetMapping("/{userId}")
    public Result<UserInfoDTO> getUserDetail(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId) {
        log.info("接收获取用户详细信息请求: userId={}", userId);
        return adminUserService.getUserDetail(userId);
    }

    /**
     * 获取用户登录历史
     */
    @Operation(summary = "获取用户登录历史", description = "分页查询指定用户的登录历史")
    @GetMapping("/{userId}/login-history")
    public Result<Page<SysLoginLog>> getUserLoginHistory(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size) {
        
        log.info("接收获取用户登录历史请求: userId={}, page={}, size={}", userId, page, size);
        return adminUserService.getUserLoginHistory(userId, page, size);
    }
}