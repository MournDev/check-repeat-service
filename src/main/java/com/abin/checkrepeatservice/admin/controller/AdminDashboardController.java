package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminDashboardService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;

/**
 * 管理员系统概览控制器
 * 职责：接收HTTP请求，调用服务层，返回响应结果
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class AdminDashboardController {

    @Resource
    private AdminDashboardService adminDashboardService;

    /**
     * 获取系统统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getSystemStats() {
        log.info("接收获取系统统计数据请求");
        try {
            return adminDashboardService.getSystemStats();
        } catch (Exception e) {
            log.error("获取系统统计数据失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取系统统计数据失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取快捷操作菜单
     */
    @GetMapping("/quick-actions")
    public Result<List<Map<String, Object>>> getQuickActions() {
        log.info("接收获取快捷操作菜单请求");
        try {
            return adminDashboardService.getQuickActions();
        } catch (Exception e) {
            log.error("获取快捷操作菜单失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取快捷操作菜单失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取实时统计信息
     */
    @GetMapping("/realtime-stats")
    public Result<Map<String, Object>> getRealtimeStats() {
        log.info("接收获取实时统计数据请求");
        try {
            return adminDashboardService.getRealtimeStats();
        } catch (Exception e) {
            log.error("获取实时统计数据失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取实时统计数据失败: " + "系统异常，请稍后重试");
        }
    }
}