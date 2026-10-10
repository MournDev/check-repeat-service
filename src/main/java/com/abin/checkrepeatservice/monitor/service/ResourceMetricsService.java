package com.abin.checkrepeatservice.monitor.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.monitor.mapper.SystemMetricMinuteMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统资源指标分钟采样服务
 *
 * 每分钟把整机/JVM 的 CPU、内存、磁盘与数据库连接池指标落一行到
 * system_metric_minute（uk_metric_time upsert），给
 * /api/admin/monitoring/performance 的 1h/24h/7d 趋势图供数。
 * 落库而非内存是刻意的：重启后历史曲线保留。
 *
 * 聚合在 Java 侧做而不在 SQL 侧 GROUP BY：库开 only_full_group_by，
 * 时间桶表达式分组容易踩坑，且 7d 全量分钟行也只有约 1 万行，读出来聚合完全够快。
 *
 * 采样/清理的全部异常都吞掉只打日志：监控是旁路，绝不能影响主流程。
 */
@Slf4j
@Service
public class ResourceMetricsService {

    private static final DateTimeFormatter MINUTE_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");

    @Resource
    private SystemMetricMinuteMapper systemMetricMinuteMapper;

    @Resource
    private SystemMonitorService systemMonitorService;

    @Resource
    private DatabaseMonitorService databaseMonitorService;

    /**
     * 数据保留天数，超时由每日清理物理删除
     */
    @Value("${monitoring.resource-metrics.retention-days:30}")
    private int retentionDays;

    /**
     * 每分钟采样一次。fixedDelay 保证上一次写完再计时，启动后 10 秒首采。
     * 表不存在时（存量库未跑 存量修复.sql 段 1.14）每分钟一条 warn，不崩。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void sample() {
        try {
            Double cpuUsage = null, jvmCpuUsage = null, memoryUsage = null, jvmHeapUsage = null, diskUsage = null;
            Double dbPoolUsage = null;
            Integer dbActiveConn = null;

            var status = systemMonitorService.getSystemStatus();
            if (status.isSuccess()) {
                Map<String, Object> data = (Map<String, Object>) status.getData();

                Map<String, Object> cpu = (Map<String, Object>) data.get("cpu");
                if (cpu != null) {
                    cpuUsage = nonNegative((Double) cpu.get("systemCpuUsage"));
                    jvmCpuUsage = nonNegative((Double) cpu.get("processCpuUsage"));
                }
                Map<String, Object> memory = (Map<String, Object>) data.get("memory");
                if (memory != null) {
                    memoryUsage = nonNegative((Double) memory.get("systemMemoryUsagePercent"));
                    jvmHeapUsage = nonNegative((Double) memory.get("heapUsagePercent"));
                }
                Map<String, Object> disk = (Map<String, Object>) data.get("disk");
                if (disk != null) {
                    diskUsage = nonNegative((Double) disk.get("usagePercent"));
                }
            }

            var dbStatus = databaseMonitorService.getDatabaseStatus();
            if (dbStatus.isSuccess()) {
                Map<String, Object> dbData = (Map<String, Object>) dbStatus.getData();
                dbPoolUsage = (Double) dbData.get("poolUsageRate");
                Object active = dbData.get("activeConnections");
                if (active instanceof Number n) {
                    dbActiveConn = n.intValue();
                }
            }

            LocalDateTime minute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
            systemMetricMinuteMapper.upsert(IdWorker.getId(), minute,
                    cpuUsage, jvmCpuUsage, memoryUsage, jvmHeapUsage, diskUsage, dbPoolUsage, dbActiveConn);
        } catch (Exception e) {
            log.warn("系统资源指标采样失败（表不存在请先跑 存量修复.sql 段 1.14）: {}", e.getMessage());
        }
    }

    /**
     * 每日 03:40 清理 retention-days 之前的分钟数据（物理删除，本表不做软删）。
     */
    @Scheduled(cron = "0 40 3 * * ?")
    public void cleanup() {
        try {
            LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
            int deleted = systemMetricMinuteMapper.deleteBefore(before);
            if (deleted > 0) {
                log.info("清理系统资源指标历史数据 {} 行（{} 天前）", deleted, retentionDays);
            }
        } catch (Exception e) {
            log.warn("清理系统资源指标历史数据失败: {}", e.getMessage());
        }
    }

    /**
     * 性能趋势：period=1h → 12 点（5 分钟均值），24h → 24 点（小时均值），7d → 7 点（日均值）。
     *
     * 无样本的时间桶补 null：ECharts 显示为断点，比伪造 0 诚实。
     * 刚部署/表刚建时数据点从少到多逐步积累，属预期。
     *
     * @return timestamps + cpuUsage/memoryUsage/diskUsage/dbConnections 四个等长序列
     */
    public Map<String, Object> getPerformanceTrend(String period) {
        int points;
        long bucketMinutes;
        boolean dayLabel;
        switch (period == null ? "1h" : period.toLowerCase()) {
            case "24h" -> { points = 24; bucketMinutes = 60; dayLabel = false; }
            case "7d"  -> { points = 7;  bucketMinutes = 1440; dayLabel = true; }
            default    -> { points = 12; bucketMinutes = 5;  dayLabel = false; }
        }

        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime start = end.minusMinutes(points * bucketMinutes);

        List<Map<String, Object>> rows;
        try {
            rows = systemMetricMinuteMapper.selectRange(start, end);
        } catch (Exception e) {
            log.warn("查询系统资源指标失败（表不存在请先跑 存量修复.sql 段 1.14）: {}", e.getMessage());
            rows = List.of();
        }

        // 每桶的累加器：sum[指标][桶序号] / count
        double[][] sums = new double[4][points];
        int[][] counts = new int[4][points];

        for (Map<String, Object> row : rows) {
            LocalDateTime t = (LocalDateTime) row.get("metric_time");
            long offset = ChronoUnit.MINUTES.between(start, t);
            int bucket = (int) (offset / bucketMinutes);
            if (bucket < 0 || bucket >= points) {
                continue;
            }
            accumulate(sums, counts, bucket, 0, row.get("cpu_usage"));
            accumulate(sums, counts, bucket, 1, row.get("memory_usage"));
            accumulate(sums, counts, bucket, 2, row.get("disk_usage"));
            accumulate(sums, counts, bucket, 3, row.get("db_active_conn"));
        }

        List<String> timestamps = new ArrayList<>(points);
        List<Double> cpu = new ArrayList<>(points);
        List<Double> memory = new ArrayList<>(points);
        List<Double> disk = new ArrayList<>(points);
        List<Integer> dbConnections = new ArrayList<>(points);

        for (int i = 0; i < points; i++) {
            LocalDateTime bucketStart = start.plusMinutes((long) i * bucketMinutes);
            timestamps.add(bucketStart.format(dayLabel ? DAY_LABEL : MINUTE_LABEL));
            cpu.add(avgOrNull(sums[0][i], counts[0][i]));
            memory.add(avgOrNull(sums[1][i], counts[1][i]));
            disk.add(avgOrNull(sums[2][i], counts[2][i]));
            Double connAvg = avgOrNull(sums[3][i], counts[3][i]);
            dbConnections.add(connAvg == null ? null : (int) Math.round(connAvg));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamps", timestamps);
        data.put("cpuUsage", cpu);
        data.put("memoryUsage", memory);
        data.put("diskUsage", disk);
        data.put("dbConnections", dbConnections);
        return data;
    }

    /**
     * 数据库连接池使用率历史（近 minutes 分钟，分钟粒度原始值），供数据库监控卡片小图。
     */
    public List<Map<String, Object>> getPoolUsageHistory(int minutes) {
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime start = end.minusMinutes(minutes);
        List<Map<String, Object>> rows;
        try {
            rows = systemMetricMinuteMapper.selectRange(start, end);
        } catch (Exception e) {
            log.warn("查询连接池历史失败: {}", e.getMessage());
            return List.of();
        }
        List<Map<String, Object>> history = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object v = row.get("db_pool_usage");
            if (v == null) {
                continue;
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", ((LocalDateTime) row.get("metric_time")).format(MINUTE_LABEL));
            point.put("value", ((Number) v).doubleValue());
            history.add(point);
        }
        return history;
    }

    /**
     * 监控语义里 -1 表示"不可用"（JMX 约定），落库时转成 null
     */
    private Double nonNegative(Double v) {
        return (v == null || v < 0) ? null : v;
    }

    private void accumulate(double[][] sums, int[][] counts, int bucket, int metric, Object value) {
        if (value instanceof Number n) {
            sums[metric][bucket] += n.doubleValue();
            counts[metric][bucket]++;
        }
    }

    private Double avgOrNull(double sum, int count) {
        if (count == 0) {
            return null;
        }
        return Math.round(sum / count * 100.0) / 100.0;
    }
}
