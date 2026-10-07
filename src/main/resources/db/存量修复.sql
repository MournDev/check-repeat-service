-- ============================================================================
-- 存量修复 —— 一次性脚本：只在「结构或数据落后于 init.sql」的库上手工跑。
--
-- ⚠️ 空库不要跑本文件。第 1 段是增量 DDL，空库由 init.sql 一次建好，
--    跑本文件只会到处报 1060 Duplicate column name。
--
-- ⚠️ 顺序：**先跑完 种子数据.sql，再跑本文件**。
--    第 1 段要从 teacher_info / student_info 回填 sys_user.major_id，而
--    种子数据.sql 会先把那两张表的 major_id 改绑到新的专业 id；先种子后修复，
--    回填进去的才是新 id。跑反了的补救 SQL 见文末附录。
--
-- 适用症状（对症才跑，不是每个库都需要）：
--   · 对 SysUser 的任何查询都报 Unknown column 'college_name' / 'major_id'
--     —— 实体把这两列声明成了真的 @TableField，MyBatis-Plus 会把它们拼进每一条
--     自动生成的 SELECT。于是登录、管理端用户管理、学生/教师列表、各类 dashboard 全挂。
--     典型特征：库里 flyway_schema_history 有 V1~V7 却没有 V8。
--   · 管理端「教师审核统计」500 —— teacher_audit 表不存在。
--   · 日志中心「统计分析」三张卡全 500 —— sys_operation_log.execution_time 不存在。
--   · Dashboard 业务指标卡恒为 0、SystemMonitoring「待处理查重任务」无数据
--     —— metric_event_daily 表不存在（埋点失败被 BusinessMetricsService 吞掉只打 warn，
--     不报错，所以表现为静默的 0）。
--   · 相似度阈值保存成功、刷新后列表恒空且无报错 —— is_deleted 落成 NULL。
--   · 管理端「待分配学生」列表恒空 —— 分配字段链路的历史脏数据。
--
-- 执行方式：**先跑「0. 自检」**看清哪些已经到位，然后按 1 → 4 顺序执行，
--           最后跑「5. 执行后自检」。
--
-- ⚠️ 用 --force 跑。第 1 段的 ADD COLUMN / ADD INDEX 不幂等（MySQL 8 不支持
--    ADD COLUMN IF NOT EXISTS），而 mysql 客户端默认**遇错即停**：库里若已经
--    对齐过一部分，脚本会在第一条 1060 处中断，后面的段（含 1.7~1.9）一条都不执行。
--    加 --force 让它逐条尝试、越过已生效的语句继续：
--
--      mysql --force -uroot -p"$MYSQL_ROOT_PASSWORD" "check-repeat-system" < 存量修复.sql
--
--    此时下列报错都是**预期的「已执行过」信号**，不是失败：
--      1060 Duplicate column name       → 该列已加过
--      1061 Duplicate key name          → 该索引已建过
--      1054 Unknown column 'created_by' / 'create_user_id'
--                                       → 段 1.2 / 1.3 的审计列改名已完成
--    判断成败只看「5. 执行后自检」的数值，不看有没有 ERROR 行。
--    1.6、1.7 用的 CREATE TABLE IF NOT EXISTS 与 2~4 段本身就可重复执行。
--
-- 前置假设：目标库里 init.sql 那 59 张表都已存在。若某张表整个不存在，先看第 1 段
--           是否已为它准备好建表语句（teacher_audit → 1.6，metric_event_daily → 1.7）；
--           其余缺表该做的是用 init.sql 重建，而不是跑本文件。
-- ============================================================================

SET NAMES utf8mb4;


-- ---------------------------------------------------------------------
-- 0. 自检（变更前）：先看现状，已经到位的段跳过
-- ---------------------------------------------------------------------

-- 0.1 段 1：sys_user 那 12 列到了几列（12 列齐了就不用再跑段 1 的 sys_user 部分）
SELECT COUNT(*) AS sys_user_cols_present FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
   AND COLUMN_NAME IN ('professional_title','college_name','major_id','research_direction',
                       'current_advisor_count','grade','major','class_name','office',
                       'office_hours','max_review_count','review_deadline');

-- 0.2 段 1：teacher_audit 是否存在、paper_submit 那 3 列到齐没（期望 1 / 3）
SELECT (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_audit') AS has_teacher_audit,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_submit'
           AND COLUMN_NAME IN ('paper_title','paper_type','grade'))       AS paper_submit_cols;

-- 0.3 段 2：is_deleted 还是 NULL 的行数（>0 才需要段 2）
SELECT COUNT(*) AS rows_with_null_is_deleted
  FROM category_similarity_threshold WHERE is_deleted IS NULL;

-- 0.4 段 3：状态列默认值，以及库里实际有哪些取值
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_DEFAULT
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'paper_info' AND COLUMN_NAME = 'paper_status')
     OR (TABLE_NAME = 'check_task' AND COLUMN_NAME = 'check_status'));
SELECT 'paper_info.paper_status' AS col, paper_status AS val, COUNT(*) AS n
  FROM paper_info GROUP BY paper_status
UNION ALL
SELECT 'check_task.check_status', check_status, COUNT(*) FROM check_task GROUP BY check_status;

-- 0.5 段 4：分配字段链路的各种组合各有多少行
SELECT paper_status, allocation_status,
       CASE WHEN teacher_id IS NULL THEN '无导师' ELSE '有导师' END AS teacher,
       COUNT(*) AS n
  FROM paper_info WHERE is_deleted = 0
 GROUP BY paper_status, allocation_status, teacher
 ORDER BY paper_status, allocation_status, teacher;

-- 0.6 段 1.7~1.9：期望 1 / 1 / 2（都到位了只需跑 1.7，建表语句本身幂等；
--     1.8 / 1.9 那两条 ALTER 已执行过会报 1060 / 1061）
SELECT (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'metric_event_daily')            AS has_metric_event_daily,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log'
           AND COLUMN_NAME = 'execution_time')                                             AS has_execution_time,
       (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'category_similarity_threshold'
           AND INDEX_NAME IN ('idx_type_college','idx_type_major'))                        AS threshold_indexes;


-- ============================================================================
-- 段 1：库结构对齐（sys_user 合并教师/学生属性 等 9 处）
-- ============================================================================

-- ---------------------------------------------------------------------
-- 1.1 sys_user 合并教师 / 学生扩展属性
--    来源：teacher_info(10 列) + student_info(grade, class_name) = 12 列
-- ---------------------------------------------------------------------
ALTER TABLE `sys_user`
  ADD COLUMN `professional_title`    varchar(50)  DEFAULT NULL COMMENT '职称（教师）',
  ADD COLUMN `college_name`          varchar(100) DEFAULT NULL COMMENT '学院名称',
  ADD COLUMN `major_id`              bigint       DEFAULT NULL COMMENT '专业ID（关联major.id）',
  ADD COLUMN `research_direction`    varchar(255) DEFAULT NULL COMMENT '科研方向（教师）',
  ADD COLUMN `current_advisor_count` int          DEFAULT '0' COMMENT '当前指导任务数（教师）',
  ADD COLUMN `grade`                 varchar(20)  DEFAULT NULL COMMENT '年级（学生）',
  ADD COLUMN `major`                 varchar(100) DEFAULT NULL COMMENT '专业名称',
  ADD COLUMN `class_name`            varchar(100) DEFAULT NULL COMMENT '班级（学生）',
  ADD COLUMN `office`                varchar(100) DEFAULT NULL COMMENT '办公地点（教师）',
  ADD COLUMN `office_hours`          varchar(255) DEFAULT NULL COMMENT '办公时间（教师）',
  ADD COLUMN `max_review_count`      int          DEFAULT '5' COMMENT '最大审核数量（教师）',
  ADD COLUMN `review_deadline`       int          DEFAULT '7' COMMENT '审核期限（天，教师）';

ALTER TABLE `sys_user` ADD KEY `idx_sys_user_major_id` (`major_id`);

-- 回填教师：先做教师，保证 college_name / major_id / major 以教师数据为准
UPDATE `sys_user` u
  JOIN `teacher_info` t ON t.user_id = u.id AND t.is_deleted = 0
   SET u.professional_title    = t.professional_title,
       u.research_direction    = t.research_direction,
       u.current_advisor_count = t.current_advisor_count,
       u.office                = t.office,
       u.office_hours          = t.office_hours,
       u.max_review_count      = t.max_review_count,
       u.review_deadline       = t.review_deadline,
       u.college_name          = t.college_name,
       u.major_id              = t.major_id,
       u.major                 = t.major;

-- 回填学生：COALESCE 避免覆盖上一步教师已写入的值
UPDATE `sys_user` u
  JOIN `student_info` s ON s.user_id = u.id AND s.is_deleted = 0
   SET u.grade        = s.grade,
       u.class_name   = s.class_name,
       u.college_name = COALESCE(u.college_name, s.college_name),
       u.major_id     = COALESCE(u.major_id, s.major_id),
       u.major        = COALESCE(u.major, s.major);


-- ---------------------------------------------------------------------
-- 1.2 审计列改名：created_* / updated_* → create_* / update_*
--    这三张表的实体继承 BaseEntity，MyBatis-Plus 只认 create_by/create_time/
--    update_by/update_time。线上三张表均为 0 行，改名无数据风险。
-- ---------------------------------------------------------------------
ALTER TABLE `compare_lib`
  CHANGE COLUMN `created_by`   `create_by`   bigint   NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  CHANGE COLUMN `created_time` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updated_by`   `update_by`   bigint   NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  CHANGE COLUMN `updated_time` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `paper_attachment`
  CHANGE COLUMN `created_by`   `create_by`   bigint   NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  CHANGE COLUMN `created_time` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updated_by`   `update_by`   bigint   NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  CHANGE COLUMN `updated_time` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `paper_advisor_rel`
  CHANGE COLUMN `created_by`   `create_by`   bigint   NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  CHANGE COLUMN `created_time` `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CHANGE COLUMN `updated_by`   `update_by`   bigint   NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  CHANGE COLUMN `updated_time` `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';


-- ---------------------------------------------------------------------
-- 1.3 paper_attachment_rel：create_user_id → create_by（对齐实体字段名）
-- ---------------------------------------------------------------------
ALTER TABLE `paper_attachment_rel`
  CHANGE COLUMN `create_user_id` `create_by` bigint DEFAULT NULL COMMENT '创建人ID（学生ID）';


-- ---------------------------------------------------------------------
-- 1.4 check_report.paper_id 放松为可空
--    实体 CheckReport 没有 paperId 字段、三处写入路径也都未赋值；表内已有 60 行。
-- ---------------------------------------------------------------------
ALTER TABLE `check_report`
  MODIFY COLUMN `paper_id` bigint DEFAULT NULL COMMENT '对应的论文ID（关联paper_info.id，可空）';


-- ---------------------------------------------------------------------
-- 1.5 paper_submit 补 3 列
--    PaperAdvisorRelMapper 取 paper_title / paper_type、CheckResultMapper 按 grade
--    分组，线上 paper_submit 均无对应列。
-- ---------------------------------------------------------------------
ALTER TABLE `paper_submit`
  ADD COLUMN `paper_title` varchar(200) DEFAULT NULL COMMENT '论文标题',
  ADD COLUMN `paper_type`  varchar(50)  DEFAULT NULL COMMENT '论文类型',
  ADD COLUMN `grade`       varchar(20)  DEFAULT NULL COMMENT '年级（冗余自 sys_user.grade）';

-- 回填标题：取自 paper_info（通过 paper_id 关联）
UPDATE `paper_submit` ps
  JOIN `paper_info` pi ON ps.paper_id = pi.id
   SET ps.paper_title = pi.paper_title
 WHERE ps.paper_title IS NULL;

-- 回填年级：取自学生用户（依赖第 1 步已把 student_info.grade 回填进 sys_user）
UPDATE `paper_submit` ps
  JOIN `sys_user` u ON ps.student_id = u.id
   SET ps.grade = u.grade
 WHERE ps.grade IS NULL;


-- ---------------------------------------------------------------------
-- 1.6 teacher_audit：TeacherAuditMapper 的两条统计查询依赖此表，线上不存在
--    （管理端「教师审核统计」必然 500）。列按查询反推，本表为新建、纯追加。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `teacher_audit` (
  `id`                bigint   NOT NULL COMMENT '主键ID（雪花ID）',
  `submit_id`         bigint   NOT NULL COMMENT '论文提交ID（关联paper_submit.id）',
  `teacher_id`        bigint   NOT NULL COMMENT '审核教师ID（关联sys_user.id）',
  `audit_status`      tinyint  NOT NULL DEFAULT '0' COMMENT '审核状态：0-待审核，1-已通过，2-已驳回',
  `allocate_time`     datetime DEFAULT NULL COMMENT '分配时间',
  `audit_finish_time` datetime DEFAULT NULL COMMENT '审核完成时间',
  `create_by`         bigint   DEFAULT NULL COMMENT '创建人ID',
  `create_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`         bigint   DEFAULT NULL COMMENT '更新人ID',
  `update_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`        tinyint  NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_teacher_audit_teacher_status` (`teacher_id`, `audit_status`),
  KEY `idx_teacher_audit_submit` (`submit_id`),
  KEY `idx_teacher_audit_allocate_time` (`allocate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师审核分配表';


-- ---------------------------------------------------------------------
-- 1.7 metric_event_daily：「业务事件日聚合指标表」，线上不存在
--    （管理端 Dashboard 业务指标卡片 + SystemMonitoring 的
--      GET /api/admin/monitoring/metrics，缺表时埋点落库报
--      Table '...metric_event_daily' doesn't exist，指标恒为 0）。
--    埋点失败已在 BusinessMetricsService 内吞掉打 warn，不影响主流程。
--    本段用 CREATE TABLE IF NOT EXISTS，重复执行安全。
--
--    按 (event,result,stat_date) 做日聚合 + upsert，而非每次事件插一行原始日志：
--    两种查询形态都是分组计数（全量 GROUP BY event,result / 今日 stat_date=CURDATE()），
--    原始日志会无界增长且让 /metrics 变成对百万行做 GROUP BY。
--    唯一键同时覆盖两种查询形态，无需额外索引。
--    （当前 6 个计数点 × 365 天 ≈ 2190 行/年。）
-- ---------------------------------------------------------------------
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

-- 本表不做软删除，is_deleted 列只为与仓库既有表结构保持一致。
-- 清理历史数据请用物理 DELETE，例如：
--   DELETE FROM `metric_event_daily` WHERE `stat_date` < CURDATE() - INTERVAL 365 DAY;
-- 不要 UPDATE is_deleted = 1：MySQL 没有部分唯一索引，软删后的行仍占着
-- (event,result,stat_date) 唯一键，重新插入会变成更新到已删除行，数据静默不可见。

-- ---------------------------------------------------------------------
-- 1.8 sys_operation_log.execution_time：日志中心「统计分析」依赖此列
--    SysOperationLogMapper.xml 的统计 SQL 一直引用 execution_time，而列不存在，
--    MySQL 直接抛 Unknown column 'execution_time' in 'field list'，
--    统计卡片 / 热门操作 TOP10 / 模块使用率三个查询全部 500。
--    本列为可空列，旧代码忽略它，因此「先 DDL 后发版」双向安全。
--
--    幂等性：MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS，重复执行报
--    1060 Duplicate column name —— 报 1060 说明本段已执行过，跳过即可。
-- ---------------------------------------------------------------------
ALTER TABLE `sys_operation_log`
  ADD COLUMN `execution_time` bigint DEFAULT NULL COMMENT '执行耗时(毫秒)' AFTER `operation_time`;

-- ---------------------------------------------------------------------
-- 1.9 category_similarity_threshold 的两个查询索引
--    SimilarityThresholdResolver.categoryThreshold() 固定按
--      WHERE category_type = ? AND college_id = ? / major_id = ?
--      ORDER BY id DESC LIMIT 1
--    取单行。表本身小、全表扫无感，但每篇论文查重都要查两次
--    （先专业、未命中再学院），条数一多就是纯浪费。
--
--    幂等性：重复执行报 1061 Duplicate key name，属预期，跳过即可。
--    （init.sql 的建表语句里已含这两个索引，本段只补存量的老表。）
-- ---------------------------------------------------------------------
ALTER TABLE category_similarity_threshold
    ADD INDEX idx_type_college (category_type, college_id);

ALTER TABLE category_similarity_threshold
    ADD INDEX idx_type_major (category_type, major_id);


-- ============================================================================
-- 段 2：similarity_threshold 系列表的 is_deleted 历史 NULL 归零
--
-- 背景：category_similarity_threshold.is_deleted 在 init.sql 里一度是 DEFAULT NULL，
--   BaseEntity.isDeleted 只标了 @TableLogic、没有 fill = FieldFill.INSERT，
--   而 MyMetaObjectHandler.insertFill 用的 strictInsertFill 只填充带 fill 注解的字段
--   —— 应用侧插入时不会补这一列，于是行带着 NULL 落库。而 MyBatis-Plus 后续所有查询
--   都会拼上 `is_deleted = 0`，SQL 里 NULL = 0 结果为 NULL、永不成立，行被静默过滤：
--   阈值页保存成功，刷新后列表恒空，没有任何报错。
--   init.sql 里的默认值已修正，本段负责把修正之前落进去的 NULL 行捞回来。
-- ============================================================================

UPDATE `category_similarity_threshold` SET `is_deleted` = 0 WHERE `is_deleted` IS NULL;


-- ============================================================================
-- 段 3：状态列默认值改成字典内的值，并清掉残留的旧数字码
--
-- 背景：paper_info.paper_status 与 check_task.check_status 的默认值都写成了 '0'，
--   而这两列的取值域是字符串字典。任何漏赋值 paper_status 的 INSERT 都会落进
--   字典外的 '0'：既不在「待分配」列表里（那里要求 = 'pending'），也不属于任何
--   状态统计 —— 论文会从所有按状态筛选的界面里静默消失。
--
--   ALTER COLUMN ... SET DEFAULT 只改元数据，不重建表、不动已有数据。
-- ============================================================================

ALTER TABLE `paper_info`  ALTER COLUMN `paper_status`  SET DEFAULT 'pending';
ALTER TABLE `check_task` ALTER COLUMN `check_status` SET DEFAULT 'pending';

-- 映射直接来自 com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum
-- （0=pending 1=checking 2=completed 3=failure 4=cancelled），不是猜的。
UPDATE check_task SET check_status = CASE check_status
                          WHEN '0' THEN 'pending'
                          WHEN '1' THEN 'checking'
                          WHEN '2' THEN 'completed'
                          WHEN '3' THEN 'failure'
                          WHEN '4' THEN 'cancelled'
                       END
 WHERE check_status IN ('0', '1', '2', '3', '4');

-- paper_info.paper_status 不做自动换算：它没有对应的数字码词表，'0' 只是「插入时
-- 没赋值」的哨兵值，无从判断那篇论文当时处于哪一步。如有这类行，先跑下面这条看一眼
-- 再决定，确认无误后再写 UPDATE，别照抄：
--   SELECT id, student_id, teacher_id, paper_status, allocation_status, submit_time
--     FROM paper_info WHERE paper_status NOT IN
--          ('pending','assigned','checking','auditing','completed','rejected','withdrawn');
-- 经验判断：teacher_id 为空的多半就是「待分配」→ 'pending'；有 teacher_id 的多半是
-- 「已分配」→ 'assigned'。


-- ============================================================================
-- 段 4：分配字段链路的历史脏数据
-- ============================================================================

-- ---------------------------------------------------------------------
-- 4.0 先看清要改哪些行：分配字段的各种组合各有多少行
-- ---------------------------------------------------------------------
SELECT paper_status,
       allocation_status,
       CASE WHEN teacher_id IS NULL THEN '无导师' ELSE '有导师' END AS teacher,
       COUNT(*) AS n
  FROM paper_info
 WHERE is_deleted = 0
 GROUP BY paper_status, allocation_status, teacher
 ORDER BY paper_status, allocation_status, teacher;

-- 4.0.2 「4.1 修复 A」要动的行（有导师、却已退回待分配）
SELECT id, student_id, paper_title, teacher_id, teacher_name,
       paper_status, allocation_status, allocation_time, create_time
  FROM paper_info
 WHERE is_deleted = 0
   AND teacher_id IS NOT NULL
   AND paper_status = 'pending'
   AND allocation_status = 'rejected'
 ORDER BY create_time;

-- 4.0.3 「4.2 修复 B」要动的行（rejected，但论文已经走过分配这一步）
SELECT id, student_id, paper_title, teacher_id, teacher_name,
       paper_status, allocation_status, confirm_time, allocation_time
  FROM paper_info
 WHERE is_deleted = 0
   AND teacher_id IS NOT NULL
   AND allocation_status = 'rejected'
   AND paper_status IN ('assigned', 'checking', 'auditing', 'completed')
 ORDER BY paper_status, create_time;


-- ---------------------------------------------------------------------
-- 4.1 修复 A：退回待分配的行，把导师摘掉，让它真正回到待分配池
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
-- 4.2 修复 B：allocation_status 归位
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


-- ============================================================================
-- 段 5：执行后自检
-- ============================================================================


-- 5.1 结构：期望 39 / 0 / 0 / 0
--     left = 本文件应补齐的列数（12 sys_user + 3 paper_submit + 1 paper_attachment_rel
--            + 4+4+4 审计列改名 + 11 teacher_audit）
--     right/middle = 不该再残留的旧列名与旧状态
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME IN ('professional_title','college_name','major_id','research_direction',
                          'current_advisor_count','grade','major','class_name','office',
                          'office_hours','max_review_count','review_deadline')
  ) + (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_submit'
          AND COLUMN_NAME IN ('paper_title','paper_type','grade'))
    + (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_attachment_rel'
          AND COLUMN_NAME = 'create_by')
    + (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME IN ('compare_lib','paper_attachment','paper_advisor_rel')
          AND COLUMN_NAME IN ('create_by','create_time','update_by','update_time'))
    + (SELECT COUNT(*) FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_audit')
  AS should_be_39,
  -- 注意必须限定这三张表：admin_task_intervene_log / re_submit_record /
  -- rule_lib_relation 在 init.sql 里本来就用 created_*/updated_*，不加限定会误报 12
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('compare_lib','paper_attachment','paper_advisor_rel')
      AND COLUMN_NAME IN ('created_by','created_time','updated_by','updated_time')
  ) AS old_audit_cols_should_be_0,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paper_attachment_rel'
      AND COLUMN_NAME = 'create_user_id'
  ) AS old_create_user_id_should_be_0;

-- 5.2 check_report.paper_id 应为可空（期望 YES）
SELECT IS_NULLABLE AS should_be_YES FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'check_report' AND COLUMN_NAME = 'paper_id';

-- 5.3 回填是否生效（有 teacher_info / student_info 数据时应非空）
--     teacher_info 里有几行 major 存的是空串 '' 而不是 NULL，用 NULLIF 排掉，
--     否则它们会被 IS NOT NULL 算成"已回填"
SELECT COUNT(*) AS total,
       SUM(college_name IS NOT NULL)                    AS has_college_name,
       SUM(NULLIF(TRIM(major), '') IS NOT NULL)         AS has_major,
       SUM(NULLIF(TRIM(grade), '') IS NOT NULL)         AS has_grade
  FROM sys_user WHERE is_deleted = 0;

-- 5.4 悬空引用检查：sys_user.major_id 应全部能在 major 表里找到（期望 0）
SELECT COUNT(*) AS dangling_major_id_should_be_0
  FROM sys_user u LEFT JOIN major m ON u.major_id = m.id
 WHERE u.major_id IS NOT NULL AND m.id IS NULL;


-- 以下来自脏数据修复脚本的后置自检
-- 5.5 分配脏数据的执行后自检
-- ---------------------------------------------------------------------

-- 5.5.1 两段的目标行应当都归零
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

-- 5.5.2 管理端「待分配学生」用的条件，执行后应当不再为空
--     （对齐 AdminAssignmentServiceImpl.getUnassignedStudents：
--      is_deleted=0 AND teacher_id IS NULL AND paper_status='pending'）
SELECT COUNT(*) AS '待分配学生（论文）数'
  FROM paper_info
 WHERE is_deleted = 0
   AND teacher_id IS NULL
   AND paper_status = 'pending';

-- 5.5.3 整体分布再打一遍，和 4.0 对照
SELECT paper_status,
       allocation_status,
       CASE WHEN teacher_id IS NULL THEN '无导师' ELSE '有导师' END AS teacher,
       COUNT(*) AS n
  FROM paper_info
 WHERE is_deleted = 0
 GROUP BY paper_status, allocation_status, teacher
 ORDER BY paper_status, allocation_status, teacher;

-- 5.6 段 1.7~1.9 执行后：期望 1 / 1 / 2（和 0.6 对照，缺哪个补哪个）
SELECT (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'metric_event_daily')            AS has_metric_event_daily,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log'
           AND COLUMN_NAME = 'execution_time')                                             AS has_execution_time,
       (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'category_similarity_threshold'
           AND INDEX_NAME IN ('idx_type_college','idx_type_major'))                        AS threshold_indexes;


-- ============================================================================
-- 附录：顺序跑反了（先本文件、后 种子数据.sql）的补救
--       现象：sys_user.major_id 指向旧 major id，段 5 的悬空检查返回非 0。
-- UPDATE `sys_user` SET `major_id` = CASE `major_id`
--   WHEN 1668572928015 THEN 1668573001004   -- 软件技术     -> 软件工程
--   WHEN 1668572928026 THEN 1668573001020   -- 机械电子工程（同名）
--   WHEN 1668572928045 THEN 1668573001065   -- 工商管理（同名）
--   WHEN 1668572928049 THEN 1668573001068   -- 人力资源管理（同名）
--   WHEN 1668572928051 THEN 1668573001070   -- 知识产权（同名）
--   WHEN 1668572928054 THEN 1668573001069   -- 民事诉讼法   -> 法学
--   ELSE `major_id` END
-- WHERE `major_id` IN (1668572928015,1668572928026,1668572928045,
--                      1668572928049,1668572928051,1668572928054);
