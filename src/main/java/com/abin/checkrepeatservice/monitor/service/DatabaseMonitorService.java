package com.abin.checkrepeatservice.monitor.service;

import com.abin.checkrepeatservice.common.Result;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据库监控服务
 *
 * 连接池指标来自 HikariCP 的 MXBean（真实瞬时值）；
 * slowQueries / cacheHitRate 来自 MySQL 的 SHOW GLOBAL STATUS：
 *   - Slow_queries：服务器全局慢查询累计数（阈值由 long_query_time 决定）
 *   - cacheHitRate：InnoDB buffer pool 命中率 = 1 - reads/read_requests
 * 健康分按连接池水位与等待线程实时计算，不再写死。
 */
@Slf4j
@Service
public class DatabaseMonitorService {

    @Autowired
    private DataSource dataSource;

    public Result<Map<String, Object>> getDatabaseStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            // 数据库基本信息 + 全局状态（慢查询、buffer pool 命中率）
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                Map<String, Object> dbInfo = new HashMap<>();
                dbInfo.put("productName", metaData.getDatabaseProductName());
                dbInfo.put("productVersion", metaData.getDatabaseProductVersion());
                dbInfo.put("driverName", metaData.getDriverName());
                dbInfo.put("url", metaData.getURL());
                status.put("databaseInfo", dbInfo);

                status.putAll(queryGlobalStatus(conn));
            }

            // 连接池状态
            if (dataSource instanceof HikariDataSource hikariDS) {
                var poolBean = hikariDS.getHikariPoolMXBean();
                var configBean = hikariDS.getHikariConfigMXBean();

                int active = poolBean.getActiveConnections();
                int idle = poolBean.getIdleConnections();
                int total = poolBean.getTotalConnections();
                int waiting = poolBean.getThreadsAwaitingConnection();
                int max = configBean.getMaximumPoolSize();

                double usageRate = total > 0 ? (double) active / total * 100 : 0;
                usageRate = Math.round(usageRate * 100.0) / 100.0;

                Map<String, Object> poolInfo = new HashMap<>();
                poolInfo.put("activeConnections", active);
                poolInfo.put("idleConnections", idle);
                poolInfo.put("totalConnections", total);
                poolInfo.put("maxConnections", max);
                poolInfo.put("waitingThreads", waiting);
                poolInfo.put("usageRate", usageRate);
                poolInfo.put("status", usageRate < 70 ? "normal" : usageRate < 90 ? "warning" : "danger");
                status.put("connectionPool", poolInfo);

                // 扁平字段：前端 SystemMonitoring 数据库卡片直接读顶层
                status.put("activeConnections", active);
                status.put("idleConnections", idle);
                status.put("maxConnections", max);
                status.put("waitingThreads", waiting);
                status.put("poolUsageRate", usageRate);

                // 健康分：连接池水位 + 等待线程实时计算
                int score = 100;
                if (usageRate >= 90) {
                    score -= 30;
                } else if (usageRate >= 70) {
                    score -= 15;
                }
                if (waiting > 0) {
                    score -= 20;
                }
                status.put("healthScore", Math.max(0, score));
                status.put("overallStatus", score >= 80 ? "HEALTHY" : score >= 60 ? "WARNING" : "CRITICAL");
            }

            return Result.success("数据库监控数据获取成功", status);
        } catch (Exception e) {
            log.error("获取数据库监控数据失败", e);
            return Result.error(com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR,
                              "获取数据库监控数据失败: " + e.getMessage());
        }
    }

    /**
     * 读 MySQL 全局状态：Slow_queries 累计数 + InnoDB buffer pool 命中率。
     * 查询失败（如权限不足）不抛出，只缺省字段——连接池指标才是主体。
     */
    private Map<String, Object> queryGlobalStatus(Connection conn) {
        Map<String, Object> result = new HashMap<>();
        long slowQueries = 0;
        long poolReads = -1;
        long poolReadRequests = -1;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SHOW GLOBAL STATUS WHERE Variable_name IN " +
                     "('Slow_queries', 'Innodb_buffer_pool_reads', 'Innodb_buffer_pool_read_requests')")) {
            while (rs.next()) {
                String name = rs.getString(1);
                long value = Long.parseLong(rs.getString(2));
                switch (name) {
                    case "Slow_queries" -> slowQueries = value;
                    case "Innodb_buffer_pool_reads" -> poolReads = value;
                    case "Innodb_buffer_pool_read_requests" -> poolReadRequests = value;
                    default -> { }
                }
            }
        } catch (Exception e) {
            log.debug("查询 MySQL 全局状态失败: {}", e.getMessage());
        }

        result.put("slowQueries", slowQueries);
        if (poolReadRequests > 0 && poolReads >= 0) {
            double hitRate = (1.0 - (double) poolReads / poolReadRequests) * 100;
            result.put("cacheHitRate", Math.round(hitRate * 100.0) / 100.0);
        }
        return result;
    }
}
