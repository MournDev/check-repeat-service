package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.SystemConfigService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SystemConfig;
import com.abin.checkrepeatservice.user.service.Impl.EmailService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置管理控制器
 * 提供完整的系统配置管理功能
 *
 * 职责分离（P1）：本控制器维护 system_param 全量配置（基础/查重/安全/邮件/性能），
 * 属全局运行时开关，写操作一律收归超级管理员；GET 读取保持 ADMIN 可见
 * （配置页仍可展示当前值）。export 会导出含密钥类敏感项的配置，同样收归超管。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/system/config")
@PreAuthorize("hasAuthority('ADMIN')")
public class SystemConfigManagementController {

    @Resource
    private SystemConfigService systemConfigService;

    @Resource
    private EmailService emailService;

    /**
     * 1. 获取所有系统配置
     * 用途：初始化页面时加载所有配置项
     */
    @GetMapping
    public Result<Map<String, Object>> getAllConfig() {
        log.info("接收获取所有系统配置请求");
        try {
            Map<String, Object> allConfig = new HashMap<>();
            
            // 获取基础配置
            Map<String, Object> basicConfig = getBasicConfig();
            allConfig.put("basicConfig", basicConfig);
            
            // 获取查重配置
            Map<String, Object> plagiarismConfig = getPlagiarismConfig();
            allConfig.put("plagiarismConfig", plagiarismConfig);
            
            // 获取安全配置
            Map<String, Object> securityConfig = getSecurityConfig();
            allConfig.put("securityConfig", securityConfig);
            
            // 获取邮件配置
            Map<String, Object> emailConfig = getEmailConfig();
            allConfig.put("emailConfig", emailConfig);
            
            // 获取性能配置
            Map<String, Object> performanceConfig = getPerformanceConfig();
            allConfig.put("performanceConfig", performanceConfig);
            
            log.info("获取所有系统配置成功");
            return Result.success(allConfig);
            
        } catch (Exception e) {
            log.error("获取系统配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取系统配置失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 2. 更新基础配置
     * 用途：保存基础配置项（系统名称、语言、时区等）
     */
    @PutMapping("/basic")
    @OperationLog(type = "config_basic_update", description = "更新基础配置")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<String> updateBasicConfig(@RequestBody Map<String, Object> config) {
        log.info("接收更新基础配置请求: {}", config);
        try {
            saveConfig("system_basic", config, "系统基础配置");
            log.info("基础配置更新成功");
            return Result.success("基础配置更新成功");
        } catch (Exception e) {
            log.error("更新基础配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新基础配置失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 3. 更新查重配置
     * 用途：保存查重相关配置（阈值、算法、缓存等）
     */
    @PutMapping("/plagiarism")
    @OperationLog(type = "config_plagiarism_update", description = "更新查重配置")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<String> updatePlagiarismConfig(@RequestBody Map<String, Object> config) {
        log.info("接收更新查重配置请求: {}", config);
        try {
            saveConfig("plagiarism_config", config, "查重配置");
            log.info("查重配置更新成功");
            return Result.success("查重配置更新成功");
        } catch (Exception e) {
            log.error("更新查重配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新查重配置失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 4. 更新安全配置
     * 用途：保存安全相关配置（密码策略、登录限制等）
     */
    @PutMapping("/security")
    @OperationLog(type = "config_security_update", description = "更新安全配置")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<String> updateSecurityConfig(@RequestBody Map<String, Object> config) {
        log.info("接收更新安全配置请求: {}", config);
        try {
            saveConfig("security_config", config, "安全配置");
            log.info("安全配置更新成功");
            return Result.success("安全配置更新成功");
        } catch (Exception e) {
            log.error("更新安全配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新安全配置失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 5. 更新邮件配置
     * 用途：保存邮件服务器配置
     */
    @PutMapping("/email")
    @OperationLog(type = "config_email_update", description = "更新邮件配置")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<String> updateEmailConfig(@RequestBody Map<String, Object> config) {
        log.info("接收更新邮件配置请求: {}", config);
        try {
            saveConfig("email_config", config, "邮件配置");
            log.info("邮件配置更新成功");
            return Result.success("邮件配置更新成功");
        } catch (Exception e) {
            log.error("更新邮件配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新邮件配置失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 6. 更新性能配置
     * 用途：保存性能相关配置（并发数、缓存策略等）
     */
    @PutMapping("/performance")
    @OperationLog(type = "config_performance_update", description = "更新性能配置")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<String> updatePerformanceConfig(@RequestBody Map<String, Object> config) {
        log.info("接收更新性能配置请求: {}", config);
        try {
            saveConfig("performance", config, "性能配置");
            log.info("性能配置更新成功");
            return Result.success("性能配置更新成功");
        } catch (Exception e) {
            log.error("更新性能配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新性能配置失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 7. 保存全部配置接口
     * 用途：批量保存所有配置项
     */
    @PostMapping("/save-all")
    @OperationLog(type = "config_save_all", description = "保存全部配置", recordResult = true)
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<String> saveAllConfig(@RequestBody Map<String, Object> allConfig) {
        log.info("接收保存全部配置请求");
        try {
            // 保存各个配置项
            if (allConfig.containsKey("basicConfig")) {
                saveConfig("system_basic", (Map<String, Object>) allConfig.get("basicConfig"), "系统基础配置");
            }
            
            if (allConfig.containsKey("plagiarismConfig")) {
                saveConfig("plagiarism_config", (Map<String, Object>) allConfig.get("plagiarismConfig"), "查重配置");
            }
            
            if (allConfig.containsKey("securityConfig")) {
                saveConfig("security_config", (Map<String, Object>) allConfig.get("securityConfig"), "安全配置");
            }
            
            if (allConfig.containsKey("emailConfig")) {
                saveConfig("email_config", (Map<String, Object>) allConfig.get("emailConfig"), "邮件配置");
            }
            
            if (allConfig.containsKey("performanceConfig")) {
                saveConfig("performance", (Map<String, Object>) allConfig.get("performanceConfig"), "性能配置");
            }
            
            log.info("全部配置保存成功");
            return Result.success("全部配置保存成功");
        } catch (Exception e) {
            log.error("保存全部配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "保存全部配置失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 8. 测试邮件配置接口
     * 用途：验证邮件配置是否正确（仅超级管理员：借全局 SMTP 发信）
     */
    @PostMapping("/test-email")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @OperationLog(type = "config_email_test", description = "管理员发送测试邮件")
    public Result<String> testEmailConfig(@RequestBody Map<String, String> request) {
        // 兼容两套前端字段名：系统配置页用 recipientEmail，规则配置页用 testEmail
        String recipientEmail = request.get("recipientEmail");
        if (recipientEmail == null || recipientEmail.isEmpty()) {
            recipientEmail = request.get("testEmail");
        }
        log.info("接收测试邮件配置请求: recipientEmail={}", recipientEmail);
        
        if (recipientEmail == null || recipientEmail.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "收件人邮箱不能为空");
        }
        
        try {
            // 真实走 SMTP 发送：失败必须如实返回，避免管理员误判配置可用
            boolean sent = emailService.sendConfigTestEmail(recipientEmail);
            if (!sent) {
                log.warn("邮件配置测试失败: recipientEmail={}", recipientEmail);
                return Result.error(ResultCode.SYSTEM_ERROR,
                        "测试邮件发送失败，请检查 SMTP 配置与收件地址");
            }
            log.info("邮件配置测试成功: {}", recipientEmail);
            return Result.success("邮件配置测试成功，邮件已发送至: " + recipientEmail);
        } catch (Exception e) {
            log.error("邮件配置测试异常: recipientEmail={}", recipientEmail, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "邮件配置测试失败，系统异常，请稍后重试");
        }
    }

    /**
     * 9. 导出配置接口
     * 用途：将当前配置导出为JSON文件（仅超级管理员：导出物含密钥类敏感项）
     * 路径：/api/admin/system/config/export
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public void exportConfig(HttpServletResponse response) {
        log.info("接收导出配置请求");
        try {
            // 获取所有配置
            Result<Map<String, Object>> configResult = getAllConfig();
            Map<String, Object> allConfig = configResult.getData();
            
            // 设置响应头
            String fileName = "system-config-" + LocalDateTime.now().toString().substring(0, 19).replace(":", "-") + ".json";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());
            
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName);
            
            // 写入JSON数据（fastjson2 用 PrettyFormat 特性替代 1.x 的 toJSONString(obj, true)）
            String jsonContent = JSON.toJSONString(allConfig, JSONWriter.Feature.PrettyFormat);
            response.getWriter().write(jsonContent);
            response.getWriter().flush();
            
            log.info("配置导出成功: {}", fileName);
            
        } catch (Exception e) {
            log.error("配置导出失败: {}", e.getMessage(), e);
            try {
                response.reset();
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":500,\"message\":\"导出失败：" + e.getMessage() + "\"}");
            } catch (IOException ex) {
                log.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 10. 恢复默认配置接口
     * 用途：将所有配置恢复到系统默认值（仅超级管理员：会清空全部存量配置）
     */
    @PostMapping("/reset-default")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @OperationLog(type = "config_reset_default", description = "管理员恢复默认配置")
    public Result<String> resetDefaultConfig() {
        log.info("接收恢复默认配置请求");
        try {
            // 删除现有配置
            systemConfigService.deleteAllConfigs();
            
            // 插入默认配置
            insertDefaultConfigs();
            
            log.info("恢复默认配置成功");
            return Result.success("恢复默认配置成功");
        } catch (Exception e) {
            log.error("恢复默认配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "恢复默认配置失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 11. 刷新配置接口
     * 用途：强制刷新系统配置缓存（仅超级管理员：影响全局运行时）
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<Map<String, Object>> refreshConfig() {
        log.info("接收刷新配置请求");
        try {
            long startTime = System.currentTimeMillis();
            
            // 触发配置刷新逻辑
            systemConfigService.refreshConfigCache();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // 返回刷新详情
            Map<String, Object> refreshDetails = new HashMap<>();
            refreshDetails.put("success", true);
            refreshDetails.put("durationMs", duration);
            refreshDetails.put("timestamp", LocalDateTime.now().toString());
            refreshDetails.put("message", "配置刷新成功");
            
            log.info("配置刷新成功，耗时: {}ms", duration);
            return Result.success("配置刷新成功", refreshDetails);
        } catch (Exception e) {
            log.error("配置刷新失败: {}", e.getMessage(), e);
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("success", false);
            errorDetails.put("error", e.getMessage());
            errorDetails.put("timestamp", LocalDateTime.now().toString());
            return Result.error(ResultCode.SYSTEM_ERROR, "配置刷新失败: " + "系统异常，请稍后重试", errorDetails);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取基础配置
     */
    private Map<String, Object> getBasicConfig() {
        SystemConfig config = systemConfigService.getConfigByKey("system_basic");
        if (config != null) {
            return JSON.parseObject(config.getConfigValue(), Map.class);
        }
        
        // 返回默认配置
        // （maintenanceMode/maintenanceNotice 已废弃：维护模式唯一真值源是 system_param 表，
        //   走 /api/system/status 与 PUT /api/admin/system/maintenance）
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("systemName", "论文查重管理系统");
        defaultConfig.put("version", "v2.1.0");
        defaultConfig.put("defaultLanguage", "zh-CN");
        defaultConfig.put("timezone", "Asia/Shanghai");
        return defaultConfig;
    }

    /**
     * 获取查重配置
     */
    private Map<String, Object> getPlagiarismConfig() {
        SystemConfig config = systemConfigService.getConfigByKey("plagiarism_config");
        if (config != null) {
            Map<String, Object> stored = JSON.parseObject(config.getConfigValue(), Map.class);
            // 「校内查重阈值」不再是合格线（真值源是 similarity_threshold /
            // category_similarity_threshold 两张表），历史配置行里的这个键在返回前摘掉。
            // thirdPartyThreshold 保留：它是第三方引擎的预警线，与本系统的合格判定无关。
            stored.remove("internalThreshold");
            return stored;
        }

        // 返回默认配置
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("thirdPartyThreshold", 20);
        defaultConfig.put("algorithm", "combined");
        defaultConfig.put("minMatchLength", 15);
        defaultConfig.put("cacheHours", 48);
        return defaultConfig;
    }

    /**
     * 获取安全配置
     */
    private Map<String, Object> getSecurityConfig() {
        SystemConfig config = systemConfigService.getConfigByKey("security_config");
        if (config != null) {
            return JSON.parseObject(config.getConfigValue(), Map.class);
        }
        
        // 返回默认配置
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("passwordMinLength", 8);
        defaultConfig.put("passwordRequirements", List.of("uppercase", "lowercase", "numbers"));
        defaultConfig.put("loginLockEnabled", true);
        defaultConfig.put("maxFailedAttempts", 5);
        defaultConfig.put("lockDuration", 30);
        defaultConfig.put("sessionTimeout", 60);
        defaultConfig.put("ipRestriction", false);
        return defaultConfig;
    }

    /**
     * 获取邮件配置
     */
    private Map<String, Object> getEmailConfig() {
        SystemConfig config = systemConfigService.getConfigByKey("email_config");
        if (config != null) {
            return JSON.parseObject(config.getConfigValue(), Map.class);
        }
        
        // 返回默认配置
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("smtpServer", "smtp.example.com");
        defaultConfig.put("smtpPort", 587);
        defaultConfig.put("encryption", "tls");
        defaultConfig.put("senderEmail", "noreply@example.com");
        defaultConfig.put("senderName", "论文查重系统");
        return defaultConfig;
    }

    /**
     * 获取性能配置
     */
    private Map<String, Object> getPerformanceConfig() {
        SystemConfig config = systemConfigService.getConfigByKey("performance");
        if (config != null) {
            return JSON.parseObject(config.getConfigValue(), Map.class);
        }
        
        // 返回默认配置
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("maxConcurrent", 20);
        defaultConfig.put("queueSize", 100);
        defaultConfig.put("cacheStrategy", "lru");
        defaultConfig.put("cacheSize", 1024);
        defaultConfig.put("autoCleanup", true);
        defaultConfig.put("cleanupInterval", 24);
        return defaultConfig;
    }

    /**
     * 保存配置
     */
    private void saveConfig(String configKey, Map<String, Object> config, String description) {
        systemConfigService.saveConfig(configKey, JSON.toJSONString(config), description);
    }

    /**
     * 插入默认配置
     */
    private void insertDefaultConfigs() {
        // 基础配置
        Map<String, Object> basicConfig = getBasicConfig();
        saveConfig("system_basic", basicConfig, "系统基础配置");
        
        // 查重配置
        Map<String, Object> plagiarismConfig = getPlagiarismConfig();
        saveConfig("plagiarism_config", plagiarismConfig, "查重配置");
        
        // 安全配置
        Map<String, Object> securityConfig = getSecurityConfig();
        saveConfig("security_config", securityConfig, "安全配置");
        
        // 邮件配置
        Map<String, Object> emailConfig = getEmailConfig();
        saveConfig("email_config", emailConfig, "邮件配置");
        
        // 性能配置
        Map<String, Object> performanceConfig = getPerformanceConfig();
        saveConfig("performance", performanceConfig, "性能配置");
    }
}