package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.common.Result;

import java.util.List;
import java.util.Map;

/**
 * 管理员仪表板服务接口
 */
public interface AdminDashboardService {
    
    /**
     * 获取系统统计数据
     */
    Result<Map<String, Object>> getSystemStats();


    /**
     * 获取快捷操作菜单
     */
    Result<List<Map<String, Object>>> getQuickActions();
    
    /**
     * 获取实时统计信息
     */
    Result<Map<String, Object>> getRealtimeStats();
}