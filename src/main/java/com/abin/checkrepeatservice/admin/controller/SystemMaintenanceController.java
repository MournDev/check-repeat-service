package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.MaintenanceUpdateReq;
import com.abin.checkrepeatservice.admin.service.MaintenanceService;
import com.abin.checkrepeatservice.common.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;

/**
 * 系统维护模式控制器
 *
 * <p>两个路径、两档权限（前端契约固定，勿合并路径）：</p>
 * <ul>
 *   <li>GET /api/system/status —— 公开（未登录可访问），已在 SecurityConfig
 *       permitAll 清单放行；前端启动/刷新时拉取维护状态</li>
 *   <li>PUT /api/admin/system/maintenance —— 仅 SUPER_ADMIN；URL 门禁
 *       /api/admin/** 是 ADMIN，方法级 @PreAuthorize 收紧到超管</li>
 * </ul>
 */
@RestController
@Slf4j
@Tag(name = "系统维护模式", description = "系统维护状态查询与维护模式更新")
public class SystemMaintenanceController {

    @Resource
    private MaintenanceService maintenanceService;

    /**
     * 系统状态查询（公开）
     * 出参：maintenanceStatus / maintenanceNotice / maintenanceStartTime / maintenanceEndTime
     */
    @Operation(summary = "系统状态查询", description = "公开接口，返回维护状态与维护公告等信息")
    @GetMapping("/api/system/status")
    public Result<Map<String, Object>> getSystemStatus() {
        return maintenanceService.getSystemStatus();
    }

    /**
     * 更新维护模式（仅超管）
     * 行为：落库 system_param → 刷新内存缓存 → WS 广播 MAINTENANCE_STATUS_CHANGED
     * 出参：data 回传最新状态，前端用它刷新 store
     */
    @Operation(summary = "更新维护模式", description = "落库并刷新缓存后广播维护状态变更（仅超管）")
    @PutMapping("/api/admin/system/maintenance")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<Map<String, Object>> updateMaintenance(@Parameter(description = "维护模式更新请求体：状态与公告", required = true) @Valid @RequestBody MaintenanceUpdateReq req) {
        log.info("接收维护模式更新请求: status={}, notice={}",
                req.getMaintenanceStatus(), req.getMaintenanceNotice());
        return maintenanceService.updateMaintenance(req);
    }
}
