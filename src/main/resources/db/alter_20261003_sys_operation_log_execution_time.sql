-- ============================================================================
-- 增量 DDL：sys_operation_log 增加「执行耗时」列
--
-- 用途：给【已有数据的库】补这一列。
--       db/init.sql 开头是 DROP TABLE IF EXISTS，只能对空库整份执行；
--       本文件只含一条 ALTER，作用于已存在的生产库。
--
-- 为什么需要这一列：
--   日志中心「统计分析」页要统计平均执行耗时（avgExecutionTime），
--   而 SysOperationLogMapper.xml 里的统计 SQL 一直在引用 execution_time，
--   sys_operation_log 表里却没有这列 —— MySQL 直接抛
--     Unknown column 'execution_time' in 'field list'
--   导致统计卡片 / 热门操作 TOP10 / 模块使用率三个查询全部 500。
--
--   ⚠️ 执行顺序：必须先跑本脚本，再发布带 SysOperationLog.executionTime 字段的代码。
--      实体加了字段后，AdminLogController 走的 MyBatis-Plus 自动 SELECT 会带上该列，
--      列不存在则操作日志列表也会跟着报 Unknown column。
--      （DEPLOY.md 第八节：DDL 不可回滚，健康检查失败会回滚镜像，让旧代码跑在新结构上；
--        本列为可空列，旧代码忽略它，因此「先 DDL 后发版」是双向安全的。）
--
-- 幂等性：MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS，
--         重复执行会报 1060 Duplicate column name，可据此判断是否已执行过。
--         执行前可先查：
--           SHOW COLUMNS FROM `sys_operation_log` LIKE 'execution_time';
--
-- 执行：docker exec -i check-repeat-mysql sh -c \
--         'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "check-repeat-system"' \
--         < db/alter_20261003_sys_operation_log_execution_time.sql
-- ============================================================================

SET NAMES utf8mb4;

ALTER TABLE `sys_operation_log`
  ADD COLUMN `execution_time` bigint DEFAULT NULL COMMENT '执行耗时(毫秒)' AFTER `operation_time`;

-- ---------------------------------------------------------------------------
-- 可选（建议手工单独执行，不放进本脚本）：
--   统计 SQL 一律按 operation_time 过滤与 GROUP BY，而该列目前没有索引，
--   日志量大时统计会全表扫。DEPLOY.md 大表 DDL 章节要求这类加索引操作
--   避开业务高峰期手工执行：
--     ALTER TABLE `sys_operation_log` ADD INDEX `idx_operation_time` (`operation_time`);
-- ---------------------------------------------------------------------------
