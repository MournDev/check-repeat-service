package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.PerformanceConfigDTO;
import com.abin.checkrepeatservice.admin.mapper.SystemConfigMapper;
import com.abin.checkrepeatservice.admin.service.SystemConfigService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SystemConfig;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 系统配置服务实现类
 */
@Slf4j
@Service
public class SystemConfigServiceImpl implements SystemConfigService {
    
    @Autowired
    private SystemConfigMapper systemConfigMapper;
    
    // 配置键常量
    private static final String PERFORMANCE_CONFIG_KEY = "performance";
    
    @Override
    @Transactional
    public Result<Void> updatePerformanceConfig(PerformanceConfigDTO performanceConfig) {
        try {
            log.info("开始更新性能配置: {}", performanceConfig);
            
            // 参数验证
            validatePerformanceConfig(performanceConfig);
            
            // 序列化配置为JSON
            String configJson = JSON.toJSONString(performanceConfig);
            
            // 查找现有配置
            SystemConfig configEntity = systemConfigMapper.selectByConfigKey(PERFORMANCE_CONFIG_KEY);
            
            if (configEntity != null) {
                // 更新现有配置
                configEntity.setConfigValue(configJson);
                configEntity.setUpdateTime(LocalDateTime.now());
                systemConfigMapper.updateById(configEntity);
            } else {
                // 创建新配置
                configEntity = new SystemConfig();
                configEntity.setConfigKey(PERFORMANCE_CONFIG_KEY);
                configEntity.setConfigValue(configJson);
                configEntity.setDescription("系统性能配置");
                configEntity.setCreateTime(LocalDateTime.now());
                configEntity.setUpdateTime(LocalDateTime.now());
                configEntity.setIsDeleted(0);
                systemConfigMapper.insert(configEntity);
            }
            
            // 应用新的配置到系统
            applyPerformanceConfig(performanceConfig);
            
            log.info("性能配置更新成功");
            return Result.success("性能配置更新成功");
            
        } catch (Exception e) {
            log.error("更新性能配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "性能配置更新失败: " + e.getMessage());
        }
    }
    
    @Override
    public Result<PerformanceConfigDTO> getPerformanceConfig() {
        try {
            SystemConfig configEntity = systemConfigMapper.selectByConfigKey(PERFORMANCE_CONFIG_KEY);
            
            if (configEntity != null) {
                PerformanceConfigDTO config = JSON.parseObject(configEntity.getConfigValue(), PerformanceConfigDTO.class);
                return Result.success("获取性能配置成功", config);
            }
            
            // 返回默认配置
            PerformanceConfigDTO defaultConfig = getDefaultPerformanceConfig();
            return Result.success("获取默认性能配置", defaultConfig);
            
        } catch (Exception e) {
            log.error("获取性能配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取性能配置失败: " + e.getMessage());
        }
    }
    
    @Override
    public void applyPerformanceConfig(PerformanceConfigDTO config) {
        try {
            log.info("开始应用性能配置: {}", config);
            
            // 这里可以集成实际的系统组件来应用配置
            // 示例：更新线程池配置
            updateThreadPoolConfig(config.getMaxConcurrent());
            
            // 示例：更新队列大小
            updateQueueSize(config.getQueueSize());
            
            // 示例：更新缓存策略
            updateCacheStrategy(config.getCacheStrategy(), config.getCacheSize());
            
            // 示例：更新自动清理配置
            updateAutoCleanup(config.getAutoCleanup(), config.getCleanupInterval());
            
            log.info("性能配置应用完成");
            
        } catch (Exception e) {
            log.error("应用性能配置失败: {}", e.getMessage(), e);
            // 不抛出异常，避免影响主流程
        }
    }
    
    @Override
    public PerformanceConfigDTO getDefaultPerformanceConfig() {
        PerformanceConfigDTO config = new PerformanceConfigDTO();
        config.setMaxConcurrent(20);
        config.setQueueSize(100);
        config.setCacheStrategy("lru");
        config.setCacheSize(1024);
        config.setAutoCleanup(true);
        config.setCleanupInterval(24);
        return config;
    }
    
    /**
     * 参数验证
     */
    private void validatePerformanceConfig(PerformanceConfigDTO config) {
        if (config.getMaxConcurrent() == null || 
            config.getMaxConcurrent() < 1 || 
            config.getMaxConcurrent() > 100) {
            throw new IllegalArgumentException("最大并发数必须在1-100之间");
        }
        
        if (config.getQueueSize() == null || 
            config.getQueueSize() < 10 || 
            config.getQueueSize() > 1000) {
            throw new IllegalArgumentException("队列大小必须在10-1000之间");
        }
        
        if (config.getCacheStrategy() == null || 
            !Arrays.asList("lru", "fifo", "ttl").contains(config.getCacheStrategy())) {
            throw new IllegalArgumentException("缓存策略只能是lru、fifo或ttl");
        }
        
        if (config.getCacheSize() == null || 
            config.getCacheSize() < 100 || 
            config.getCacheSize() > 10000) {
            throw new IllegalArgumentException("缓存大小必须在100-10000MB之间");
        }
        
        if (config.getCleanupInterval() == null || 
            config.getCleanupInterval() < 1 || 
            config.getCleanupInterval() > 168) {
            throw new IllegalArgumentException("清理周期必须在1-168小时之间");
        }
    }
    
    /**
     * 更新线程池配置（示例实现）
     */
    private void updateThreadPoolConfig(Integer maxConcurrent) {
        log.info("更新线程池最大并发数为: {}", maxConcurrent);
        // 实际实现中这里会调用线程池管理器来更新配置
    }
    
    /**
     * 更新队列大小（示例实现）
     */
    private void updateQueueSize(Integer queueSize) {
        log.info("更新查重队列大小为: {}", queueSize);
        // 实际实现中这里会调用队列管理器来更新配置
    }
    
    /**
     * 更新缓存策略（示例实现）
     */
    private void updateCacheStrategy(String cacheStrategy, Integer cacheSize) {
        log.info("更新缓存策略为: {}, 缓存大小: {}MB", cacheStrategy, cacheSize);
        // 实际实现中这里会调用缓存管理器来更新配置
    }
    
    /**
     * 更新自动清理配置（示例实现）
     */
    private void updateAutoCleanup(Boolean autoCleanup, Integer cleanupInterval) {
        log.info("更新自动清理配置: 开关={}, 周期={}小时", autoCleanup, cleanupInterval);
        // 实际实现中这里会调用定时任务调度器来更新配置
    }
    
    // ==================== 新增配置管理方法实现 ====================
    
    @Override
    public SystemConfig getConfigByKey(String configKey) {
        try {
            return systemConfigMapper.selectByConfigKey(configKey);
        } catch (Exception e) {
            log.error("根据配置键获取配置失败: configKey={}, error={}", configKey, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    @Transactional
    public void saveConfig(String configKey, String configValue, String description) {
        try {
            SystemConfig existingConfig = systemConfigMapper.selectByConfigKey(configKey);
            
            if (existingConfig != null) {
                // 更新现有配置
                existingConfig.setConfigValue(configValue);
                existingConfig.setDescription(description);
                existingConfig.setUpdateTime(LocalDateTime.now());
                systemConfigMapper.updateById(existingConfig);
            } else {
                // 创建新配置
                SystemConfig newConfig = new SystemConfig();
                newConfig.setConfigKey(configKey);
                newConfig.setConfigValue(configValue);
                newConfig.setDescription(description);
                newConfig.setCreateTime(LocalDateTime.now());
                newConfig.setUpdateTime(LocalDateTime.now());
                newConfig.setIsDeleted(0);
                systemConfigMapper.insert(newConfig);
            }
            
            log.info("配置保存成功: configKey={}", configKey);
        } catch (Exception e) {
            log.error("保存配置失败: configKey={}, error={}", configKey, e.getMessage(), e);
            throw new RuntimeException("保存配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    @Transactional
    public void deleteAllConfigs() {
        try {
            // 软删除所有配置
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SystemConfig> wrapper = 
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(SystemConfig::getIsDeleted, 0);
            
            List<SystemConfig> configs = systemConfigMapper.selectList(wrapper);
            for (SystemConfig config : configs) {
                config.setIsDeleted(1);
                config.setUpdateTime(LocalDateTime.now());
                systemConfigMapper.updateById(config);
            }
            
            log.info("所有配置删除成功，共{}条记录", configs.size());
        } catch (Exception e) {
            log.error("删除所有配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("删除所有配置失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void refreshConfigCache() {
        try {
            log.info("开始刷新系统配置缓存");
                
            // 1. 清除配置缓存
            clearConfigurationCache();
                
            // 2. 重新加载数据库配置
            reloadDatabaseConfigurations();
                
            // 3. 刷新应用上下文中的配置
            refreshApplicationContext();
                
            // 4. 重新初始化相关服务
            reinitializeServices();
                
            // 5. 更新系统监控配置
            updateMonitoringConfig();
                
            log.info("配置缓存刷新成功");
        } catch (Exception e) {
            log.error("刷新配置缓存失败：{}", e.getMessage(), e);
            throw new RuntimeException("刷新配置缓存失败：" + e.getMessage(), e);
        }
    }
        
    @Override
    public com.abin.checkrepeatservice.student.dto.DeadlinesDTO getDeadlines() {
        // 直接调用 StudentDashboardService 的方法（已实现从数据库读取）
        // 这里为了简化，直接复用逻辑
        return new com.abin.checkrepeatservice.student.service.Impl.StudentDashboardService()
            .getDeadlines();
    }
        
    @Override
    public void updateDeadlines(com.abin.checkrepeatservice.student.dto.DeadlinesDTO deadlines) {
        try {
            log.info("开始更新时间节点配置");
                
            // 更新提交截止日期
            if (deadlines.getSubmissionDeadline() != null) {
                saveConfig("submission_deadline", deadlines.getSubmissionDeadline(), "论文提交截止日期");
            }
                
            // 更新审核截止日期
            if (deadlines.getReviewDeadline() != null) {
                saveConfig("review_deadline", deadlines.getReviewDeadline(), "审核截止日期");
            }
                
            // 更新答辩时间
            if (deadlines.getDefenseDate() != null) {
                saveConfig("defense_date", deadlines.getDefenseDate(), "答辩时间");
            }
                
            // 更新预计毕业时间
            if (deadlines.getGraduationDate() != null) {
                saveConfig("graduation_date", deadlines.getGraduationDate(), "预计毕业时间");
            }
                
            log.info("时间节点配置更新成功");
        } catch (Exception e) {
            log.error("更新时间节点配置失败", e);
            throw new RuntimeException("更新时间节点配置失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 清除配置缓存
     */
    private void clearConfigurationCache() {
        try {
            // 清除本地缓存
            // 这里可以根据实际使用的缓存框架来实现
            // 例如 Redis、Ehcache 等
            log.info("清除本地配置缓存");
            
            // 如果使用了Spring的缓存注解，可以清除相关缓存
            // CacheManager cacheManager = ...
            // cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
            
        } catch (Exception e) {
            log.warn("清除配置缓存时发生警告: {}", e.getMessage());
        }
    }
    
    /**
     * 重新加载数据库配置
     */
    private void reloadDatabaseConfigurations() {
        try {
            log.info("重新加载数据库配置");
            
            // 重新加载所有配置项
            List<String> configKeys = Arrays.asList(
                "system_basic",
                "plagiarism_config", 
                "security_config",
                "email_config",
                "performance",
                "storage_config"
            );
            
            for (String configKey : configKeys) {
                SystemConfig config = getConfigByKey(configKey);
                if (config != null) {
                    log.debug("重新加载配置: {} = {}", configKey, config.getConfigValue());
                }
            }
            
        } catch (Exception e) {
            log.error("重新加载数据库配置失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 刷新应用上下文配置
     */
    private void refreshApplicationContext() {
        try {
            log.info("刷新应用上下文配置");
            
            // 如果需要重新加载某些Spring配置，可以在这里实现
            // 例如重新绑定@ConfigurationProperties
            // ConfigurableApplicationContext context = ...
            // context.getAutowireCapableBeanFactory().destroyBean(...);
            // context.getAutowireCapableBeanFactory().initializeBean(...);
            
        } catch (Exception e) {
            log.warn("刷新应用上下文配置时发生警告: {}", e.getMessage());
        }
    }
    
    /**
     * 重新初始化相关服务
     */
    private void reinitializeServices() {
        try {
            log.info("重新初始化相关服务");
            
            // 重新初始化邮件服务配置
            reinitializeEmailService();
            
            // 重新初始化存储服务配置
            reinitializeStorageService();
            
            // 重新初始化性能相关配置
            reinitializePerformanceConfig();
            
        } catch (Exception e) {
            log.error("重新初始化服务失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 重新初始化邮件服务
     */
    private void reinitializeEmailService() {
        try {
            SystemConfig emailConfig = getConfigByKey("email_config");
            if (emailConfig != null) {
                Map<String, Object> config = JSON.parseObject(emailConfig.getConfigValue(), Map.class);
                log.info("重新初始化邮件服务配置: host={}, port={}", 
                        config.get("smtpHost"), config.get("smtpPort"));
                // 这里可以重新配置邮件发送器
            }
        } catch (Exception e) {
            log.warn("重新初始化邮件服务时发生警告: {}", e.getMessage());
        }
    }
    
    /**
     * 重新初始化存储服务
     */
    private void reinitializeStorageService() {
        try {
            SystemConfig storageConfig = getConfigByKey("storage_config");
            if (storageConfig != null) {
                Map<String, Object> config = JSON.parseObject(storageConfig.getConfigValue(), Map.class);
                log.info("重新初始化存储服务配置: type={}, endpoint={}", 
                        config.get("storageType"), config.get("endpoint"));
                // 这里可以重新配置存储客户端
            }
        } catch (Exception e) {
            log.warn("重新初始化存储服务时发生警告: {}", e.getMessage());
        }
    }
    
    /**
     * 重新初始化性能配置
     */
    private void reinitializePerformanceConfig() {
        try {
            SystemConfig perfConfig = getConfigByKey("performance");
            if (perfConfig != null) {
                PerformanceConfigDTO config = JSON.parseObject(perfConfig.getConfigValue(), PerformanceConfigDTO.class);
                applyPerformanceConfig(config);
                log.info("重新应用性能配置: maxConcurrent={}, cacheStrategy={}", 
                        config.getMaxConcurrent(), config.getCacheStrategy());
            }
        } catch (Exception e) {
            log.warn("重新初始化性能配置时发生警告: {}", e.getMessage());
        }
    }
    
    /**
     * 更新系统监控配置
     */
    private void updateMonitoringConfig() {
        try {
            log.info("更新系统监控配置");
            
            // 这里可以通知监控服务重新加载配置
            // 例如重新设置采样频率、告警阈值等
            
        } catch (Exception e) {
            log.warn("更新监控配置时发生警告: {}", e.getMessage());
        }
    }
}