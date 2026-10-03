-- ============================================================================
-- 增量种子数据：补全操作类型配置（sys_operation_type）缺失的 5 个类型
--
-- 用途：给【已有数据的库】补这几个操作类型。
--       db/init.sql 开头是 DROP TABLE IF EXISTS，只能对空库整份执行；
--       本文件不含任何 DDL，可安全地对生产库重复执行（幂等）。
--
-- 背景：init.sql 里 sys_operation_type 只有 CREATE TABLE、没有 INSERT，
--       但这张表在实际库里是**有数据的**（134 行，模块为
--       论文管理/系统管理/教师管理/用户管理/查重管理/通知管理/学生管理/日志管理）。
--       本文件只补实际日志里出现、而字典里没有的 5 个 type，
--       命名与模块归属均对齐字典中已有的同前缀条目，不另起一套。
--
-- 为什么需要补：
--   日志中心「统计分析」页的「模块使用率」饼图靠
--     LEFT JOIN sys_operation_type t ON t.type = l.operation_type
--   取 module。未收录的 type 会落到 COALESCE 的 '未知'。
--   截至 2026-10-03，sys_operation_log 中 2369 行里有 83 行（3.5%）未命中，
--   恰好是下面这 5 个 type；补上后覆盖率为 100%。
--   （剩余 2286 行已由字典中既有条目命中，无需改动。）
--
-- 幂等方式：靠 type 上的唯一键 uk_type 做 ON DUPLICATE KEY UPDATE。
--           id 是 AUTO_INCREMENT，显式省略让其自增。
--
-- 执行：docker exec -i check-repeat-mysql sh -c \
--         'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "check-repeat-system"' \
--         < db/seed_sys_operation_type.sql
-- ============================================================================

-- 必须先声明连接字符集：本文件含中文，客户端若按 latin1 解析会写进乱码。
SET NAMES utf8mb4;

INSERT INTO `sys_operation_type`
  (`type`, `name`, `module`, `permission_level`, `status`) VALUES
  -- 与既有 admin_* 条目（如 admin_user_update）同属管理动作，归属教师管理
  ('admin_auto_assign',  '管理员自动分配', '教师管理', 'HIGH',   1),
  ('check_task_create',  '创建查重任务',   '查重管理', 'MEDIUM', 1),
  ('check_task_delete',  '删除查重任务',   '查重管理', 'HIGH',   1),
  ('user_update_info',   '更新用户信息',   '用户管理', 'MEDIUM', 1),
  ('teacher_update_info','更新教师信息',   '教师管理', 'MEDIUM', 1)
ON DUPLICATE KEY UPDATE
  `name`             = VALUES(`name`),
  `module`           = VALUES(`module`),
  `permission_level` = VALUES(`permission_level`),
  `status`           = VALUES(`status`);
