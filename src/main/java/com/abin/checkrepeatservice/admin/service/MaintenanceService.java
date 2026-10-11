package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.MaintenanceUpdateReq;
import com.abin.checkrepeatservice.common.Result;

import java.util.Map;

/**
 * 维护模式服务
 * 前端契约：GET /api/system/status（公开）+ PUT /api/admin/system/maintenance（仅超管）
 */
public interface MaintenanceService {

    /**
     * 查询系统状态（公开接口，未登录可访问）
     * 出参：maintenanceStatus / maintenanceNotice / maintenanceStartTime / maintenanceEndTime
     * 时间格式 yyyy-MM-dd HH:mm:ss，无值时为 null
     */
    Result<Map<String, Object>> getSystemStatus();

    /**
     * 更新维护模式（仅超管）：落库 system_param → 刷新内存缓存 → 广播状态变更
     */
    Result<Map<String, Object>> updateMaintenance(MaintenanceUpdateReq req);
}
