package com.abin.checkrepeatservice.monitor.service;

import com.abin.checkrepeatservice.common.Result;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 应用性能监控服务
 *
 * JVM 指标全部来自 ManagementFactory 的 MXBean（真实瞬时值）：
 *   - 堆/非堆内存：MemoryMXBean
 *   - 线程数：ThreadMXBean
 *   - GC 次数：GarbageCollectorMXBean，按名称归类 young/full
 *     （G1 Young Generation、PS Scavenge、ParNew、Copy → young；
 *       G1 Old Generation、PS MarkSweep、ConcurrentMarkSweep → full）
 *   - 类加载数：ClassLoadingMXBean
 * HTTP 统计来自 ResponseTimeStore 的进程级累计（本次启动以来，重启清零）。
 */
@Slf4j
@Service
public class ApplicationMonitorService {

    @Resource
    private ResponseTimeStore responseTimeStore;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * 获取应用性能指标（扁平结构，与 SystemMonitoring 前端字段对齐）
     */
    public Result<Map<String, Object>> getApplicationMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

            // 堆内存
            MemoryUsage heap = memoryBean.getHeapMemoryUsage();
            metrics.put("heapUsed", formatBytes(heap.getUsed()));
            metrics.put("heapMax", formatBytes(heap.getMax()));
            metrics.put("heapPercent", percent(heap.getUsed(), heap.getMax()));

            // 非堆内存
            MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
            metrics.put("nonHeapUsed", formatBytes(nonHeap.getUsed()));
            metrics.put("nonHeapPercent", percent(nonHeap.getUsed(), nonHeap.getMax()));

            // 线程
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            metrics.put("threadCount", threadBean.getThreadCount());

            // GC 次数（按名称归类 young / full）
            long youngGcCount = 0;
            long fullGcCount = 0;
            for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
                long count = gcBean.getCollectionCount();
                if (count < 0) {
                    continue; // -1 表示该收集器不统计次数
                }
                if (isYoungCollector(gcBean.getName())) {
                    youngGcCount += count;
                } else {
                    fullGcCount += count;
                }
            }
            metrics.put("youngGcCount", youngGcCount);
            metrics.put("fullGcCount", fullGcCount);

            // 类加载
            ClassLoadingMXBean classBean = ManagementFactory.getClassLoadingMXBean();
            metrics.put("loadedClassCount", classBean.getLoadedClassCount());

            // HTTP 请求统计（进程启动以来的真实采集）
            metrics.put("http", responseTimeStore.globalStats());

            // 应用基本信息
            Map<String, Object> appInfo = new HashMap<>();
            appInfo.put("name", "check-repeat-service");
            String version = getClass().getPackage().getImplementationVersion();
            appInfo.put("version", version != null ? version : "0.0.1-SNAPSHOT");
            metrics.put("application", appInfo);

            return Result.success("应用性能指标获取成功", metrics);
        } catch (Exception e) {
            log.error("获取应用性能指标失败", e);
            return Result.error(com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR,
                              "获取应用性能指标失败: 系统异常，请稍后重试");
        }
    }

    private boolean isYoungCollector(String name) {
        return name.contains("Young") || name.contains("Scavenge")
                || name.equals("ParNew") || name.equals("Copy");
    }

    /**
     * 缓存性能指标：Redis 命中/未命中来自真实的 INFO stats
     * （keyspace_hits / keyspace_misses，Redis 自启动累计）。
     * 本地缓存没有统一的真实统计源，不再返回伪造数字。
     */
    public Result<Map<String, Object>> getCacheMetrics() {
        Map<String, Object> cacheMetrics = new HashMap<>();

        try {
            Map<String, Object> redisMetrics = new HashMap<>();
            if (redisConnectionFactory != null) {
                try (RedisConnection conn = redisConnectionFactory.getConnection()) {
                    Properties stats = conn.info("stats");
                    long hits = Long.parseLong(stats.getProperty("keyspace_hits", "0"));
                    long misses = Long.parseLong(stats.getProperty("keyspace_misses", "0"));
                    long total = hits + misses;

                    redisMetrics.put("hits", hits);
                    redisMetrics.put("misses", misses);
                    redisMetrics.put("hitRate", total > 0 ? Math.round(hits * 10000.0 / total) / 100.0 : 0.0);
                    redisMetrics.put("missRate", total > 0 ? Math.round(misses * 10000.0 / total) / 100.0 : 0.0);
                    redisMetrics.put("status", "healthy");
                }
            } else {
                redisMetrics.put("status", "unavailable");
            }
            cacheMetrics.put("redis", redisMetrics);

            return Result.success("缓存性能指标获取成功", cacheMetrics);
        } catch (Exception e) {
            log.error("获取缓存性能指标失败", e);
            return Result.error(com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR,
                              "获取缓存性能指标失败: 系统异常，请稍后重试");
        }
    }

    private int percent(long used, long max) {
        if (max <= 0) {
            return 0; // 非堆 max 可能是 -1（未定义上限），此时百分比无意义
        }
        return (int) Math.round(used * 100.0 / max);
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "-";
        }
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.2fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2fMB", bytes / (1024.0 * 1024));
        return String.format("%.2fGB", bytes / (1024.0 * 1024 * 1024));
    }
}
