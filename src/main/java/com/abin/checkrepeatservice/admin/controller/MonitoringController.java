package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.monitor.service.BusinessMetricsService;
import com.abin.checkrepeatservice.monitor.service.ResourceMetricsService;
import com.abin.checkrepeatservice.monitor.service.ResponseTimeStore;
import com.abin.checkrepeatservice.monitor.service.SystemMonitorService;
import com.abin.checkrepeatservice.monitor.service.DatabaseMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.*;

/**
 * 系统监控控制器
 * 提供CPU、内存、磁盘、数据库等真实性能监控数据
 */
@RestController
@RequestMapping("/api/admin/monitoring")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
@Tag(name = "系统监控", description = "系统性能监控接口")
public class MonitoringController {

    @Resource
    private SystemMonitorService systemMonitorService;

    @Resource
    private DatabaseMonitorService databaseMonitorService;

    @Resource
    private BusinessMetricsService businessMetricsService;

    @Resource
    private ResponseTimeStore responseTimeStore;

    @Resource
    private ResourceMetricsService resourceMetricsService;

    /**
     * 获取系统性能监控数据（历史趋势）
     *
     * 数据来自 system_metric_minute 分钟采样表（ResourceMetricsService 每分钟落一行），
     * 重启后历史保留。无样本的时间桶补 null，前端显示为断点——宁缺毋假。
     *
     * @param metric cpu/memory/disk/database/all，控制返回哪些序列
     * @param period 1h（12点/5分钟均值）、24h（24点/小时均值）、7d（7点/日均值）
     */
    @GetMapping("/performance")
    @Operation(summary = "系统性能监控", description = "获取CPU、内存、磁盘、数据库等历史性能趋势（分钟采样落库）")
    public Result<Map<String, Object>> getPerformanceData(
            @RequestParam(defaultValue = "all") String metric,
            @RequestParam(defaultValue = "1h") String period) {

        log.info("接收获取系统性能监控数据请求: metric={}, period={}", metric, period);

        try {
            Map<String, Object> trend = resourceMetricsService.getPerformanceTrend(period);

            // 按 metric 过滤序列，timestamps 始终保留
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("timestamps", trend.get("timestamps"));
            switch (metric.toLowerCase()) {
                case "cpu" -> data.put("cpuUsage", trend.get("cpuUsage"));
                case "memory" -> data.put("memoryUsage", trend.get("memoryUsage"));
                case "disk" -> data.put("diskUsage", trend.get("diskUsage"));
                case "database" -> data.put("dbConnections", trend.get("dbConnections"));
                default -> {
                    data.put("cpuUsage", trend.get("cpuUsage"));
                    data.put("memoryUsage", trend.get("memoryUsage"));
                    data.put("diskUsage", trend.get("diskUsage"));
                    data.put("dbConnections", trend.get("dbConnections"));
                }
            }

            log.info("系统性能监控数据获取成功");
            return Result.success("系统性能监控数据获取成功", data);

        } catch (Exception e) {
            log.error("获取系统性能监控数据失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取系统性能监控数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前资源使用率（瞬时快照）
     *
     * 口径说明：cpuUsage / memoryUsage 是【操作系统整机】口径（与页面「系统资源」语义一致）；
     * jvmCpuUsage / jvmHeapUsage 额外返回 JVM 进程口径。diskUsage 为 jar 所在分区。
     * 某项采集不到时该字段缺省，不返回估算值。
     */
    @GetMapping("/resources")
    @Operation(summary = "当前资源使用率", description = "获取当前实时的系统资源使用率（整机口径）")
    public Result<Map<String, Object>> getResourceUsage() {
        log.info("接收获取当前资源使用率请求");

        try {
            Map<String, Object> resources = new HashMap<>();

            var systemStatus = systemMonitorService.getSystemStatus();
            if (systemStatus.isSuccess()) {
                Map<String, Object> statusData = (Map<String, Object>) systemStatus.getData();

                // CPU：整机口径 + JVM 进程口径
                Map<String, Object> cpuInfo = (Map<String, Object>) statusData.get("cpu");
                if (cpuInfo != null) {
                    putIfNonNegative(resources, "cpuUsage", (Double) cpuInfo.get("systemCpuUsage"));
                    putIfNonNegative(resources, "jvmCpuUsage", (Double) cpuInfo.get("processCpuUsage"));
                }

                // 内存：整机物理内存口径 + JVM 堆口径
                Map<String, Object> memoryInfo = (Map<String, Object>) statusData.get("memory");
                if (memoryInfo != null) {
                    putIfNonNegative(resources, "memoryUsage", (Double) memoryInfo.get("systemMemoryUsagePercent"));
                    putIfNonNegative(resources, "jvmHeapUsage", (Double) memoryInfo.get("heapUsagePercent"));
                }

                // 磁盘使用率
                Map<String, Object> diskInfo = (Map<String, Object>) statusData.get("disk");
                if (diskInfo != null) {
                    putIfNonNegative(resources, "diskUsage", (Double) diskInfo.get("usagePercent"));
                }
            }

            // 数据库连接池使用率
            var dbStatus = databaseMonitorService.getDatabaseStatus();
            if (dbStatus.isSuccess()) {
                Map<String, Object> dbData = (Map<String, Object>) dbStatus.getData();
                Double usageRate = (Double) dbData.get("poolUsageRate");
                if (usageRate != null) {
                    resources.put("dbPoolUsage", usageRate);
                }
            }

            log.info("当前资源使用率获取成功");
            return Result.success("当前资源使用率获取成功", resources);

        } catch (Exception e) {
            log.error("获取当前资源使用率失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取当前资源使用率失败: " + e.getMessage());
        }
    }

    /**
     * 获取业务指标：业务事件计数 + 查重任务队列深度
     *
     * 响应形状与前端 Dashboard 的约定一致：
     *   data.counters      全量累计计数
     *   data.todayCounters 今日计数
     *   data.gauges        瞬时值，目前只有 check.task.queue.depth
     * 计数键形如 business.event.count[event=user_login,result=success]
     */
    @GetMapping("/metrics")
    @Operation(summary = "业务指标监控", description = "获取全量累计与今日的业务事件计数、查重任务队列深度")
    public Result<Map<String, Object>> getBusinessMetrics() {

        log.info("接收获取业务指标请求");

        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("counters", businessMetricsService.getAllTimeCounters());
            data.put("todayCounters", businessMetricsService.getTodayCounters());

            Map<String, Object> gauges = new LinkedHashMap<>();
            gauges.put(BusinessMetricsService.QUEUE_DEPTH_KEY, businessMetricsService.getQueueDepth());
            data.put("gauges", gauges);

            log.info("业务指标获取成功");
            return Result.success("业务指标获取成功", data);

        } catch (Exception e) {
            log.error("获取业务指标失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取业务指标失败: " + e.getMessage());
        }
    }

    /**
     * 获取响应时间趋势（按分钟聚合）
     *
     * 数据来自内存中的逐请求耗时采集（ResponseTimeStore），进程重启即清零。
     *
     * minutes 超出采集窗口时【裁剪】到窗口上限而非报错：
     * 否则一旦有人调小 monitoring.response-time.window-minutes，
     * 前端 SystemMonitoring 的 60 分钟选项会直接 500。
     */
    @GetMapping("/response-time-trend")
    @Operation(summary = "响应时间趋势", description = "获取最近N分钟按分钟聚合的平均/最小/最大/P95/P99响应时间与请求数")
    public Result<Map<String, Object>> getResponseTimeTrend(
            @RequestParam(value = "minutes", defaultValue = "15") Integer minutes) {

        log.info("接收获取响应时间趋势请求: minutes={}", minutes);

        try {
            if (minutes == null || minutes < 1) {
                return Result.error(ResultCode.PARAM_VALUE_INVALID, "minutes 必须为正整数");
            }

            int window = responseTimeStore.getWindowMinutes();
            int effective = Math.min(minutes, window);
            if (effective != minutes) {
                log.warn("minutes={} 超出采集窗口 {} 分钟，已裁剪为 {}", minutes, window, effective);
            }

            return Result.success("响应时间趋势获取成功", responseTimeStore.snapshot(effective));

        } catch (Exception e) {
            log.error("获取响应时间趋势失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取响应时间趋势失败: " + e.getMessage());
        }
    }

    /**
     * 获取API响应时间统计
     *
     * 语义收缩说明：原先 24h/7d 分支返回的是 Random 造的假数据，现已统一委托
     * ResponseTimeStore 的真实采集数据，因此受采集窗口限制（默认 60 分钟），
     * 三个 period 实际都会返回最近 window 分钟的数据。保留 period 入参仅为兼容既有调用方。
     */
    @GetMapping("/response-times")
    @Operation(summary = "API响应时间统计", description = "获取API平均响应时间、最小/最大响应时间、95百分位响应时间等详细指标")
    public Result<Map<String, Object>> getApiResponseTimes(
            @RequestParam(defaultValue = "1h") String period) {

        log.info("接收获取API响应时间统计请求: period={}（实际受采集窗口限制）", period);

        try {
            Map<String, Object> responseTimes = responseTimeStore.snapshot(responseTimeStore.getWindowMinutes());

            log.info("API响应时间统计获取成功");
            return Result.success("API响应时间统计获取成功", responseTimes);

        } catch (Exception e) {
            log.error("获取API响应时间统计失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取API响应时间统计失败: " + e.getMessage());
        }
    }

    /**
     * 采集语义里 -1 / null 表示"不可用"（JMX 约定），此时字段缺省，不下发假值
     */
    private void putIfNonNegative(Map<String, Object> target, String key, Double value) {
        if (value != null && value >= 0) {
            target.put(key, value);
        }
    }
}
