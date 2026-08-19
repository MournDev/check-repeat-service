package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.PerformanceConfigDTO;
import com.abin.checkrepeatservice.admin.service.SystemConfigService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置控制器
 */
@RestController
@RequestMapping("/api/admin/config")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class SystemConfigController {
    
    @Autowired
    private SystemConfigService systemConfigService;
    
    /**
     * 更新性能配置
     */
    @PutMapping("/performance")
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
     * 重置性能配置为默认值
     */
    @PostMapping("/performance/reset")
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
     * 测试性能配置应用
     */
    @PostMapping("/performance/test")
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