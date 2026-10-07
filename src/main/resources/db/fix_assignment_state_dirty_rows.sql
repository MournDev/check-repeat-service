-- =====================================================================
-- 【存量数据修复】paper_info 上「导师 / 分配状态 / 论文状态」三者对不上的行
--
-- 这个目录（db/）下所有 .sql 都不会被应用自动执行，全靠手工跑。
-- 本文件不含任何 DDL，只有 UPDATE，可重复执行（第二次影响 0 行）。
--
-- 背景（2026-10-07）：全项目判断「这篇论文有没有导师」一律只看 teacher_id 是否为空
--   （待分配列表、自动分配、学生端「导师互动」都如此），而判断「老师是否还要确认」
--   看 allocation_status（教师端待确认列表查 teacher_id + allocation_status='pending'）。
--   历史上有多条路径只改了其中一个字段，于是攒出下面两类脏行：
--
--   A. 教师拒收后没清 teacher_id（TeacherAssignmentServiceImpl.doRejectAssignment）：
--      paper_status 改回了 pending，teacher_id 却留着。这类论文成了僵尸——进不了待分配
--      列表（要求 teacher_id 为空）、自动分配也拒收（见 teacher_id 非空就返回"已分配"）、
--      学生端却还显示着那位已经拒收的老师。
--      代码侧已修（改用 LambdaUpdateWrapper 显式 set teacher_id = null；updateById
--      默认忽略 null 字段，清空字段用它无效）。本段负责把修改之前留下的行改回来。
--
--   B. allocation_status 停在 'rejected'，论文却已经往前走过了分配这一步
--      （paper_status 是 checking/auditing/completed）：老师要确认接收才会进查重/审核，
--      所以这些行的 rejected 是脏值（后续分配路径没回写 allocation_status）。
--      代码侧已修（AdminAssignmentServiceImpl.applyAssignment 现在把这一组字段写全）。
--
-- 执行顺序：**先部署修好的后端，再跑本文件**。否则刚清干净的论文会被旧代码再写脏。
-- 本文件已在临时库 cr_dangling_fix_test 演练过（第 1 段：15 行，待分配列表由 0 → 15）。
--
-- 执行方式：先跑「0. 自检」看清要改哪些行，再依次执行 1、2，最后跑「3. 执行后自检」。
-- =====================================================================


-- ---------------------------------------------------------------------
-- 0. 自检：分配字段的各种组合各有多少行（先看清再改）
-- ---------------------------------------------------------------------
SELECT paper_status,
       allocation_status,
       CASE WHEN teacher_id IS NULL THEN '无导师' ELSE '有导师' END AS teacher,
       COUNT(*) AS n
  FROM paper_info
 WHERE is_deleted = 0
 GROUP BY paper_status, allocation_status, teacher
 ORDER BY paper_status, allocation_status, teacher;

-- 0.2 第 1 段要动的行（有导师、却已退回待分配）
SELECT id, student_id, paper_title, teacher_id, teacher_name,
       paper_status, allocation_status, allocation_time, create_time
  FROM paper_info
 WHERE is_deleted = 0
   AND teacher_id IS NOT NULL
   AND paper_status = 'pending'
   AND allocation_status = 'rejected'
 ORDER BY create_time;

-- 0.3 第 2 段要动的行（rejected，但论文已经走过分配这一步）
SELECT id, student_id, paper_title, teacher_id, teacher_name,
       paper_status, allocation_status, confirm_time, allocation_time
  FROM paper_info
 WHERE is_deleted = 0
   AND teacher_id IS NOT NULL
   AND allocation_status = 'rejected'
   AND paper_status IN ('assigned', 'checking', 'auditing', 'completed')
 ORDER BY paper_status, create_time;


-- ---------------------------------------------------------------------
-- 1. 修复 A：退回待分配的行，把导师摘掉，让它真正回到待分配池
--
--    保留 allocation_status='rejected' 与 allocation_time 不动——它们是「上一轮被拒」
--    的痕迹（重新分配时会被覆盖）。update_time 由库上的 ON UPDATE CURRENT_TIMESTAMP
--    自动刷新，不用手写。
--
--    提醒：如果某行其实是「管理员手动分配过、但那版单条分配没回写 allocation_status」
--    造成的，那么清掉后需要重新分配一次。这是刻意选的失败方向——留着 teacher_id 的话
--    这篇论文在管理端和学生端都还能看到，唯独待分配列表看不见，等于谁都找不回来。
-- ---------------------------------------------------------------------
UPDATE paper_info
   SET teacher_id   = NULL,
       teacher_name = NULL
 WHERE is_deleted = 0
   AND teacher_id IS NOT NULL
   AND paper_status = 'pending'
   AND allocation_status = 'rejected';


-- ---------------------------------------------------------------------
-- 2. 修复 B：allocation_status 归位
--
--    B1. 已经进查重/审核（checking/auditing/completed）说明导师接收已确认过
--        （confirmAssignment 才会把状态推进到 checking 并写 confirmed）→ 置 confirmed。
--        注：这些行 confirm_time 仍为空，本文件不去补造时间戳。
--    B2. 还停在 assigned 的是「已分配、等老师确认」→ 置 pending，否则老师端
--        待确认列表里看不到它。
-- ---------------------------------------------------------------------
UPDATE paper_info
   SET allocation_status = 'confirmed'
 WHERE is_deleted = 0
   AND teacher_id IS NOT NULL
   AND allocation_status = 'rejected'
   AND paper_status IN ('checking', 'auditing', 'completed');

UPDATE paper_info
   SET allocation_status = 'pending'
 WHERE is_deleted = 0
   AND teacher_id IS NOT NULL
   AND allocation_status = 'rejected'
   AND paper_status = 'assigned';


-- ---------------------------------------------------------------------
-- 3. 执行后自检
-- ---------------------------------------------------------------------

-- 3.1 两段的目标行应当都归零
SELECT 'A. 有导师却待分配（应为 0）' AS chk, COUNT(*) AS n
  FROM paper_info
 WHERE is_deleted = 0 AND teacher_id IS NOT NULL
   AND paper_status = 'pending' AND allocation_status = 'rejected'
UNION ALL
SELECT 'B. rejected 但已过分配阶段（应为 0）', COUNT(*)
  FROM paper_info
 WHERE is_deleted = 0 AND teacher_id IS NOT NULL
   AND allocation_status = 'rejected'
   AND paper_status IN ('assigned', 'checking', 'auditing', 'completed');

-- 3.2 管理端「待分配学生」用的条件，执行后应当不再为空
--     （对齐 AdminAssignmentServiceImpl.getUnassignedStudents：
--      is_deleted=0 AND teacher_id IS NULL AND paper_status='pending'）
SELECT COUNT(*) AS '待分配学生（论文）数'
  FROM paper_info
 WHERE is_deleted = 0
   AND teacher_id IS NULL
   AND paper_status = 'pending';

-- 3.3 整体分布再打一遍，和 0 段对照
SELECT paper_status,
       allocation_status,
       CASE WHEN teacher_id IS NULL THEN '无导师' ELSE '有导师' END AS teacher,
       COUNT(*) AS n
  FROM paper_info
 WHERE is_deleted = 0
 GROUP BY paper_status, allocation_status, teacher
 ORDER BY paper_status, allocation_status, teacher;
