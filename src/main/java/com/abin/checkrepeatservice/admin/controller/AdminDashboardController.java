package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminDashboardService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "系统概览", description = "管理员仪表盘统计与快捷操作")
public class AdminDashboardController {

    @Resource
    private AdminDashboardService adminDashboardService;

    /**
     * 获取系统统计数据
     */
    @Operation(summary = "获取系统统计数据", description = "获取管理员首页的系统统计数据")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getSystemStats() {
        log.info("接收获取系统统计数据请求");
        return adminDashboardService.getSystemStats();
    }

    /**
     * 获取快捷操作菜单
     */
    @Operation(summary = "获取快捷操作菜单", description = "获取管理员首页的快捷操作入口")
    @GetMapping("/quick-actions")
    public Result<List<Map<String, Object>>> getQuickActions() {
        log.info("接收获取快捷操作菜单请求");
        return adminDashboardService.getQuickActions();
    }

    /**
     * 获取实时统计信息
     */
    @Operation(summary = "获取实时统计信息", description = "获取管理员首页的实时统计数据")
    @GetMapping("/realtime-stats")
    public Result<Map<String, Object>> getRealtimeStats() {
        log.info("接收获取实时统计数据请求");
        return adminDashboardService.getRealtimeStats();
    }
}