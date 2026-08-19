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
public class AdminUserController {

    @Resource
    private AdminUserService adminUserService;

    /**
     * 获取用户列表（分页）
     */
    @GetMapping("/list")
    public Result<Page<UserInfoDTO>> getUserList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        
        log.info("接收获取用户列表请求: page={}, size={}, userType={}, status={}, keyword={}",
                page, size, userType, status, keyword);
        try {
            return adminUserService.getUserList(page, size, userType, status, keyword);
        } catch (Exception e) {
            log.error("获取用户列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取用户列表失败: " + e.getMessage());
        }
    }

    /**
     * 创建新用户
     */
    @PostMapping("/create")
    @OperationLog(type = "admin_user_create", description = "管理员创建用户", recordResult = true)
    public Result<Map<String, Object>> createUser(@Valid @RequestBody UserCreateReq createReq) {
        log.info("接收创建用户请求: username={}", createReq.getUsername());
        try {
            return adminUserService.createUser(createReq);
        } catch (Exception e) {
            log.error("创建用户失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "创建用户失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{userId}")
    @OperationLog(type = "admin_user_update", description = "管理员更新用户信息")
    public Result<String> updateUser(@PathVariable Long userId,
                                     @Valid @RequestBody UserUpdateReq updateReq) {
        log.info("接收更新用户信息请求: userId={}", userId);
        try {
            return adminUserService.updateUser(userId, updateReq);
        } catch (Exception e) {
            log.error("更新用户信息失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新用户信息失败: " + e.getMessage());
        }
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    @OperationLog(type = "admin_user_delete", description = "管理员删除用户")
    public Result<String> deleteUser(@PathVariable Long userId) {
        log.info("接收删除用户请求: userId={}", userId);
        try {
            return adminUserService.deleteUser(userId);
        } catch (Exception e) {
            log.error("删除用户失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除用户失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除用户
     */
    @PostMapping("/batch-delete")
    @OperationLog(type = "admin_user_batch_delete", description = "管理员批量删除用户")
    public Result<String> batchDeleteUsers(@Valid @RequestBody BatchDeleteReq batchReq) {
        log.info("接收批量删除用户请求: userIds={}", batchReq.getUserIds());
        try {
            return adminUserService.batchDeleteUsers(batchReq);
        } catch (Exception e) {
            log.error("批量删除用户失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量删除用户失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用用户
     */
    @PutMapping("/{userId}/status")
    @OperationLog(type = "admin_user_status_update", description = "管理员更新用户状态")
    public Result<String> updateUserStatus(@PathVariable Long userId,
                                           @RequestBody Map<String, Integer> requestBody) {
        Integer status = requestBody.get("status");
        log.info("接收更新用户状态请求: userId={}, status={}", userId, status);
        try {
            return adminUserService.updateUserStatus(userId, status);
        } catch (Exception e) {
            log.error("更新用户状态失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新用户状态失败: " + e.getMessage());
        }
    }

    /**
     * 重置用户密码
     */
    @PutMapping("/{userId}/reset-password")
    @OperationLog(type = "admin_user_password_reset", description = "管理员重置用户密码")
    public Result<String> resetPassword(@PathVariable Long userId,
                                        @Valid @RequestBody ResetPasswordReq resetReq) {
        log.info("接收重置用户密码请求: userId={}", userId);
        try {
            return adminUserService.resetPassword(userId, resetReq);
        } catch (Exception e) {
            log.error("重置用户密码失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "重置用户密码失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户详细信息
     */
    @GetMapping("/{userId}")
    public Result<UserInfoDTO> getUserDetail(@PathVariable Long userId) {
        log.info("接收获取用户详细信息请求: userId={}", userId);
        try {
            return adminUserService.getUserDetail(userId);
        } catch (Exception e) {
            log.error("获取用户详细信息失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取用户详细信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户登录历史
     */
    @GetMapping("/{userId}/login-history")
    public Result<Page<SysLoginLog>> getUserLoginHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        
        log.info("接收获取用户登录历史请求: userId={}, page={}, size={}", userId, page, size);
        try {
            return adminUserService.getUserLoginHistory(userId, page, size);
        } catch (Exception e) {
            log.error("获取用户登录历史失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取用户登录历史失败: " + e.getMessage());
        }
    }
}