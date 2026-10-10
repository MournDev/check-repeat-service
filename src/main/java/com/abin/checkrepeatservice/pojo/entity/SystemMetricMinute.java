package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统资源指标分钟采样
 *
 * 每分钟一行（metric_time 秒置零），由 ResourceMetricsService 采样 upsert，
 * 给 SystemMonitoring 的性能趋势图（/api/admin/monitoring/performance）供数。
 *
 * 注意：本类【刻意不继承 BaseEntity】，与 MetricEventDaily 同理——
 * 唯一键 uk_metric_time 若叠加软删，同一分钟的补采样会更新到已删除行而静默不可见。
 * 清理走物理删除（deleteBefore）。
 */
@Data
@TableName("system_metric_minute")
public class SystemMetricMinute {

    /**
     * 主键ID（雪花ID，由业务代码生成并传入，故为 INPUT 而非 ASSIGN_ID）
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 指标时间（分钟精度，秒置零）
     */
    private LocalDateTime metricTime;

    /**
     * 整机CPU使用率%
     */
    private Double cpuUsage;

    /**
     * JVM进程CPU使用率%
     */
    private Double jvmCpuUsage;

    /**
     * 整机物理内存使用率%
     */
    private Double memoryUsage;

    /**
     * JVM堆内存使用率%
     */
    private Double jvmHeapUsage;

    /**
     * 磁盘使用率%（jar所在分区）
     */
    private Double diskUsage;

    /**
     * 数据库连接池使用率%
     */
    private Double dbPoolUsage;

    /**
     * 数据库活跃连接数
     */
    private Integer dbActiveConn;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 软删除标记（0-未删除，1-已删除）。本表不做软删除，恒为 0。
     */
    private Integer isDeleted;
}
