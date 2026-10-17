package com.abin.checkrepeatservice.monitor.service;

import com.abin.checkrepeatservice.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统监控服务
 * 提供真实的系统资源监控数据
 */
@Slf4j
@Service
public class SystemMonitorService {

    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final RuntimeMXBean runtimeBean;
    private final ThreadMXBean threadBean;
    private final Instant startTime;

    public SystemMonitorService() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.startTime = Instant.now();
    }

    /**
     * 获取系统基本信息
     */
    public Map<String, Object> getSystemInfo() {
        Map<String, Object> systemInfo = new HashMap<>();
        
        systemInfo.put("osName", osBean.getName());
        systemInfo.put("osVersion", osBean.getVersion());
        systemInfo.put("osArch", osBean.getArch());
        systemInfo.put("availableProcessors", osBean.getAvailableProcessors());
        
        return systemInfo;
    }

    /**
     * 获取CPU使用情况
     */
    public Map<String, Object> getCpuInfo() {
        Map<String, Object> cpuInfo = new HashMap<>();
        
        try {
            // CPU核心数
            cpuInfo.put("cores", osBean.getAvailableProcessors());
            
            // 系统负载（Unix/Linux系统）
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                double systemLoad = sunOsBean.getSystemLoadAverage();
                cpuInfo.put("systemLoadAverage", systemLoad >= 0 ? systemLoad : -1);

                // 整机CPU使用率（JDK14+ 的 getCpuLoad；不可用返回 -1）
                double systemCpuUsage = sunOsBean.getCpuLoad() * 100;
                cpuInfo.put("systemCpuUsage", systemCpuUsage >= 0 ? Math.round(systemCpuUsage * 100.0) / 100.0 : -1);

                // JVM进程CPU使用率（近似值）
                double cpuUsage = sunOsBean.getProcessCpuLoad() * 100;
                cpuInfo.put("processCpuUsage", cpuUsage >= 0 ? Math.round(cpuUsage * 100.0) / 100.0 : -1);

                // JVM进程CPU时间
                long processCpuTime = sunOsBean.getProcessCpuTime();
                cpuInfo.put("processCpuTime", processCpuTime);
            }
            
            cpuInfo.put("status", getCpuStatus(cpuInfo));
        } catch (Exception e) {
            cpuInfo.put("status", "unknown");
            cpuInfo.put("error", e.getMessage());
        }
        
        return cpuInfo;
    }

    /**
     * 获取内存使用情况
     */
    public Map<String, Object> getMemoryInfo() {
        Map<String, Object> memoryInfo = new HashMap<>();
        
        try {
            // 堆内存
            var heapMemory = memoryBean.getHeapMemoryUsage();
            memoryInfo.put("heapUsed", formatBytes(heapMemory.getUsed()));
            memoryInfo.put("heapMax", formatBytes(heapMemory.getMax()));
            memoryInfo.put("heapCommitted", formatBytes(heapMemory.getCommitted()));
            memoryInfo.put("heapInit", formatBytes(heapMemory.getInit()));
            
            double heapUsage = (double) heapMemory.getUsed() / heapMemory.getMax() * 100;
            memoryInfo.put("heapUsagePercent", Math.round(heapUsage * 100.0) / 100.0);
            
            // 非堆内存
            var nonHeapMemory = memoryBean.getNonHeapMemoryUsage();
            memoryInfo.put("nonHeapUsed", formatBytes(nonHeapMemory.getUsed()));
            memoryInfo.put("nonHeapMax", formatBytes(nonHeapMemory.getMax()));
            memoryInfo.put("nonHeapCommitted", formatBytes(nonHeapMemory.getCommitted()));
            
            double nonHeapUsage = (double) nonHeapMemory.getUsed() / nonHeapMemory.getMax() * 100;
            memoryInfo.put("nonHeapUsagePercent", Math.round(nonHeapUsage * 100.0) / 100.0);
            
            // JVM总内存信息
            long totalMemory = Runtime.getRuntime().totalMemory();
            long freeMemory = Runtime.getRuntime().freeMemory();
            long maxMemory = Runtime.getRuntime().maxMemory();
            
            memoryInfo.put("jvmTotal", formatBytes(totalMemory));
            memoryInfo.put("jvmFree", formatBytes(freeMemory));
            memoryInfo.put("jvmMax", formatBytes(maxMemory));
            memoryInfo.put("jvmUsed", formatBytes(totalMemory - freeMemory));
            
            double jvmUsage = (double) (totalMemory - freeMemory) / maxMemory * 100;
            memoryInfo.put("jvmUsagePercent", Math.round(jvmUsage * 100.0) / 100.0);

            // 整机物理内存（与 JVM 堆口径区分，供「系统资源」卡片使用）
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                long systemTotal = sunOsBean.getTotalMemorySize();
                long systemFree = sunOsBean.getFreeMemorySize();
                if (systemTotal > 0) {
                    memoryInfo.put("systemTotal", formatBytes(systemTotal));
                    memoryInfo.put("systemUsed", formatBytes(systemTotal - systemFree));
                    double systemUsage = (double) (systemTotal - systemFree) / systemTotal * 100;
                    memoryInfo.put("systemMemoryUsagePercent", Math.round(systemUsage * 100.0) / 100.0);
                }
            }

            memoryInfo.put("status", getMemoryStatus(memoryInfo));
        } catch (Exception e) {
            memoryInfo.put("status", "unknown");
            memoryInfo.put("error", e.getMessage());
        }
        
        return memoryInfo;
    }

    /**
     * 获取线程信息
     */
    public Map<String, Object> getThreadInfo() {
        Map<String, Object> threadInfo = new HashMap<>();
        
        try {
            threadInfo.put("threadCount", threadBean.getThreadCount());
            threadInfo.put("peakThreadCount", threadBean.getPeakThreadCount());
            threadInfo.put("daemonThreadCount", threadBean.getDaemonThreadCount());
            threadInfo.put("totalStartedThreadCount", threadBean.getTotalStartedThreadCount());
            
            // 线程状态分布
            Map<String, Integer> threadStates = new HashMap<>();
            for (var threadInfoObj : threadBean.getAllThreadIds()) {
                var threadState = threadBean.getThreadInfo(threadInfoObj).getThreadState();
                threadStates.merge(threadState.toString(), 1, Integer::sum);
            }
            threadInfo.put("threadStates", threadStates);
            
            threadInfo.put("status", getThreadStatus(threadInfo));
        } catch (Exception e) {
            threadInfo.put("status", "unknown");
            threadInfo.put("error", e.getMessage());
        }
        
        return threadInfo;
    }

    /**
     * 获取运行时信息
     */
    public Map<String, Object> getRuntimeInfo() {
        Map<String, Object> runtimeInfo = new HashMap<>();
        
        try {
            runtimeInfo.put("uptime", formatDuration(Duration.between(startTime, Instant.now())));
            runtimeInfo.put("startTime", startTime.toString());
            runtimeInfo.put("vmName", runtimeBean.getVmName());
            runtimeInfo.put("vmVersion", runtimeBean.getVmVersion());
            runtimeInfo.put("vmVendor", runtimeBean.getVmVendor());
            runtimeInfo.put("javaVersion", runtimeBean.getSpecVersion());
            runtimeInfo.put("inputArguments", runtimeBean.getInputArguments());
            runtimeInfo.put("classpath", runtimeBean.getClassPath());
        } catch (Exception e) {
            runtimeInfo.put("error", e.getMessage());
        }
        
        return runtimeInfo;
    }

    /**
     * 获取垃圾回收信息
     */
    public Map<String, Object> getGcInfo() {
        Map<String, Object> gcInfo = new HashMap<>();
        
        try {
            var gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            long totalCollections = 0;
            long totalCollectionTime = 0;
            
            for (var gcBean : gcBeans) {
                totalCollections += gcBean.getCollectionCount();
                totalCollectionTime += gcBean.getCollectionTime();
            }
            
            gcInfo.put("totalCollections", totalCollections);
            gcInfo.put("totalCollectionTime", totalCollectionTime + "ms");
            gcInfo.put("collectionBeans", gcBeans.size());
        } catch (Exception e) {
            gcInfo.put("error", e.getMessage());
        }
        
        return gcInfo;
    }

    /**
     * 获取磁盘使用情况
     * 注意：Java标准库不直接提供磁盘使用率，这里提供估算值
     */
    public Map<String, Object> getDiskInfo() {
        Map<String, Object> diskInfo = new HashMap<>();
        
        try {
            // 获取JVM所在文件系统的空间信息
            File root = new File(".").getAbsoluteFile().getParentFile();
            if (root == null) {
                root = new File("/");
            }
            
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usableSpace = root.getUsableSpace();
            
            long usedSpace = totalSpace - freeSpace;
            double usagePercent = totalSpace > 0 ? (double) usedSpace / totalSpace * 100 : 0;
            
            diskInfo.put("totalSpace", formatBytes(totalSpace));
            diskInfo.put("usedSpace", formatBytes(usedSpace));
            diskInfo.put("freeSpace", formatBytes(freeSpace));
            diskInfo.put("usableSpace", formatBytes(usableSpace));
            diskInfo.put("usagePercent", Math.round(usagePercent * 100.0) / 100.0);
            diskInfo.put("status", getDiskStatus(usagePercent));
            
        } catch (Exception e) {
            diskInfo.put("status", "unknown");
            diskInfo.put("error", e.getMessage());
            // 不返回任何估算值：宁缺毋假，监控页对缺省应显示未知而非随机数
        }
        
        return diskInfo;
    }

    /**
     * 获取综合系统状态
     */
    public Result<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            status.put("system", getSystemInfo());
            status.put("cpu", getCpuInfo());
            status.put("memory", getMemoryInfo());
            status.put("disk", getDiskInfo());
            status.put("threads", getThreadInfo());
            status.put("runtime", getRuntimeInfo());
            status.put("gc", getGcInfo());
            
            // 计算整体健康分数
            int healthScore = calculateHealthScore(status);
            status.put("healthScore", healthScore);
            status.put("overallStatus", getOverallStatus(healthScore));
            
            return Result.success("系统状态获取成功", status);
        } catch (Exception e) {
            return Result.error(com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR,
                              "获取系统状态失败: 系统异常，请稍后重试");
        }
    }

    // ===== 辅助方法 =====

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.2fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2fMB", bytes / (1024.0 * 1024));
        return String.format("%.2fGB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天 ");
        if (hours > 0) sb.append(hours).append("小时 ");
        if (minutes > 0) sb.append(minutes).append("分钟 ");
        sb.append(seconds).append("秒");
        
        return sb.toString().trim();
    }

    private String getCpuStatus(Map<String, Object> cpuInfo) {
        Double usage = (Double) cpuInfo.get("processCpuUsage");
        if (usage == null || usage < 0) return "unknown";
        if (usage < 70) return "normal";
        if (usage < 90) return "warning";
        return "danger";
    }

    private String getMemoryStatus(Map<String, Object> memoryInfo) {
        Double heapUsage = (Double) memoryInfo.get("heapUsagePercent");
        if (heapUsage == null) return "unknown";
        if (heapUsage < 75) return "normal";
        if (heapUsage < 90) return "warning";
        return "danger";
    }

    private String getThreadStatus(Map<String, Object> threadInfo) {
        Integer threadCount = (Integer) threadInfo.get("threadCount");
        if (threadCount == null) return "unknown";
        if (threadCount < 100) return "normal";
        if (threadCount < 200) return "warning";
        return "danger";
    }

    private String getDiskStatus(double usagePercent) {
        if (usagePercent < 80) return "normal";
        if (usagePercent < 95) return "warning";
        return "danger";
    }

    private int calculateHealthScore(Map<String, Object> status) {
        int score = 100;
        
        try {
            // CPU使用率扣分
            Map<String, Object> cpuInfo = (Map<String, Object>) status.get("cpu");
            Double cpuUsage = (Double) cpuInfo.get("processCpuUsage");
            if (cpuUsage != null && cpuUsage > 0) {
                if (cpuUsage > 90) score -= 20;
                else if (cpuUsage > 70) score -= 10;
            }
            
            // 内存使用率扣分
            Map<String, Object> memoryInfo = (Map<String, Object>) status.get("memory");
            Double heapUsage = (Double) memoryInfo.get("heapUsagePercent");
            if (heapUsage != null) {
                if (heapUsage > 90) score -= 25;
                else if (heapUsage > 75) score -= 15;
            }
            
            // 线程数扣分
            Map<String, Object> threadInfo = (Map<String, Object>) status.get("threads");
            Integer threadCount = (Integer) threadInfo.get("threadCount");
            if (threadCount != null) {
                if (threadCount > 200) score -= 15;
                else if (threadCount > 100) score -= 5;
            }
            
            // 磁盘使用率扣分
            Map<String, Object> diskInfo = (Map<String, Object>) status.get("disk");
            Double diskUsage = (Double) diskInfo.get("usagePercent");
            if (diskUsage != null) {
                if (diskUsage > 95) score -= 20;
                else if (diskUsage > 80) score -= 10;
            }
            
        } catch (Exception e) {
            // 发生异常时给较低分数
            score = 60;
        }
        
        return Math.max(0, Math.min(100, score));
    }

    private String getOverallStatus(int healthScore) {
        if (healthScore >= 80) return "HEALTHY";
        if (healthScore >= 60) return "WARNING";
        return "CRITICAL";
    }
}