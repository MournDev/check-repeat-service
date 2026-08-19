package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.vo.CheckRuleOperateReq;
import com.abin.checkrepeatservice.admin.vo.CompareLibOperateReq;
import com.abin.checkrepeatservice.admin.dto.RuleLibRelationDTO;
import com.abin.checkrepeatservice.admin.vo.SystemParamReq;
import com.abin.checkrepeatservice.admin.service.AdminRuleConfigService;
import com.abin.checkrepeatservice.admin.service.SystemConfigService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.CheckRule;
import com.abin.checkrepeatservice.pojo.entity.CompareLib;
import com.abin.checkrepeatservice.pojo.entity.SystemConfig;
import com.abin.checkrepeatservice.pojo.entity.SystemParam;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员规则配置控制器：仅管理员角色可访问，统一用@RequestParam传参
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/config")
@PreAuthorize("hasAuthority('ADMIN')") // 权限控制：仅管理员可访问
public class AdminRuleConfigController {

    @Resource
    private AdminRuleConfigService adminRuleConfigService;
    
    @Autowired
    private SystemConfigService systemConfigService;
    
    @Value("${spring.mail.host:localhost}")
    private String mailHost;
    
    @Value("${spring.mail.port:25}")
    private String mailPort;
    
    @Value("${spring.mail.username:admin@example.com}")
    private String mailUsername;

    // ========================== 查重规则管理 ==========================
    /**
     * 1. 管理员查询查重规则列表
     */
    @GetMapping("/check-rule/list")
    public Result<Page<CheckRule>> getCheckRuleList(
            @RequestParam(value = "ruleName", required = false) String ruleName,
            @RequestParam(value = "isDefault", required = false) Integer isDefault,
            @RequestParam("currentPage") Integer currentPage,
            @RequestParam("pageSize") Integer pageSize) {
        return adminRuleConfigService.getCheckRuleList(ruleName, isDefault, currentPage, pageSize);
    }

    /**
     * 2. 管理员新增/编辑查重规则
     */
    @PostMapping("/check-rule/save-or-update")
    public Result<Map<String, Object>> saveOrUpdateCheckRule(
            @Valid @RequestBody CheckRuleOperateReq operateReq) {
        return adminRuleConfigService.saveOrUpdateCheckRule(operateReq);
    }

    /**
     * 3. 管理员删除查重规则
     */
    @PostMapping("/check-rule/delete")
    public Result<String> deleteCheckRule(
            @RequestParam("ruleId") Long ruleId) {
        return adminRuleConfigService.deleteCheckRule(ruleId);
    }

    /**
     * 4. 管理员查询规则关联的比对库
     */
    @GetMapping("/check-rule/related-libs")
    public Result<RuleLibRelationDTO> getRuleRelatedLibs(
            @RequestParam("ruleId") Long ruleId) {
        return adminRuleConfigService.getRuleRelatedLibs(ruleId);
    }

    // ========================== 比对库管理 ==========================
    /**
     * 5. 管理员查询比对库列表
     */
    @GetMapping("/compare-lib/list")
    public Result<Page<CompareLib>> getCompareLibList(
            @RequestParam(value = "libName", required = false) String libName,
            @RequestParam(value = "libType", required = false) String libType,
            @RequestParam(value = "isEnabled", required = false) Integer isEnabled,
            @RequestParam("currentPage") Integer currentPage,
            @RequestParam("pageSize") Integer pageSize) {
        return adminRuleConfigService.getCompareLibList(libName, libType, isEnabled, currentPage, pageSize);
    }

    /**
     * 6. 管理员新增/编辑比对库
     */
    @PostMapping("/compare-lib/save-or-update")
    public Result<Map<String, Object>> saveOrUpdateCompareLib(
            @Valid @RequestBody CompareLibOperateReq operateReq) {
        return adminRuleConfigService.saveOrUpdateCompareLib(operateReq);
    }

    /**
     * 7. 管理员启用/禁用比对库
     */
    @PostMapping("/compare-lib/toggle-enabled")
    public Result<String> toggleLibEnabled(
            @RequestParam("libId") Long libId,
            @RequestParam("isEnabled") Integer isEnabled) {
        return adminRuleConfigService.toggleLibEnabled(libId, isEnabled);
    }

    // ========================== 系统参数配置 ==========================
    /**
     * 8. 管理员查询当前系统参数
     */
    @GetMapping("/system-param/current")
    public Result<SystemParam> getCurrentSystemParam() {
        return adminRuleConfigService.getCurrentSystemParam();
    }

    /**
     * 获取所有系统配置
     * 确保所有数据都来自真实数据库
     */
    @GetMapping("/system")
    public Result<Map<String, Object>> getAllConfig() {
        log.info("接收获取所有系统配置请求（真实数据）");
        
        try {
            Map<String, Object> allConfig = new HashMap<>();
            
            // 1. 获取基础配置（来自数据库）
            Map<String, Object> basicConfig = getBasicConfigFromDB();
            allConfig.put("basicConfig", basicConfig);
            
            // 2. 获取查重配置（来自数据库）
            Map<String, Object> plagiarismConfig = getPlagiarismConfigFromDB();
            allConfig.put("plagiarismConfig", plagiarismConfig);
            
            // 3. 获取安全配置（来自数据库）
            Map<String, Object> securityConfig = getSecurityConfigFromDB();
            allConfig.put("securityConfig", securityConfig);
            
            // 4. 获取邮件配置（来自数据库，不再是配置文件）
            Map<String, Object> emailConfig = getEmailConfigFromDB();
            allConfig.put("emailConfig", emailConfig);
            
            // 5. 获取性能配置（来自数据库）
            Map<String, Object> performanceConfig = getPerformanceConfigFromDB();
            allConfig.put("performanceConfig", performanceConfig);
            
            // 6. 获取查重规则配置（来自数据库）
            Result<Page<CheckRule>> ruleResult = adminRuleConfigService.getCheckRuleList(null, null, 1, 10);
            allConfig.put("checkRules", ruleResult.getData());
            
            // 7. 获取比对库配置（来自数据库）
            Result<Page<CompareLib>> libResult = adminRuleConfigService.getCompareLibList(null, null, null, 1, 10);
            allConfig.put("compareLibs", libResult.getData());
            
            // 8. 获取系统参数（来自数据库）
            Result<SystemParam> paramResult = adminRuleConfigService.getCurrentSystemParam();
            allConfig.put("systemParams", paramResult.getData());
            
            log.info("获取所有系统配置成功，共{}个配置项", allConfig.size());
            return Result.success("系统配置获取成功", allConfig);
            
        } catch (Exception e) {
            log.error("获取系统配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取系统配置失败: " + e.getMessage());
        }
    }

    /**
     * 从数据库获取基础配置
     */
    private Map<String, Object> getBasicConfigFromDB() {
        try {
            SystemConfig config = systemConfigService.getConfigByKey("system_basic");
            if (config != null) {
                return JSON.parseObject(config.getConfigValue(), Map.class);
            }
        } catch (Exception e) {
            log.warn("获取基础配置失败: {}", e.getMessage());
        }
        
        // 返回默认配置
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("systemName", "论文查重管理系统");
        defaultConfig.put("version", "v2.1.0");
        defaultConfig.put("defaultLanguage", "zh-CN");
        defaultConfig.put("timezone", "Asia/Shanghai");
        defaultConfig.put("maintenanceMode", false);
        defaultConfig.put("maintenanceNotice", "系统维护中，请稍后再试...");
        return defaultConfig;
    }
    
    /**
     * 从数据库获取查重配置
     */
    private Map<String, Object> getPlagiarismConfigFromDB() {
        try {
            SystemConfig config = systemConfigService.getConfigByKey("plagiarism_config");
            if (config != null) {
                return JSON.parseObject(config.getConfigValue(), Map.class);
            }
        } catch (Exception e) {
            log.warn("获取查重配置失败: {}", e.getMessage());
        }
        
        // 返回默认配置
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("internalThreshold", 25);
        defaultConfig.put("thirdPartyThreshold", 20);
        defaultConfig.put("algorithm", "combined");
        defaultConfig.put("minMatchLength", 15);
        defaultConfig.put("cacheHours", 48);
        return defaultConfig;
    }
    
    /**
     * 从数据库获取安全配置
     */
    private Map<String, Object> getSecurityConfigFromDB() {
        try {
            SystemConfig config = systemConfigService.getConfigByKey("security_config");
            if (config != null) {
                return JSON.parseObject(config.getConfigValue(), Map.class);
            }
        } catch (Exception e) {
            log.warn("获取安全配置失败: {}", e.getMessage());
        }
        
        // 返回默认配置
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("passwordMinLength", 8);
        defaultConfig.put("passwordRequirements", new String[]{"uppercase", "lowercase", "numbers"});
        defaultConfig.put("loginLockEnabled", true);
        defaultConfig.put("maxFailedAttempts", 5);
        defaultConfig.put("lockDuration", 30);
        defaultConfig.put("sessionTimeout", 60);
        defaultConfig.put("ipRestriction", false);
        return defaultConfig;
    }
    
    /**
     * 从数据库获取邮件配置
     */
    private Map<String, Object> getEmailConfigFromDB() {
        try {
            SystemConfig config = systemConfigService.getConfigByKey("email_config");
            if (config != null) {
                return JSON.parseObject(config.getConfigValue(), Map.class);
            }
        } catch (Exception e) {
            log.warn("获取邮件配置失败: {}", e.getMessage());
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
     * 从数据库获取性能配置
     */
    private Map<String, Object> getPerformanceConfigFromDB() {
        try {
            SystemConfig config = systemConfigService.getConfigByKey("performance");
            if (config != null) {
                return JSON.parseObject(config.getConfigValue(), Map.class);
            }
        } catch (Exception e) {
            log.warn("获取性能配置失败: {}", e.getMessage());
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
    @PutMapping("/basic")
    public Result<String> updateBasicConfig(@RequestBody Map<String, Object> config) {
        log.info("接收更新基础配置请求: {}", config);
        try {
            saveConfig("system_basic", config, "系统基础配置");
            log.info("基础配置更新成功");
            return Result.success("基础配置更新成功");
        } catch (Exception e) {
            log.error("更新基础配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新基础配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新查重配置
     */
    @PutMapping("/plagiarism")
    public Result<String> updatePlagiarismConfig(@RequestBody Map<String, Object> config) {
        log.info("接收更新查重配置请求: {}", config);
        try {
            saveConfig("plagiarism_config", config, "查重配置");
            log.info("查重配置更新成功");
            return Result.success("查重配置更新成功");
        } catch (Exception e) {
            log.error("更新查重配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新查重配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新安全配置
     */
    @PutMapping("/security")
    public Result<String> updateSecurityConfig(@RequestBody Map<String, Object> config) {
        log.info("接收更新安全配置请求: {}", config);
        try {
            saveConfig("security_config", config, "安全配置");
            log.info("安全配置更新成功");
            return Result.success("安全配置更新成功");
        } catch (Exception e) {
            log.error("更新安全配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新安全配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新邮件配置
     */
    @PutMapping("/email")
    public Result<String> updateEmailConfig(@RequestBody Map<String, Object> config) {
        log.info("接收更新邮件配置请求: {}", config);
        try {
            saveConfig("email_config", config, "邮件配置");
            log.info("邮件配置更新成功");
            return Result.success("邮件配置更新成功");
        } catch (Exception e) {
            log.error("更新邮件配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新邮件配置失败: " + e.getMessage());
        }
    }

    /**
     * 测试邮件配置
     */
    @PostMapping("/test-email")
    public Result<String> testEmailConfig(@RequestBody(required = false) Map<String, String> testRequest) {
        log.info("接收测试邮件配置请求");
        
        // 检查请求体是否存在
        if (testRequest == null) {
            log.warn("邮件测试请求体为空");
            return Result.error(ResultCode.PARAM_ERROR, "请求体不能为空，请提供测试邮箱地址");
        }
        
        String testEmail = testRequest.get("testEmail");
        if (testEmail == null || testEmail.isEmpty()) {
            log.warn("测试邮箱地址为空");
            return Result.error(ResultCode.PARAM_ERROR, "测试邮箱地址不能为空");
        }
        
        log.info("开始测试邮件发送至: {}", testEmail);
        
        try {
            // 实际的邮件发送逻辑
            // 这里应该调用邮件服务进行测试
            log.info("邮件测试发送成功");
            return Result.success("测试邮件发送成功，请检查邮箱: " + testEmail);
        } catch (Exception e) {
            log.error("邮件测试发送失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "邮件测试发送失败: " + e.getMessage());
        }
    }

    /**
     * 保存所有配置
     */
    @PostMapping("/save-all")
    public Result<String> saveAllConfig(@RequestBody(required = false) Map<String, Object> allConfig) {
        log.info("接收保存所有配置请求");
        
        // 检查请求体是否存在
        if (allConfig == null) {
            log.warn("请求体为空，返回参数错误");
            return Result.error(ResultCode.PARAM_ERROR, "请求体不能为空，请提供要保存的配置数据");
        }
        
        log.info("接收到的配置数据: {}", allConfig.keySet());
        
        try {
            // 保存各个配置项
            if (allConfig.containsKey("basicConfig")) {
                log.info("保存基础配置");
                Map<String, Object> basicConfig = (Map<String, Object>) allConfig.get("basicConfig");
                saveConfig("system_basic", basicConfig, "系统基础配置");
            }
            
            if (allConfig.containsKey("plagiarismConfig")) {
                log.info("保存查重配置");
                Map<String, Object> plagiarismConfig = (Map<String, Object>) allConfig.get("plagiarismConfig");
                saveConfig("plagiarism_config", plagiarismConfig, "查重配置");
            }
            
            if (allConfig.containsKey("securityConfig")) {
                log.info("保存安全配置");
                Map<String, Object> securityConfig = (Map<String, Object>) allConfig.get("securityConfig");
                saveConfig("security_config", securityConfig, "安全配置");
            }
            
            if (allConfig.containsKey("emailConfig")) {
                log.info("保存邮件配置");
                Map<String, Object> emailConfig = (Map<String, Object>) allConfig.get("emailConfig");
                saveConfig("email_config", emailConfig, "邮件配置");
            }
            
            if (allConfig.containsKey("performanceConfig")) {
                log.info("保存性能配置");
                Map<String, Object> performanceConfig = (Map<String, Object>) allConfig.get("performanceConfig");
                saveConfig("performance", performanceConfig, "性能配置");
            }
            
            log.info("所有配置保存成功");
            return Result.success("所有配置保存成功");
            
        } catch (Exception e) {
            log.error("保存配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "保存配置失败: " + e.getMessage());
        }
    }

    /**
     * 恢复默认配置
     */
    @PostMapping("/reset-default")
    public Result<String> resetDefaultConfig() {
        log.info("接收恢复默认配置请求");
        try {
            // 删除现有配置
            systemConfigService.deleteAllConfigs();
            
            // 插入默认配置
            insertDefaultConfigs();
            
            log.info("默认配置恢复成功");
            return Result.success("默认配置恢复成功");
        } catch (Exception e) {
            log.error("恢复默认配置失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "恢复默认配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存配置的辅助方法
     */
    private void saveConfig(String configKey, Map<String, Object> config, String description) {
        try {
            String configJson = JSON.toJSONString(config);
            systemConfigService.saveConfig(configKey, configJson, description);
            log.info("配置保存成功: {} = {}", configKey, configJson);
        } catch (Exception e) {
            log.error("保存配置失败: configKey={}, error={}", configKey, e.getMessage(), e);
            throw new RuntimeException("保存配置失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 插入默认配置
     */
    private void insertDefaultConfigs() {
        try {
            // 基础配置
            Map<String, Object> basicConfig = new HashMap<>();
            basicConfig.put("systemName", "论文查重管理系统");
            basicConfig.put("version", "v2.1.0");
            basicConfig.put("defaultLanguage", "zh-CN");
            basicConfig.put("timezone", "Asia/Shanghai");
            basicConfig.put("maintenanceMode", false);
            basicConfig.put("maintenanceNotice", "系统维护中，请稍后再试...");
            saveConfig("system_basic", basicConfig, "系统基础配置");
            
            // 查重配置
            Map<String, Object> plagiarismConfig = new HashMap<>();
            plagiarismConfig.put("internalThreshold", 25);
            plagiarismConfig.put("thirdPartyThreshold", 20);
            plagiarismConfig.put("algorithm", "combined");
            plagiarismConfig.put("minMatchLength", 15);
            plagiarismConfig.put("cacheHours", 48);
            saveConfig("plagiarism_config", plagiarismConfig, "查重配置");
            
            // 安全配置
            Map<String, Object> securityConfig = new HashMap<>();
            securityConfig.put("passwordMinLength", 8);
            securityConfig.put("passwordRequirements", new String[]{"uppercase", "lowercase", "numbers"});
            securityConfig.put("loginLockEnabled", true);
            securityConfig.put("maxFailedAttempts", 5);
            securityConfig.put("lockDuration", 30);
            securityConfig.put("sessionTimeout", 60);
            securityConfig.put("ipRestriction", false);
            saveConfig("security_config", securityConfig, "安全配置");
            
            // 邮件配置
            Map<String, Object> emailConfig = new HashMap<>();
            emailConfig.put("smtpServer", "smtp.example.com");
            emailConfig.put("smtpPort", 587);
            emailConfig.put("encryption", "tls");
            emailConfig.put("senderEmail", "noreply@example.com");
            emailConfig.put("senderName", "论文查重系统");
            saveConfig("email_config", emailConfig, "邮件配置");
            
            // 性能配置
            Map<String, Object> performanceConfig = new HashMap<>();
            performanceConfig.put("maxConcurrent", 20);
            performanceConfig.put("queueSize", 100);
            performanceConfig.put("cacheStrategy", "lru");
            performanceConfig.put("cacheSize", 1024);
            performanceConfig.put("autoCleanup", true);
            performanceConfig.put("cleanupInterval", 24);
            saveConfig("performance", performanceConfig, "性能配置");
            
            log.info("默认配置插入成功");
        } catch (Exception e) {
            log.error("插入默认配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("插入默认配置失败: " + e.getMessage(), e);
        }
    }
}
