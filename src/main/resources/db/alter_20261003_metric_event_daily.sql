-- ============================================================================
-- 增量 DDL：新增「业务事件日聚合指标表」metric_event_daily
--
-- 用途：给【已有数据的库】补这张表。
--       db/init.sql 开头是 DROP TABLE IF EXISTS，只能对空库整份执行；
--       本文件是对已存在库的增量脚本。
--
-- 为什么需要这张表：
--   管理端 Dashboard 的业务指标卡片（登录成功/失败、注册、论文提交、
--   审核通过/驳回）和 SystemMonitoring 的「待处理查重任务」，
--   前端调的是 GET /api/admin/monitoring/metrics。
--   该接口此前在后端不存在（落到静态资源处理器报 NoResourceFoundException），
--   且后端没有任何业务埋点，无数据可返回。
--
--   本表按 (event, result, stat_date) 做日聚合 + upsert，
--   而不是每次事件插一行原始日志：
--     两种查询形态都是分组计数（全量 GROUP BY event,result / 今日 stat_date=CURDATE()），
--     原始事件日志会无界增长，且让 /metrics 变成对百万行做 GROUP BY。
--     聚合行每条事件只写一条原子语句，表大小 = 事件数 × 结果数 × 天数
--     （当前 6 个计数点 × 365 天 ≈ 2190 行/年）。
--
--   ⚠️ 执行顺序：必须先跑本脚本，再发布带 MetricEventDaily 实体的代码。
--      否则首次业务事件（登录/注册/提交/审核）触发埋点落库时会报
--      Table 'check-repeat-system.metric_event_daily' doesn't exist。
--      埋点失败已在 BusinessMetricsService 内部吞掉并打 warn，
--      不会影响业务主流程，但指标会一直为 0。
--
-- 幂等性：用了 CREATE TABLE IF NOT EXISTS，重复执行安全。
--         执行前可先查：
--           SHOW TABLES LIKE 'metric_event_daily';
--
-- 执行：docker exec -i check-repeat-mysql sh -c \
--         'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "check-repeat-system"' \
--         < db/alter_20261003_metric_event_daily.sql
--
-- 冗余说明：唯一键 (event,result,stat_date) 同时覆盖上面两种查询形态，
--           无需额外索引。
-- ============================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `metric_event_daily` (
  `id`          bigint      NOT NULL                                    COMMENT '主键ID（雪花ID，由业务代码生成，非自增）',
  `event`       varchar(64) NOT NULL                                    COMMENT '事件类型(user_login/user_register/paper_submit/paper_review)',
  `result`      varchar(32) NOT NULL                                    COMMENT '结果标签(success/failure/pass/reject)',
  `stat_date`   date        NOT NULL                                    COMMENT '统计日期',
  `event_count` bigint      NOT NULL DEFAULT 0                          COMMENT '当日累计次数',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  tinyint     NOT NULL DEFAULT 0                          COMMENT '软删除标记(0-未删除,1-已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_result_date` (`event`,`result`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务事件日聚合指标表';

-- ---------------------------------------------------------------------------
-- 说明（不需要执行）：
--   本表不做软删除，is_deleted 列只为与仓库既有表结构保持一致。
--   清理历史数据请用物理 DELETE，例如：
--     DELETE FROM `metric_event_daily` WHERE `stat_date` < CURDATE() - INTERVAL 365 DAY;
--   不要 UPDATE is_deleted = 1：MySQL 没有部分唯一索引，
--   软删后的行仍占着 (event,result,stat_date) 唯一键，会导致重新插入
--   变成更新到已删除行，数据静默不可见。
-- ---------------------------------------------------------------------------
