package com.abin.checkrepeatservice.monitor.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 业务事件日聚合指标 Mapper
 *
 * 刻意不用 MyBatis-Plus 的自动方法：写入路径是 upsert，查询路径是分组求和，
 * 都不是按主键/单行的 CRUD，写显式 SQL 更直白。
 */
@Mapper
public interface MetricEventDailyMapper {

    /**
     * 当日计数 +1。首次写入插入新行，已存在则累加。
     * 依赖唯一键 uk_event_result_date 判重，id 为雪花ID由调用方生成。
     */
    @Insert("INSERT INTO metric_event_daily " +
            "(id, event, result, stat_date, event_count, create_time, update_time, is_deleted) " +
            "VALUES (#{id}, #{event}, #{result}, CURDATE(), 1, NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE event_count = event_count + 1, update_time = NOW()")
    int upsert(@Param("id") Long id, @Param("event") String event, @Param("result") String result);

    /**
     * 全量累计：每个 (event, result) 的历史总次数
     */
    @Select("SELECT event, result, SUM(event_count) AS cnt FROM metric_event_daily " +
            "WHERE is_deleted = 0 GROUP BY event, result")
    List<Map<String, Object>> selectAllTime();

    /**
     * 今日增量：每个 (event, result) 当天的次数
     */
    @Select("SELECT event, result, SUM(event_count) AS cnt FROM metric_event_daily " +
            "WHERE is_deleted = 0 AND stat_date = CURDATE() GROUP BY event, result")
    List<Map<String, Object>> selectToday();

    /**
     * 清理历史数据。必须是物理删除——见 MetricEventDaily 类注释中关于唯一键与软删除的说明。
     */
    @Delete("DELETE FROM metric_event_daily WHERE stat_date < #{before}")
    int deleteBefore(@Param("before") LocalDate before);
}
