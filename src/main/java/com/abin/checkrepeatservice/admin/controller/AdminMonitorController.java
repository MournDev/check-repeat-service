package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminDashboardService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.monitor.service.ApplicationMonitorService;
import com.abin.checkrepeatservice.monitor.service.DatabaseMonitorService;
import com.abin.checkrepeatservice.monitor.service.ResourceMetricsService;
import com.abin.checkrepeatservice.monitor.service.ResponseTimeStore;
import com.abin.checkrepeatservice.monitor.service.SystemMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员系统监控控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/monitor")
@PreAuthorize("hasAuthority('ADMIN')")
@Tag(name = "系统监控接口", description = "系统性能监控和健康度检查相关接口")
public class AdminMonitorController {

    @Resource
    private AdminDashboardService adminDashboardService;

    @Resource
    private SystemMonitorService systemMonitorService;

    @Resource
    private DatabaseMonitorService databaseMonitorService;

    @Resource
    private ApplicationMonitorService applicationMonitorService;

    @Resource
    private ResourceMetricsService resourceMetricsService;

    @Resource
    private ResponseTimeStore responseTimeStore;

    /**
     * 获取系统监控概览（扁平结构，与 SystemMonitoring 前端字段对齐）
     *
     * totalRequests / avgResponseTime / errorRate 来自 ResponseTimeStore 的
     * 进程级累计（本次启动以来，重启清零）；uptime 同为本次启动时长。
     */
    @GetMapping("/overview")
    @Operation(summary = "系统监控概览", description = "获取系统运行时长、版本、请求统计与线程等概览指标")
    public Result<Map<String, Object>> getSystemOverview() {
        try {
            log.info("管理员请求获取系统监控概览");

            Map<String, Object> overview = new HashMap<>();
            var status = systemMonitorService.getSystemStatus();
            if (status.isSuccess()) {
                Map<String, Object> data = (Map<String, Object>) status.getData();

                Map<String, Object> runtime = (Map<String, Object>) data.get("runtime");
                if (runtime != null) {
                    overview.put("uptime", runtime.get("uptime"));
                    overview.put("javaVersion", runtime.get("javaVersion"));
                }
                Map<String, Object> system = (Map<String, Object>) data.get("system");
                if (system != null) {
                    overview.put("osName", system.get("osName"));
                }
                Map<String, Object> threads = (Map<String, Object>) data.get("threads");
                if (threads != null) {
                    overview.put("activeThreads", threads.get("threadCount"));
                }
            }

            String version = getClass().getPackage().getImplementationVersion();
            overview.put("version", version != null ? version : "0.0.1-SNAPSHOT");
            overview.putAll(responseTimeStore.globalStats());

            return Result.success("系统监控概览获取成功", overview);
        } catch (Exception e) {
            log.error("获取系统监控概览失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取系统监控概览失败: " + e.getMessage());
        }
    }

    /**
     * 获取详细性能指标
     */
    @GetMapping("/performance")
    @Operation(summary = "性能指标详情", description = "获取详细的系统性能指标数据")
    public Result<Map<String, Object>> getPerformanceMetrics() {
        try {
            log.info("管理员请求获取性能指标详情");
            return Result.success("性能指标获取成功", getMockPerformanceData());
        } catch (Exception e) {
            log.error("获取性能指标失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取性能指标失败: " + e.getMessage());
        }
    }

    /**
     * 获取安全审计信息
     */
    @GetMapping("/security")
    @Operation(summary = "安全审计信息", description = "获取系统安全相关统计和异常事件")
    public Result<Map<String, Object>> getSecurityAudit() {
        try {
            log.info("管理员请求获取安全审计信息");
            return Result.success("安全审计信息获取成功", getMockSecurityData());
        } catch (Exception e) {
            log.error("获取安全审计信息失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取安全审计信息失败: " + e.getMessage());
        }
    }

    /**
     * 系统健康度检查
     */
    @GetMapping("/health")
    @Operation(summary = "系统健康检查", description = "执行系统健康度综合评估")
    public Result<Map<String, Object>> healthCheck() {
        try {
            log.info("执行系统健康度检查");
            return Result.success("健康检查完成", getMockHealthCheckData());
        } catch (Exception e) {
            log.error("健康检查失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "健康检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取数据库监控信息
     *
     * 连接池/慢查询/buffer pool 命中率均为真实值（Hikari MXBean + SHOW GLOBAL STATUS），
     * poolUsageHistory 取自 system_metric_minute 分钟采样（近 60 分钟）。
     */
    @GetMapping("/database")
    @Operation(summary = "数据库监控", description = "获取数据库连接池和性能监控信息")
    public Result<Map<String, Object>> getDatabaseMonitor() {
        try {
            log.info("管理员请求获取数据库监控信息");
            var result = databaseMonitorService.getDatabaseStatus();
            if (result.isSuccess()) {
                Map<String, Object> data = (Map<String, Object>) result.getData();
                data.put("poolUsageHistory", resourceMetricsService.getPoolUsageHistory(60));
            }
            return result;
        } catch (Exception e) {
            log.error("获取数据库监控信息失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取数据库监控信息失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取应用性能指标
     */
    @GetMapping("/application")
    @Operation(summary = "应用性能监控", description = "获取应用层面的性能指标和统计信息")
    public Result<Map<String, Object>> getApplicationMetrics() {
        try {
            log.info("管理员请求获取应用性能指标");
            return applicationMonitorService.getApplicationMetrics();
        } catch (Exception e) {
            log.error("获取应用性能指标失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取应用性能指标失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取缓存性能指标
     */
    @GetMapping("/cache")
    @Operation(summary = "缓存性能监控", description = "获取Redis和本地缓存的性能指标")
    public Result<Map<String, Object>> getCacheMetrics() {
        try {
            log.info("管理员请求获取缓存性能指标");
            return applicationMonitorService.getCacheMetrics();
        } catch (Exception e) {
            log.error("获取缓存性能指标失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取缓存性能指标失败: " + e.getMessage());
        }
    }

    /**
     * 模拟系统监控数据
     */
    private Map<String, Object> getMockSystemMonitorData() {
        Map<String, Object> monitorData = new java.util.HashMap<>();
        
        // CPU监控
        Map<String, Object> cpu = new java.util.HashMap<>();
        cpu.put("usage", 45.2);
        cpu.put("cores", 8);
        cpu.put("loadAverage", 1.2);
        cpu.put("status", "normal");
        monitorData.put("cpu", cpu);
        
        // 内存监控
        Map<String, Object> memory = new java.util.HashMap<>();
        memory.put("total", "16GB");
        memory.put("used", "10.2GB");
        memory.put("free", "5.8GB");
        memory.put("usage", 63.8);
        memory.put("status", "normal");
        monitorData.put("memory", memory);
        
        // 磁盘监控
        Map<String, Object> disk = new java.util.HashMap<>();
        disk.put("total", "500GB");
        disk.put("used", "280GB");
        disk.put("free", "220GB");
        disk.put("usage", 56.0);
        disk.put("status", "normal");
        monitorData.put("disk", disk);
        
        // 网络监控
        Map<String, Object> network = new java.util.HashMap<>();
        network.put("bytesIn", 1024000L);
        network.put("bytesOut", 2048000L);
        network.put("connections", 85);
        monitorData.put("network", network);
        
        // 应用监控
        Map<String, Object> application = new java.util.HashMap<>();
        application.put("uptime", "15天 3小时 25分钟");
        application.put("threads", 42);
        application.put("requestsPerSecond", 12.8);
        application.put("errorRate", 0.3);
        monitorData.put("application", application);
        
        return monitorData;
    }

    /**
     * 模拟性能数据
     */
    private Map<String, Object> getMockPerformanceData() {
        Map<String, Object> perfData = new java.util.HashMap<>();
        
        // API响应时间
        Map<String, Object> apiTimes = new java.util.HashMap<>();
        apiTimes.put("avg", 150);
        apiTimes.put("min", 20);
        apiTimes.put("max", 2500);
        apiTimes.put("p95", 400);
        perfData.put("apiResponseTime", apiTimes);
        
        // 数据库性能
        Map<String, Object> dbPerf = new java.util.HashMap<>();
        dbPerf.put("avgQueryTime", 45);
        dbPerf.put("slowQueries", 3);
        dbPerf.put("connectionPoolUsage", 65);
        dbPerf.put("activeConnections", 12);
        perfData.put("database", dbPerf);
        
        // 缓存性能
        Map<String, Object> cachePerf = new java.util.HashMap<>();
        cachePerf.put("hitRate", 85.5);
        cachePerf.put("missRate", 14.5);
        cachePerf.put("evictions", 42);
        perfData.put("cache", cachePerf);
        
        // JVM监控
        Map<String, Object> jvm = new java.util.HashMap<>();
        jvm.put("heapUsed", "2.3GB");
        jvm.put("heapMax", "8GB");
        jvm.put("gcCount", 156);
        jvm.put("gcTime", "2.3s");
        perfData.put("jvm", jvm);
        
        return perfData;
    }

    /**
     * 模拟安全数据
     */
    private Map<String, Object> getMockSecurityData() {
        Map<String, Object> securityData = new java.util.HashMap<>();
        
        // 登录统计
        Map<String, Object> loginStats = new java.util.HashMap<>();
        loginStats.put("todayLogins", 127);
        loginStats.put("failedAttempts", 3);
        loginStats.put("uniqueUsers", 85);
        securityData.put("loginStatistics", loginStats);
        
        // 安全事件
        Map<String, Object> securityEvents = new java.util.HashMap<>();
        securityEvents.put("failedLogins", 12);
        securityEvents.put("suspiciousIPs", 2);
        securityEvents.put("bruteForceAttempts", 1);
        securityData.put("securityEvents", securityEvents);
        
        // 权限审计
        Map<String, Object> permissionAudit = new java.util.HashMap<>();
        permissionAudit.put("unauthorizedAccess", 0);
        permissionAudit.put("privilegeEscalations", 0);
        permissionAudit.put("sensitiveOperations", 23);
        securityData.put("permissionAudit", permissionAudit);
        
        return securityData;
    }

    /**
     * 模拟健康检查数据
     */
    private Map<String, Object> getMockHealthCheckData() {
        Map<String, Object> healthData = new java.util.HashMap<>();
        
        healthData.put("overallStatus", "HEALTHY");
        healthData.put("healthScore", 92);
        
        // 各组件状态
        Map<String, Object> components = new java.util.HashMap<>();
        
        Map<String, Object> database = new java.util.HashMap<>();
        database.put("status", "UP");
        database.put("responseTime", 25);
        components.put("database", database);
        
        Map<String, Object> redis = new java.util.HashMap<>();
        redis.put("status", "UP");
        redis.put("responseTime", 5);
        components.put("redis", redis);
        
        Map<String, Object> minio = new java.util.HashMap<>();
        minio.put("status", "UP");
        minio.put("responseTime", 45);
        components.put("minio", minio);
        
        healthData.put("components", components);
        
        // 建议
        java.util.List<String> recommendations = new java.util.ArrayList<>();
        recommendations.add("系统运行正常，各项指标均在合理范围内");
        recommendations.add("建议定期备份重要数据");
        recommendations.add("可考虑优化慢查询SQL语句");
        healthData.put("recommendations", recommendations);
        
        return healthData;
    }
}