package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.admin.dto.PerformanceConfigDTO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.SystemConfig;

/**
 * 系统配置服务接口
 */
public interface SystemConfigService {
    
    /**
     * 更新性能配置
     */
    Result<Void> updatePerformanceConfig(PerformanceConfigDTO performanceConfig);
    
    /**
     * 获取性能配置
     */
    Result<PerformanceConfigDTO> getPerformanceConfig();
    
    /**
     * 应用性能配置到系统
     */
    void applyPerformanceConfig(PerformanceConfigDTO config);
    
    /**
     * 获取默认性能配置
     */
    PerformanceConfigDTO getDefaultPerformanceConfig();
    
    // ==================== 新增配置管理方法 ====================
    
    /**
     * 根据配置键获取配置
     */
    SystemConfig getConfigByKey(String configKey);
    
    /**
     * 保存配置
     */
    void saveConfig(String configKey, String configValue, String description);
    
    /**
     * 删除所有配置
     */
    void deleteAllConfigs();
    
    /**
     * 刷新配置缓存
     */
    void refreshConfigCache();
    
    /**
     * 获取时间节点配置
     */
    com.abin.checkrepeatservice.student.dto.DeadlinesDTO getDeadlines();
    
    /**
     * 更新时间节点配置
     */
    void updateDeadlines(com.abin.checkrepeatservice.student.dto.DeadlinesDTO deadlines);
}