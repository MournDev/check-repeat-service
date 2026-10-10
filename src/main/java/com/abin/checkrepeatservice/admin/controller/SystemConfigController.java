package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.PerformanceConfigDTO;
import com.abin.checkrepeatservice.admin.service.SystemConfigService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置控制器
 *
 * 职责分离（P1）：性能配置（并发上限/队列/缓存策略）是全局运行时调优，
 * 误配会拖垮整个查重链路，写操作（更新/重置/试应用）收归超级管理员；
 * 读取保持 ADMIN 可见（配置页仍可展示当前值）。
 */
@RestController
@RequestMapping("/api/admin/config")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    /**
     * 更新性能配置（仅超级管理员）
     */
    @PutMapping("/performance")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @OperationLog(type = "admin_config_performance_update", description = "管理员更新性能配置", recordResult = true)
    public Result<Void> updatePerformanceConfig(@Valid @RequestBody PerformanceConfigDTO performanceConfig) {
        log.info("接收更新性能配置请求: maxConcurrent={}, queueSize={}, cacheStrategy={}",
                performanceConfig.getMaxConcurrent(),
                performanceConfig.getQueueSize(),
                performanceConfig.getCacheStrategy());
        
        try {
            return systemConfigService.updatePerformanceConfig(performanceConfig);
        } catch (Exception e) {
            log.error("更新性能配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR,"更新性能配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取性能配置
     */
    @GetMapping("/performance")
    public Result<PerformanceConfigDTO> getPerformanceConfig() {
        log.info("接收获取性能配置请求");
        
        try {
            return systemConfigService.getPerformanceConfig();
        } catch (Exception e) {
            log.error("获取性能配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR,"获取性能配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 重置性能配置为默认值（仅超级管理员）
     */
    @PostMapping("/performance/reset")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @OperationLog(type = "admin_config_performance_reset", description = "管理员重置性能配置为默认值")
    public Result<Void> resetPerformanceConfig() {
        log.info("接收重置性能配置请求");
        
        try {
            PerformanceConfigDTO defaultConfig = systemConfigService.getDefaultPerformanceConfig();
            return systemConfigService.updatePerformanceConfig(defaultConfig);
        } catch (Exception e) {
            log.error("重置性能配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR,"重置性能配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试性能配置应用（仅超级管理员；试应用同样会实际生效到运行时线程池/缓存）
     */
    @PostMapping("/performance/test")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @OperationLog(type = "admin_config_performance_test", description = "管理员测试应用性能配置")
    public Result<Void> testPerformanceConfig(@Valid @RequestBody PerformanceConfigDTO performanceConfig) {
        log.info("接收测试性能配置请求: {}", performanceConfig);
        
        try {
            // 仅测试配置应用，不保存到数据库
            systemConfigService.applyPerformanceConfig(performanceConfig);
            return Result.success("性能配置测试应用成功");
        } catch (Exception e) {
            log.error("测试性能配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR,"测试性能配置失败: " + e.getMessage());
        }
    }
}