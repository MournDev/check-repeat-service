package com.abin.checkrepeatservice.monitor.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统资源指标分钟表 Mapper
 *
 * 刻意不用 MyBatis-Plus 的自动方法：写入路径是 upsert，查询路径是时间范围，
 * 都不是按主键/单行的 CRUD，写显式 SQL 更直白。
 */
@Mapper
public interface SystemMetricMinuteMapper {

    /**
     * 按分钟 upsert。秒置零后作为 metric_time，依赖 uk_metric_time 判重：
     * 首次写入插入新行，已存在则更新所有指标列。
     */
    @Insert("INSERT INTO system_metric_minute " +
            "(id, metric_time, cpu_usage, jvm_cpu_usage, memory_usage, jvm_heap_usage, disk_usage, db_pool_usage, db_active_conn, create_time, update_time, is_deleted) " +
            "VALUES (#{id}, #{metricTime}, #{cpuUsage}, #{jvmCpuUsage}, #{memoryUsage}, #{jvmHeapUsage}, #{diskUsage}, #{dbPoolUsage}, #{dbActiveConn}, NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE " +
            "cpu_usage = VALUES(cpu_usage), jvm_cpu_usage = VALUES(jvm_cpu_usage), " +
            "memory_usage = VALUES(memory_usage), jvm_heap_usage = VALUES(jvm_heap_usage), " +
            "disk_usage = VALUES(disk_usage), db_pool_usage = VALUES(db_pool_usage), " +
            "db_active_conn = VALUES(db_active_conn), update_time = NOW()")
    int upsert(@Param("id") Long id,
               @Param("metricTime") LocalDateTime metricTime,
               @Param("cpuUsage") Double cpuUsage,
               @Param("jvmCpuUsage") Double jvmCpuUsage,
               @Param("memoryUsage") Double memoryUsage,
               @Param("jvmHeapUsage") Double jvmHeapUsage,
               @Param("diskUsage") Double diskUsage,
               @Param("dbPoolUsage") Double dbPoolUsage,
               @Param("dbActiveConn") Integer dbActiveConn);

    /**
     * 查询时间范围内的指标（含起止两端），按 metric_time 升序。
     */
    @Select("SELECT metric_time, cpu_usage, jvm_cpu_usage, memory_usage, jvm_heap_usage, " +
            "disk_usage, db_pool_usage, db_active_conn " +
            "FROM system_metric_minute " +
            "WHERE metric_time >= #{start} AND metric_time <= #{end} AND is_deleted = 0 " +
            "ORDER BY metric_time ASC")
    List<Map<String, Object>> selectRange(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /**
     * 物理删除早于指定时间的数据（清理旧数据）。
     */
    @Delete("DELETE FROM system_metric_minute WHERE metric_time < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);
}
