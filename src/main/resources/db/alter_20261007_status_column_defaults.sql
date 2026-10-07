-- =====================================================================
-- 【存量库】状态列的默认值改成字典内的值，并清掉库里已在的字典外值
--
-- 这个目录（db/）下所有 .sql 都不会被应用自动执行，全靠手工跑。
-- init.sql 已同步改过（新库不用跑本文件）。本文件只对已有数据的库执行一次。
--
-- 背景（2026-10-07）：
--   paper_info.paper_status 与 check_task.check_status 两列的默认值都写成了 '0'，
--   而这两列的取值域是字符串字典（pending/assigned/checking/... 与
--   pending/checking/completed/failure）。任何漏赋值 paper_status 的 INSERT 都会落进
--   字典外的 '0'：它既不在「待分配」列表里（那里要求 = 'pending'），也不属于任何
--   状态统计——论文会从所有按状态筛选的界面里静默消失。
--   本机库 check_task 里已经有一行 check_status='2'（旧数字码残留，见第 2 段）。
--
-- 执行方式：先单独跑「0. 自检」，再按 1 → 2 → 3 顺序执行。
-- =====================================================================


-- ---------------------------------------------------------------------
-- 0. 自检（变更前）
-- ---------------------------------------------------------------------

-- 0.1 两个默认值当前是什么（改完应当都变成 pending）
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_DEFAULT, IS_NULLABLE
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'paper_info' AND COLUMN_NAME = 'paper_status')
     OR (TABLE_NAME = 'check_task' AND COLUMN_NAME = 'check_status'));

-- 0.2 库里实际有哪些取值（改默认值不会动存量行，脏值要看这里）
SELECT 'paper_info.paper_status' AS col, paper_status AS val, COUNT(*) AS n
  FROM paper_info GROUP BY paper_status
UNION ALL
SELECT 'check_task.check_status', check_status, COUNT(*)
  FROM check_task GROUP BY check_status;


-- ---------------------------------------------------------------------
-- 1. 变更：默认值 → 字典内值
--    ALTER COLUMN ... SET DEFAULT 只改元数据，不重建表、不动已有数据。
-- ---------------------------------------------------------------------
ALTER TABLE `paper_info`  ALTER COLUMN `paper_status`  SET DEFAULT 'pending';
ALTER TABLE `check_task` ALTER COLUMN `check_status` SET DEFAULT 'pending';


-- ---------------------------------------------------------------------
-- 2. 变更：把 check_task.check_status 里残留的旧「数字码」换算成字典值
--
--    映射直接来自 com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum
--    （0=pending 1=checking 2=completed 3=failure 4=cancelled），不是猜的。
--    本机库有 1 行 '2'（该行 end_time 与 report_id 都有、fail_reason 为空，
--    确实是「查重成功」，与 2=completed 一致）。
-- ---------------------------------------------------------------------
UPDATE check_task SET check_status = CASE check_status
                          WHEN '0' THEN 'pending'
                          WHEN '1' THEN 'checking'
                          WHEN '2' THEN 'completed'
                          WHEN '3' THEN 'failure'
                          WHEN '4' THEN 'cancelled'
                       END
 WHERE check_status IN ('0', '1', '2', '3', '4');

-- paper_info.paper_status 不做自动换算：它没有对应的数字码词表，'0' 只是「插入时没赋值」
-- 的哨兵值，无从判断那篇论文当时处于哪一步。如果有这类行，先跑下面这条看一眼再决定：
--   SELECT id, student_id, teacher_id, paper_status, allocation_status, submit_time
--     FROM paper_info WHERE paper_status NOT IN
--          ('pending','assigned','checking','auditing','completed','rejected','withdrawn');
-- 经验判断：teacher_id 为空的多半就是"待分配"→ 'pending'；有 teacher_id 的多半是
-- "已分配"→ 'assigned'。确认无误后再写 UPDATE，别照抄。


-- ---------------------------------------------------------------------
-- 3. 执行后自检
-- ---------------------------------------------------------------------

-- 3.1 默认值（应均为 pending）
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'paper_info' AND COLUMN_NAME = 'paper_status')
     OR (TABLE_NAME = 'check_task' AND COLUMN_NAME = 'check_status'));

-- 3.2 字典外的值应当已经清空（查不出行即为干净）
SELECT 'check_task.check_status' AS col, check_status AS val, COUNT(*) AS n
  FROM check_task
 WHERE check_status NOT IN ('pending', 'checking', 'completed', 'failure', 'cancelled')
 GROUP BY check_status
UNION ALL
SELECT 'paper_info.paper_status', paper_status, COUNT(*)
  FROM paper_info
 WHERE paper_status IS NULL
    OR paper_status NOT IN ('pending', 'assigned', 'checking', 'auditing', 'completed', 'rejected', 'withdrawn')
 GROUP BY paper_status;
