-- ============================================================================
-- 阶段 4：查重/登录链路性能索引
--
-- 目标库：MySQL 8.x
-- 执行顺序：先跑 0. 前置检查，确认无重复主键后再执行 1~3，最后跑 4. 自检。
--
-- 幂等说明（MySQL 8 不支持 ADD/DROP INDEX IF EXISTS）：
--   · ADD INDEX 重跑会报 1061 Duplicate key name  → 已生效，可忽略
--   · ADD PRIMARY KEY 重跑会报 1068 Multiple primary key → 已生效，可忽略
--   · DROP INDEX 重跑会报 1091 Can't DROP ... check that column/key exists → 已生效，可忽略
--   建议用 mysql --force 执行，逐条尝试、越过已生效语句。
-- ============================================================================

-- 0. 前置检查 ---------------------------------------------------------------

-- 0.1 sys_login_log 是否存在重复 id（有结果时不能直接加主键，先人工去重）
SELECT id, COUNT(*) AS dup_count
  FROM sys_login_log
 GROUP BY id
HAVING COUNT(*) > 1;

-- 0.2 当前相关索引（执行前对照）
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND table_name IN ('check_task', 'sys_login_log', 'check_result', 'file_info')
 GROUP BY table_name, index_name
 ORDER BY table_name, index_name;

-- 1. check_task：状态超时扫描 & 论文时间线 --------------------------------

-- (check_status, is_deleted, start_time)：
--   支撑超时扫描 WHERE check_status='checking' AND is_deleted=0 AND start_time < ?
--   以及待审/统计按状态过滤（两等值列在前，范围列在后）
ALTER TABLE `check_task`
  ADD KEY `idx_ct_status_delete_start` (`check_status`, `is_deleted`, `start_time`);

-- (paper_id, is_deleted, create_time)：
--   支撑学生/教师任务列表 paper_id IN (...) AND is_deleted=0 ORDER BY create_time DESC
ALTER TABLE `check_task`
  ADD KEY `idx_ct_paper_delete_time` (`paper_id`, `is_deleted`, `create_time`);

-- 2. sys_login_log：补主键 + 账号时间线 -----------------------------------

-- 2.1 补主键（此前该表只有普通索引，updateById 会全表扫描）
ALTER TABLE `sys_login_log`
  ADD PRIMARY KEY (`id`);

-- 2.2 用户/管理员按账号查登录历史
ALTER TABLE `sys_login_log`
  ADD KEY `idx_sll_username_time` (`username`, `login_time`);

-- 3. 删除重复/无用索引 -----------------------------------------------------

-- check_result 上 idx_check_result_task_id / idx_check_result_paper_id
-- 与已有 idx_task_id / idx_paper_id 完全重复（同列同序），只保留后者。
ALTER TABLE `check_result`
  DROP INDEX `idx_check_result_task_id`,
  DROP INDEX `idx_check_result_paper_id`;

-- file_info 上 idx_file_info_md5 与唯一索引 idx_md5 重复；
-- 唯一索引已能覆盖等值查询，普通索引纯属写放大。
ALTER TABLE `file_info`
  DROP INDEX `idx_file_info_md5`;

-- 4. 执行后自检 -------------------------------------------------------------

-- 4.1 check_task 期望 2 个新索引
SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND table_name = 'check_task'
   AND index_name IN ('idx_ct_status_delete_start', 'idx_ct_paper_delete_time')
 GROUP BY index_name;

-- 4.2 sys_login_log 主键与 username+time 索引（期望 primary_key=1 / username_idx=1）
SELECT
  SUM(CASE WHEN index_name = 'PRIMARY' THEN 1 ELSE 0 END) AS primary_key,
  SUM(CASE WHEN index_name = 'idx_sll_username_time' THEN 1 ELSE 0 END) AS username_idx
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND table_name = 'sys_login_log';

-- 4.3 重复索引应已消失（期望 dup_check_result_idx=0 / dup_file_info_idx=0）
SELECT
  (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'check_result'
      AND index_name IN ('idx_check_result_task_id', 'idx_check_result_paper_id')) AS dup_check_result_idx,
  (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'file_info'
      AND index_name = 'idx_file_info_md5') AS dup_file_info_idx;
