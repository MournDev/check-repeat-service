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
--   · 管理端「用户管理」列表 500，日志报
--     Cannot invoke "String.hashCode()" because "<local4>" is null
--     —— 有 sys_user.user_type 为 NULL 的行（自注册的遗留），跑 4.3 段回填。
--   · 学生端「我的论文」对从未查重的论文也显示「查重报告」按钮、点击报
--     「未找到该论文的查重报告」，总相似比列错显示 0% 而非「未检测」
--     —— similarity_rate 被落成 0.00 而非 NULL，跑 4.4 段归位。
--   · 需要「学科领域」独立成表（subject / major_subject / teacher_subject）、
--     论文带 subject_id、阈值支持学科维度 —— 跑 1.10 段（纯加表加列，不动字典）。
--   · 论文类型升级成 PaperTypeEnum、阈值要能按论文类型分档 —— 跑 1.11 段
--     （仅给 category_similarity_threshold 加一列一索引，新列 NULL = 适用全部类型，
--     故存量配置的生效范围不变）。**漏跑它会让新版后端的所有查重任务 500**：
--     CategorySimilarityThreshold 一旦带上 paperType 字段，MyBatis-Plus 生成的
--     每一条 SELECT 都会引用 paper_type。必须是「先跑 1.11，再部署后端」。
--   · 权限守护栏（新版后端）依赖 sys_role.role_code 唯一 —— 跑 1.13 段
--     （重复编码去重、已软删角色改码腾位、建唯一索引；可重复执行）。建议
--     「先跑 1.13，再部署后端」：新版 deleteRole 软删时会改码，和本段配合。
--   · SystemMonitoring 性能趋势图无数据、日志每分钟报
--     Table 'system_metric_minute' doesn't exist —— 跑 1.14 段（建表，
--     CREATE TABLE IF NOT EXISTS，可重复执行）。采样写入失败只打 warn 不崩，
--     但缺表时趋势图永远没有数据。必须是「先跑 1.14，再部署后端」。
--   · 论文全生命周期各期接口 500（指导记录 / 评阅 / 答辩 / 句级明细 / 报告验真 /
--     签核 / 检测策略 / 学院额度 / 导师评价）—— 跑 1.16 段（P1~P6 全部建表 +
--     存量表扩列 + 字典种子，幂等可重复执行）。必须是「先跑 1.16，再部署后端」。
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
-- 前置假设：目标库里 init.sql 那 76 张表都已存在。若某张表整个不存在，先看第 1 段
--           是否已为它准备好建表语句（teacher_audit → 1.6，metric_event_daily → 1.7，
--           论文全生命周期 P1~P6 的 11 张表 → 1.16）；其余缺表该做的是用 init.sql
--           重建，而不是跑本文件。
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

-- 0.7 段 1.10 学科领域重构的前置现状
--     has_subject_tables 期望 3（subject / major_subject / teacher_subject）
--     subject_cols       期望 2（paper_info.subject_id + category_similarity_threshold.subject_id）
--     dict_subject_rows  旧字典 sys_dict_data 里 dict_type='subject_field' 的行数（本机/线上都是 100）
--     papers_pending_backfill >0 才需要 1.10.5 回填
--     注意：本探针只用 information_schema 判断表/列在不在，不去直接 SELECT 那三张表，
--     否则结构还没对齐的库里这一句就先报 1146 了。（sys_dict_data 本身没有 is_deleted 列。）
SELECT (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME IN ('subject','major_subject','teacher_subject'))               AS has_subject_tables,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND ((TABLE_NAME = 'paper_info'  AND COLUMN_NAME = 'subject_id')
             OR (TABLE_NAME = 'category_similarity_threshold' AND COLUMN_NAME = 'subject_id'))) AS subject_cols,
       (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type = 'subject_field')             AS dict_subject_rows,
       (SELECT COUNT(*) FROM paper_info
         WHERE is_deleted = 0 AND subject_id IS NULL
           AND subject_code IS NOT NULL AND TRIM(subject_code) <> '')                     AS papers_pending_backfill;

-- 0.8 段 4.3：user_type 为 NULL / 空串的行数（>0 才需要段 4.3）
--     这一列不是结构问题，纯脏数据；不查 information_schema，直接数行即可。
SELECT COUNT(*) AS user_type_null_rows FROM sys_user WHERE user_type IS NULL OR user_type = '';

-- 0.9 段 1.11 论文类型维度的前置现状（没跑过段 1.11 的库上期望 0 / 0，跑过则为 1 / 1）
--     threshold_pt_col    category_similarity_threshold.paper_type 在不在
--     threshold_pt_index  idx_type_paper_type 在不在
--     用 information_schema 判断列/索引在不在，避免结构没对齐的库上直接 SELECT 报 1054
--     ⚠️ 索引必须数 COUNT(DISTINCT INDEX_NAME)，不能数 COUNT(*)：STATISTICS 是
--        「每个索引的每一列一行」，idx_type_paper_type 建在两列上，COUNT(*) 恒为 2，
--        会让人误以为"有两个索引"。
SELECT (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'category_similarity_threshold'
           AND COLUMN_NAME = 'paper_type')                                                AS threshold_pt_col,
       (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'category_similarity_threshold'
           AND INDEX_NAME = 'idx_type_paper_type')                                        AS threshold_pt_index;

-- 0.10 段 1.15 个人设置「通知/偏好」6 列的前置现状（没跑过段 1.15 的库上期望 0）
SELECT COUNT(*) AS sys_user_pref_cols_present FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
   AND COLUMN_NAME IN ('notify_system_error','notify_security_warning',
                       'notify_system_announcement','notify_email',
                       'log_retention_days','backup_frequency');

-- 0.11 段 1.16 论文全生命周期 P1~P6 的前置现状
--   没跑过 1.16 的库上期望 lifecycle_tables=0 / lifecycle_new_cols=0；
--   跑过的库期望 11 / 10（P2 的 workflow_step 1 列 + P5 的 9 列）。
SELECT COUNT(*) AS lifecycle_tables FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('guidance_record','review_assignment','review_workflow_progress',
                      'defense_batch','defense_group_member','defense_record',
                      'check_sentence_hit','paper_check_signoff','check_policy',
                      'check_college_quota','advisor_evaluation');

SELECT COUNT(*) AS lifecycle_new_cols FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'paper_info'   AND COLUMN_NAME IN ('workflow_step','degree_type'))
     OR (TABLE_NAME = 'check_task'   AND COLUMN_NAME IN ('max_per_source','citation_rate'))
     OR (TABLE_NAME = 'check_result' AND COLUMN_NAME IN ('max_per_source','citation_rate'))
     OR (TABLE_NAME = 'check_report' AND COLUMN_NAME = 'content_hash')
     OR (TABLE_NAME = 'system_param' AND COLUMN_NAME IN ('guidance_weight','review_weight','defense_weight')));

-- 0.12 段 1.17 paper_info 版本关联两列的前置现状
--   没跑过 1.17 的老库期望 paper_version_cols=0；跑过（或用 init.sql 新建的库）期望 2。
SELECT COUNT(*) AS paper_version_cols FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'paper_info'
   AND COLUMN_NAME IN ('version','parent_paper_id');


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


-- ---------------------------------------------------------------------
-- 1.10 学科领域重构：subject / major_subject / teacher_subject + 两处加列 + 存量回填
--
-- 背景：学科领域原先是一张没有版本控制的字典树 —— 100 行 sys_dict_data
--   (dict_type='subject_field')，层级靠 dict_value 里的连字符约定出来，没有 parent_id；
--   教师研究方向又是第三套取值域（前端硬编码的 7 个短码，和这 100 个编码对不上）。
--   本段把它升级成独立主数据表，并让论文/阈值/教师研究方向共用同一套取值域。
--
-- 与 种子数据.sql 段 3B 的关系：那边是「先 DELETE 再 INSERT 写死 id」的整表替换，
--   这边改成 INSERT ... ON DUPLICATE KEY UPDATE（打到 uk_subject_code）—— 因为
--   paper_info.subject_id / major_subject / teacher_subject 都指向这些 id，
--   逐行 upsert 不会制造一段「先删空、后重插」的悬空窗口。
--
-- ⚠️ 本段**一行都不删 sys_dict_data**。理由见 DEPLOY.md 第八节：deploy.sh 健康检查
--   失败会回滚应用镜像但 DDL 不回滚，旧代码正好跑在新结构上；旧代码的学科树只读
--   字典，删了之后回滚，论文提交页的选择器直接空白。字典的清理要单独出脚本、
--   且只在确认所有环境都不再回滚之后执行。
--
-- 幂等性：1.10.1 是 CREATE TABLE IF NOT EXISTS；1.10.2 / 1.10.3 的 ALTER 重复执行
--   报 1060 / 1061（预期）；1.10.4~1.10.7 全部可重复执行。
-- ---------------------------------------------------------------------

-- 1.10.1 三张新表（DDL 与 init.sql 逐字一致）
CREATE TABLE IF NOT EXISTS `subject` (
  `id` bigint NOT NULL COMMENT '主键ID（写死的13位，见 种子数据.sql 段 3B）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `parent_id` bigint DEFAULT NULL COMMENT '父学科ID（一级门类为NULL，二级指向所属门类id）',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '层级：1-一级门类，2-二级学科（由 parent_id 推导，不接受前端传值）',
  `code` varchar(50) NOT NULL COMMENT '学科编码（唯一，如 ENG、ENG-CS）。对外稳定标识，paper_info.subject_code 存它；创建后不可改',
  `name` varchar(128) NOT NULL COMMENT '学科名称（如 工学、人工智能），可改',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '同层排序',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用（0-停用，1-启用）：停用后不再出现在选择器/阈值候选中，已引用的数据不受影响',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_code` (`code`),
  KEY `idx_subject_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学科领域表（取代 sys_dict_data 的 subject_field 字符串树）';

CREATE TABLE IF NOT EXISTS `major_subject` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `major_id` bigint NOT NULL COMMENT '专业ID（关联major.id）',
  `subject_id` bigint NOT NULL COMMENT '学科领域ID（关联subject.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_subject` (`major_id`,`subject_id`),
  KEY `idx_major_subject_subject_id` (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业↔学科领域映射表';

CREATE TABLE IF NOT EXISTS `teacher_subject` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `user_id` bigint NOT NULL COMMENT '教师用户ID（关联sys_user.id）',
  `subject_id` bigint NOT NULL COMMENT '学科领域ID（关联subject.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_subject` (`user_id`,`subject_id`),
  KEY `idx_teacher_subject_subject_id` (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师研究方向↔学科领域关联表';

-- 1.10.2 paper_info 加 subject_id（可空列 + 索引，重复执行报 1060 / 1061）
--        subject_code 保留不动：它是 subject.code 的冗余列，值域必须维持「编码」形态
--        —— AcademicIntegrityServiceImpl:95 拿它当 subjectArea 去匹配资源分类。
ALTER TABLE `paper_info`
  ADD COLUMN `subject_id` bigint DEFAULT NULL COMMENT '学科领域ID（关联subject.id）。写入时由 SubjectService.findIdByCode 反查 subject_code 得到' AFTER `subject_code`;

ALTER TABLE `paper_info`
  ADD INDEX `idx_paper_info_subject_id` (`subject_id`);

-- 1.10.3 category_similarity_threshold 增加 subject 维度（同上，1060 / 1061 预期内）
--        这张表没有唯一键，去重靠 Java 侧 SimilarityThresholdServiceImpl.validate 的
--        naturalKey，所以加 subject 行不会撞键；但 naturalKey 必须同步拼上 subjectId。
ALTER TABLE `category_similarity_threshold`
  MODIFY COLUMN `category_type` varchar(50) DEFAULT NULL COMMENT '分类类型：college-学院，major-专业，subject-学科领域';

ALTER TABLE `category_similarity_threshold`
  ADD COLUMN `subject_id` bigint DEFAULT NULL COMMENT '学科领域ID（category_type=subject 时必填，关联subject.id）' AFTER `major_id`;

ALTER TABLE `category_similarity_threshold`
  ADD INDEX `idx_type_subject` (`category_type`,`subject_id`);

-- 1.10.4 播 100 行学科（10 门类 + 90 二级），与 种子数据.sql 段 3B 同一份清单。
--        code 与原 dict_value 完全一致，因此 paper_info.subject_code 的存量值一个都不用改。
--        冲突时只更新可变的展示/排序/启用列，不动 code（它是匹配键）、也不动 create_by/create_time。
--        create_by/update_by 写 1：这两列只是 NOT NULL 的审计列，全局没有 FK 指向 sys_user
--        （init.sql 里为学科这几张表只声明了 PRIMARY/UNIQUE/KEY，故意不加外键），
--        所以不必去猜一个真实存在过的用户 id。种子路径（种子数据.sql 段 3B）用的是 @op。
INSERT INTO `subject` (id, create_by, create_time, update_by, update_time, is_deleted, delete_by, delete_time, parent_id, level, code, name, sort_no, is_enabled) VALUES
(1668573200000, 1, NOW(), 1, NOW(), 0, NULL, NULL, NULL, 1, 'ENG',   '工学',     1, 1),
(1668573200001, 1, NOW(), 1, NOW(), 0, NULL, NULL, NULL, 1, 'MED',   '医学',     2, 1),
(1668573200002, 1, NOW(), 1, NOW(), 0, NULL, NULL, NULL, 1, 'SCI',   '理学',     3, 1),
(1668573200003, 1, NOW(), 1, NOW(), 0, NULL, NULL, NULL, 1, 'ECON',  '经济学',   4, 1),
(1668573200004, 1, NOW(), 1, NOW(), 0, NULL, NULL, NULL, 1, 'MGT',   '管理学',   5, 1),
(1668573200005, 1, NOW(), 1, NOW(), 0, NULL, NULL, NULL, 1, 'LAW',   '法学',     6, 1),
(1668573200006, 1, NOW(), 1, NOW(), 0, NULL, NULL, NULL, 1, 'LIT',   '文学',     7, 1),
(1668573200007, 1, NOW(), 1, NOW(), 0, NULL, NULL, NULL, 1, 'EDU',   '教育学',   8, 1),
(1668573200008, 1, NOW(), 1, NOW(), 0, NULL, NULL, NULL, 1, 'ART',   '艺术学',   9, 1),
(1668573200009, 1, NOW(), 1, NOW(), 0, NULL, NULL, NULL, 1, 'INTER', '交叉学科', 10, 1),
(1668573201000, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-AI',      '人工智能',               1, 1),
(1668573201001, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-CS',      '计算机科学与技术',       2, 1),
(1668573201002, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-SE',      '软件工程',               3, 1),
(1668573201003, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-CYBER',   '网络空间安全',           4, 1),
(1668573201004, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-BIGDATA', '大数据技术',             5, 1),
(1668573201005, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-CLOUD',   '云计算技术',             6, 1),
(1668573201006, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-IoT',     '物联网工程',             7, 1),
(1668573201007, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-BC',      '区块链工程',             8, 1),
(1668573201008, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-ME',      '微电子科学与工程',       9, 1),
(1668573201009, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-EE',      '电气工程及其自动化',    10, 1),
(1668573201010, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-AUTO',    '自动化',                11, 1),
(1668573201011, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-MECH',    '机械设计制造及其自动化', 12, 1),
(1668573201012, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-CIVIL',   '土木工程',              13, 1),
(1668573201013, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-HYDRO',   '水利水电工程',          14, 1),
(1668573201014, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-SURVEY',  '测绘工程',              15, 1),
(1668573201015, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-ENV',     '环境工程',              16, 1),
(1668573201016, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-CHEM',    '化学工程与工艺',        17, 1),
(1668573201017, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-FOOD',    '食品科学与工程',        18, 1),
(1668573201018, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-BIO',     '生物工程',              19, 1),
(1668573201019, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-AERO',    '航空航天工程',          20, 1),
(1668573201020, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-MARINE',  '海洋工程',              21, 1),
(1668573201021, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-RAIL',    '轨道交通信号与控制',    22, 1),
(1668573201022, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-COM',     '通信工程',              23, 1),
(1668573201023, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-INFO',    '信息工程',              24, 1),
(1668573201024, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-CLIN',   '临床医学',                1, 1),
(1668573201025, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-DENT',   '口腔医学',                2, 1),
(1668573201026, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-PH',     '公共卫生与预防医学',      3, 1),
(1668573201027, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-PHAR',   '药学',                    4, 1),
(1668573201028, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-TCM',    '中药学',                  5, 1),
(1668573201029, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-NURSE',  '护理学',                  6, 1),
(1668573201030, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-IMG',    '医学影像学',              7, 1),
(1668573201031, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-LAB',    '医学检验技术',            8, 1),
(1668573201032, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-REHAB',  '康复医学',                9, 1),
(1668573201033, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-PREV',   '预防医学',               10, 1),
(1668573201034, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-ANES',   '麻醉学',                 11, 1),
(1668573201035, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-PSY',    '精神病与精神卫生学',     12, 1),
(1668573201036, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-INT',    '中西医结合',             13, 1),
(1668573201037, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-PREC',   '精准医学',               14, 1),
(1668573201038, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-MATH',     '数学',          1, 1),
(1668573201039, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-PHY',      '物理学',        2, 1),
(1668573201040, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-CHEM',     '化学',          3, 1),
(1668573201041, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-BIO',      '生物学',        4, 1),
(1668573201042, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-ECOL',     '生态学',        5, 1),
(1668573201043, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-ASTR',     '天文学',        6, 1),
(1668573201044, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-GEO',      '地理学',        7, 1),
(1668573201045, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-ATM',      '大气科学',      8, 1),
(1668573201046, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-MAR',      '海洋科学',      9, 1),
(1668573201047, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-GEOP',     '地球物理学',   10, 1),
(1668573201048, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-GEOL',     '地质学',       11, 1),
(1668573201049, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-STAT',     '统计学',       12, 1),
(1668573201050, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-BIOINFO',  '生物信息学',   13, 1),
(1668573201051, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-MATP',     '材料物理',     14, 1),
(1668573201052, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-FIN',   '金融学',          1, 1),
(1668573201053, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-TRADE', '国际贸易学',      2, 1),
(1668573201054, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-FISC',  '财政学',          3, 1),
(1668573201055, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-ACC',   '会计学',          4, 1),
(1668573201056, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-BA',    '工商管理',        5, 1),
(1668573201057, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-THEO',  '经济学（理论）',  6, 1),
(1668573201058, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-DIG',   '数字经济',        7, 1),
(1668573201059, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-FE',    '金融工程',        8, 1),
(1668573201060, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-INS',   '保险学',          9, 1),
(1668573201061, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-PA',   '公共管理',           1, 1),
(1668573201062, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-LOG',  '物流管理',           2, 1),
(1668573201063, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-IMS',  '信息管理与信息系统', 3, 1),
(1668573201064, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-TOUR', '旅游管理',           4, 1),
(1668573201065, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-EM',   '工程管理',           5, 1),
(1668573201066, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-HR',   '人力资源管理',       6, 1),
(1668573201067, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-MKT',  '市场营销',           7, 1),
(1668573201068, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-FIN',  '财务管理',           8, 1),
(1668573201069, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-EMER', '应急管理',           9, 1),
(1668573201070, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-THEO',  '法学理论',           1, 1),
(1668573201071, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-CIV',   '民商法学',           2, 1),
(1668573201072, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-CRIM',  '刑法学',             3, 1),
(1668573201073, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-ADMIN', '行政法学',           4, 1),
(1668573201074, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-INT',   '国际法学',           5, 1),
(1668573201075, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-ENV',   '环境与资源保护法学', 6, 1),
(1668573201076, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200006, 2, 'LIT-CN', '中国语言文学', 1, 1),
(1668573201077, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200006, 2, 'LIT-EN', '外国语言文学', 2, 1),
(1668573201078, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200006, 2, 'LIT-JC', '新闻传播学',   3, 1),
(1668573201079, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200006, 2, 'LIT-AD', '广告学',       4, 1),
(1668573201080, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200006, 2, 'LIT-ED', '编辑出版学',   5, 1),
(1668573201081, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200007, 2, 'EDU-THEO',   '教育学原理',     1, 1),
(1668573201082, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200007, 2, 'EDU-CUR',    '课程与教学论',   2, 1),
(1668573201083, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200007, 2, 'EDU-PRE',    '学前教育学',     3, 1),
(1668573201084, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200007, 2, 'EDU-HIGHER', '高等教育学',     4, 1),
(1668573201085, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200007, 2, 'EDU-VOC',    '职业技术教育学', 5, 1),
(1668573201086, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200008, 2, 'ART-VIS', '视觉传达设计', 1, 1),
(1668573201087, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200008, 2, 'ART-ENV', '环境设计',     2, 1),
(1668573201088, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200008, 2, 'ART-DIG', '数字媒体艺术', 3, 1),
(1668573201089, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573200009, 2, 'INTER-QIS', '量子信息科学', 1, 1)
ON DUPLICATE KEY UPDATE
  `parent_id`  = VALUES(`parent_id`),
  `level`      = VALUES(`level`),
  `name`       = VALUES(`name`),
  `sort_no`    = VALUES(`sort_no`),
  `is_enabled` = VALUES(`is_enabled`),
  `is_deleted` = 0,
  `update_time` = NOW();

-- 1.10.4b 播专业↔学科映射（128 行），与 种子数据.sql 段 3B 同一份清单。
--
--   ⚠️ 这一段容易漏：论文提交页「选完专业只列相关学科」全靠它。漏了不会报任何错
--      —— SubjectService.getTree(majorId) 查不到映射就会退回整棵树，表现为
--      "收窄没生效"，是那种只能靠端到端点一遍才能发现的静默失效。
--
--   major_id 用的是 种子数据.sql 段 3 改绑之后的新专业 id（1668573001000+），
--   所以本文件必须跑在 种子数据.sql 之后 —— 文件头那条顺序约定在这里也是硬要求。
--
--   冲突时只刷新 is_deleted/update_time：major_id/subject_id 是匹配键不能改；
--   管理端（阶段 6）用雪花 id 建的行不可能撞上这里的 13 位 id，所以
--   ON DUPLICATE KEY UPDATE 命中的一定是 uk_major_subject 这一侧，是预期的幂等行为。
INSERT INTO `major_subject` (id, create_by, create_time, update_by, update_time, is_deleted, delete_by, delete_time, major_id, subject_id) VALUES
(1668573300000, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001000, 1668573201001),
(1668573300001, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001001, 1668573201006),
(1668573300002, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001002, 1668573201000),
(1668573300003, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001003, 1668573201004),
(1668573300004, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001004, 1668573201002),
(1668573300005, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001005, 1668573201022),
(1668573300006, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001005, 1668573201003),
(1668573300007, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001006, 1668573201088),
(1668573300008, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001007, 1668573201003),
(1668573300009, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001008, 1668573201003),
(1668573300010, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001009, 1668573201003),
(1668573300011, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001010, 1668573201003),
(1668573300012, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001010, 1668573201038),
(1668573300013, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001011, 1668573201009),
(1668573300014, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001012, 1668573201023),
(1668573300015, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001012, 1668573201022),
(1668573300016, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001013, 1668573201008),
(1668573300017, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001013, 1668573201023),
(1668573300018, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001014, 1668573201022),
(1668573300019, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001015, 1668573201008),
(1668573300020, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001016, 1668573201008),
(1668573300021, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001017, 1668573201000),
(1668573300022, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001018, 1668573201011),
(1668573300023, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001019, 1668573201011),
(1668573300024, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001020, 1668573201011),
(1668573300025, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001021, 1668573201011),
(1668573300026, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001022, 1668573201010),
(1668573300027, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001022, 1668573201011),
(1668573300028, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001023, 1668573201011),
(1668573300029, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001023, 1668573201009),
(1668573300030, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001024, 1668573201011),
(1668573300031, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001025, 1668573201051),
(1668573300032, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001026, 1668573201051),
(1668573300033, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001027, 1668573201051),
(1668573300034, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001028, 1668573201051),
(1668573300035, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001028, 1668573201016),
(1668573300036, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001029, 1668573201051),
(1668573300037, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001030, 1668573201051),
(1668573300038, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001030, 1668573201009),
(1668573300039, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001031, 1668573201012),
(1668573300040, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001032, 1668573201012),
(1668573300041, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001033, 1668573201012),
(1668573300042, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001033, 1668573201013),
(1668573300043, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001034, 1668573201012),
(1668573300044, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001035, 1668573201012),
(1668573300045, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001036, 1668573201012),
(1668573300046, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001037, 1668573201065),
(1668573300047, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001038, 1668573201040),
(1668573300048, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001039, 1668573201040),
(1668573300049, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001039, 1668573201016),
(1668573300050, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001040, 1668573201016),
(1668573300051, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001041, 1668573201027),
(1668573300052, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001041, 1668573201016),
(1668573300053, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001042, 1668573201016),
(1668573300054, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001043, 1668573201016),
(1668573300055, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001044, 1668573201041),
(1668573300056, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001045, 1668573201041),
(1668573300057, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001045, 1668573201018),
(1668573300058, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001046, 1668573201050),
(1668573300059, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001047, 1668573201042),
(1668573300060, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001048, 1668573201018),
(1668573300061, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001049, 1668573201027),
(1668573300062, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001050, 1668573201038),
(1668573300063, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001051, 1668573201038),
(1668573300064, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001052, 1668573201038),
(1668573300065, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001052, 1668573201004),
(1668573300066, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001053, 1668573201049),
(1668573300067, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001054, 1668573201049),
(1668573300068, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001055, 1668573201039),
(1668573300069, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001056, 1668573201039),
(1668573300070, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001057, 1668573201039),
(1668573300071, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001058, 1668573201023),
(1668573300072, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001058, 1668573201039),
(1668573300073, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001059, 1668573201057),
(1668573300074, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001060, 1668573201052),
(1668573300075, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001061, 1668573201059),
(1668573300076, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001062, 1668573201053),
(1668573300077, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001063, 1668573201063),
(1668573300078, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001064, 1668573201004),
(1668573300079, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001064, 1668573201063),
(1668573300080, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001065, 1668573201056),
(1668573300081, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001066, 1668573201055),
(1668573300082, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001067, 1668573201068),
(1668573300083, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001068, 1668573201066),
(1668573300084, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001069, 1668573200005),
(1668573300085, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001070, 1668573201071),
(1668573300086, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001071, 1668573201074),
(1668573300087, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001071, 1668573201053),
(1668573300088, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001072, 1668573201073),
(1668573300089, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001072, 1668573201072),
(1668573300090, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001073, 1668573201077),
(1668573300091, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001074, 1668573201077),
(1668573300092, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001075, 1668573201077),
(1668573300093, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001076, 1668573201077),
(1668573300094, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001077, 1668573201077),
(1668573300095, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001078, 1668573201077),
(1668573300096, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001080, 1668573201076),
(1668573300097, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001081, 1668573201076),
(1668573300098, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001082, 1668573201076),
(1668573300099, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001084, 1668573201078),
(1668573300100, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001085, 1668573201078),
(1668573300101, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001086, 1668573201079),
(1668573300102, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001087, 1668573201078),
(1668573300103, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001088, 1668573201078),
(1668573300104, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001088, 1668573201077),
(1668573300105, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001089, 1668573200008),
(1668573300106, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001090, 1668573201088),
(1668573300107, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001091, 1668573200008),
(1668573300108, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001092, 1668573201086),
(1668573300109, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001093, 1668573201087),
(1668573300110, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001094, 1668573201086),
(1668573300111, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001095, 1668573201088),
(1668573300112, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001096, 1668573200001),
(1668573300113, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001097, 1668573201024),
(1668573300114, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001098, 1668573201025),
(1668573300115, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001099, 1668573201033),
(1668573300116, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001100, 1668573201027),
(1668573300117, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001101, 1668573201028),
(1668573300118, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001102, 1668573201032),
(1668573300119, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001103, 1668573201029),
(1668573300120, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001104, 1668573201044),
(1668573300121, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001105, 1668573201044),
(1668573300122, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001106, 1668573201048),
(1668573300123, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001107, 1668573201015),
(1668573300124, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001108, 1668573201015),
(1668573300125, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001108, 1668573201042),
(1668573300126, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001109, 1668573201042),
(1668573300127, 1, NOW(), 1, NOW(), 0, NULL, NULL, 1668573001109, 1668573201015)
ON DUPLICATE KEY UPDATE
  `is_deleted`  = 0,
  `update_time` = NOW();

-- 1.10.5 回填 paper_info.subject_id（只补 NULL 行，可重复执行）
--        subject_code 存的是 code，join 上就得到 id。本机 19 篇的 9 个不同 code
--        （SCI/MED/ECON/ENG/MGT/INTER/ART + MGT-IMS/MED-DENT）都在 1.10.4 里，
--        所以 5.7 的 paper_code_unmapped_should_be_0 期望 0。
UPDATE `paper_info` p
  JOIN `subject` s ON TRIM(p.subject_code) = s.code
   SET p.subject_id = s.id,
       p.update_time = NOW()
 WHERE p.subject_id IS NULL
   AND p.subject_code IS NOT NULL
   AND TRIM(p.subject_code) <> '';

-- 1.10.6 教师研究方向 → teacher_subject（只迁能同名映射的 2 个 token）
--
--     旧值是前端硬编码的 7 个短码：ai / machine_learning / data_mining /
--     computer_network / software_engineering / database / information_security。
--     逐条比对 ENG 下的 24 个二级学科名后，只有 ai→ENG-AI、software_engineering→ENG-SE
--     能同名对上；其余 5 个（机器学习/数据挖掘/计算机网络/数据库/信息安全）在学科目录里
--     没有同名项，**原样保留不动** —— 显示与迁移前完全一致，零回归，教师下次进设置页重选即可。
--
--     ⚠️ 必须限定 user_type='TEACHER'：sys_user.research_direction 学生也在用
--        （StudentProfileServiceImpl 把学生「研究兴趣」写进同一列），
--        不加这个条件会把学生的自由文本也当成 token 处理。
--     ⚠️ FIND_IN_SET 做的是逗号分词的精确匹配（不是子串），所以短 token 'ai'
--        不会误伤 machine_learning / data_mining 等。REPLACE(...,' ','') 是容忍
--        "ai, machine_learning" 这种逗号后带空格的写法。
--
--     幂等：uk_teacher_subject 已存在则只刷新 update_time，不会插重复行。
--     id 用 UUID_SHORT()：这些 id 从不返回给前端（设置页回显的是 subject.code），
--     所以不必是雪花 ID，只要有唯一性即可。
--
--     先备份 research_direction 原值。这里用 CREATE TABLE IF NOT EXISTS 而不是种子脚本
--     的 DROP + CREATE：本文件是一次性脚本，重跑时**必须保留第一次跑时的快照**，
--     DROP 会把已经改过的值当成"原值"重新拍一遍，快照就失去意义了。
CREATE TABLE IF NOT EXISTS bak_sys_user_research_direction_20261008 AS
SELECT id, user_type, research_direction
  FROM `sys_user`
 WHERE user_type = 'TEACHER' AND research_direction IS NOT NULL;

INSERT INTO `teacher_subject` (id, create_by, create_time, update_by, update_time, is_deleted, delete_by, delete_time, user_id, subject_id)
SELECT UUID_SHORT(), u.id, NOW(), u.id, NOW(), 0, NULL, NULL, u.id,
       (SELECT s.id FROM `subject` s WHERE s.code = 'ENG-AI')
  FROM `sys_user` u
 WHERE u.is_deleted = 0
   AND u.user_type = 'TEACHER'
   AND u.research_direction IS NOT NULL
   AND FIND_IN_SET('ai', REPLACE(u.research_direction, ' ', ''))
ON DUPLICATE KEY UPDATE `update_time` = NOW();

INSERT INTO `teacher_subject` (id, create_by, create_time, update_by, update_time, is_deleted, delete_by, delete_time, user_id, subject_id)
SELECT UUID_SHORT(), u.id, NOW(), u.id, NOW(), 0, NULL, NULL, u.id,
       (SELECT s.id FROM `subject` s WHERE s.code = 'ENG-SE')
  FROM `sys_user` u
 WHERE u.is_deleted = 0
   AND u.user_type = 'TEACHER'
   AND u.research_direction IS NOT NULL
   AND FIND_IN_SET('software_engineering', REPLACE(u.research_direction, ' ', ''))
ON DUPLICATE KEY UPDATE `update_time` = NOW();

-- 1.10.7 把上面这 2 个 token 在有 teacher_subject 行的用户身上改成学科中文名
--        （research_direction 降级为「学科中文名 CSV 缓存」，三个展示点 + 两处关键词
--        匹配不改一行代码，显示还从 ai 变成了「人工智能」）。
--        逗号包裹再替换：只认整 token，且顺手把逗号后的空格规整掉。
--        未映射的 5 个 token 原样留在串里。
UPDATE `sys_user`
   SET `research_direction` = TRIM(BOTH ',' FROM
         REPLACE(REPLACE(CONCAT(',', REPLACE(`research_direction`, ' ', ''), ','),
                         ',software_engineering,', ',软件工程,'),
                 ',ai,', ',人工智能,'))
 WHERE `is_deleted` = 0
   AND `user_type` = 'TEACHER'
   AND `research_direction` IS NOT NULL
   AND (FIND_IN_SET('ai', REPLACE(`research_direction`, ' ', ''))
     OR FIND_IN_SET('software_engineering', REPLACE(`research_direction`, ' ', '')));


-- ---------------------------------------------------------------------
-- 1.11 category_similarity_threshold 增加论文类型维度
--
-- 背景：论文类型（graduation/course/other）从 DTO 上的裸字符串升级成
--   common/enums/PaperTypeEnum，并接入阈值解析：每条配置行可以只对某一种论文类型
--   生效，解析顺序变为
--     学科+类型 > 学科 > 父门类+类型 > 父门类
--       > 专业+类型 > 专业 > 学院+类型 > 学院 > 仅类型 > 全局
--
-- 关键：本列 DEFAULT NULL，而 NULL 的语义是「适用全部类型」——
--   这正是本列存在之前**全部存量行**的形态。所以这一段执行完，
--   一条既有配置的生效范围都不会变，解析结果逐字节不变。
--   （回滚护栏：旧版后端不认这一列，列留着无害。）
--
-- ⚠️ 与 1.10.3 同理，这张表没有唯一键，去重靠 Java 侧
--   SimilarityThresholdServiceImpl.validate 的 naturalKey —— 该 key 已同步拼上
--   paperType（并把 null 与 '' 折叠成同一个值），否则"仅类型"档的两种写法会各存一行。
--
-- 幂等性：重复执行报 1060 / 1061，属预期，跳过即可。UPDATE 本身可重复执行。
-- ---------------------------------------------------------------------

-- 两条 ALTER 必须分开写。合并成一条的话，已执行过的库里 ADD COLUMN 报 1060
-- 会把同一条语句里的 ADD KEY 一起压掉，索引就永远建不上。
ALTER TABLE `category_similarity_threshold`
  ADD COLUMN `paper_type` varchar(50) DEFAULT NULL COMMENT '论文类型维度（NULL=适用全部类型；取值见 PaperTypeEnum）' AFTER `subject_id`;

ALTER TABLE `category_similarity_threshold`
  ADD INDEX `idx_type_paper_type` (`category_type`,`paper_type`);

-- 顺手把 category_type 的注释补上新档位
ALTER TABLE `category_similarity_threshold`
  MODIFY COLUMN `category_type` varchar(50) DEFAULT NULL COMMENT '分类类型：college-学院，major-专业，subject-学科领域，paper_type-论文类型（仅按类型的一档）';

-- 空串归一成 NULL。解析器查「不带类型」的行时用的是 IS NULL，
-- 一行写成 '' 就会既匹配不到通用档、又不会在任何带类型的查询里命中，成为永远失效的垃圾行。
UPDATE `category_similarity_threshold`
   SET `paper_type` = NULL
 WHERE `paper_type` = '';

-- 存量论文里可能出现不在 PaperTypeEnum 取值域内的 paper_type（已知历史上有过
-- THESIS/JOURNAL/CONFERENCE 这套幽灵值）。**这里不做任何 UPDATE**：
-- 从 'THESIS' 映射到哪个规范值纯属臆测，改错就是无声篡改业务数据。
-- 读取侧（实体保持 String、解析器未命中原样回退）对非法值天然容错，
-- 下面这条只是把现状摆出来供人工判断：
SELECT paper_type, COUNT(*) AS n
  FROM paper_info
 WHERE is_deleted = 0
 GROUP BY paper_type
 ORDER BY n DESC;


-- ============================================================================
-- 段 1.12：学生分组两张表（教师端「学生分组管理」页面）
--   CREATE TABLE IF NOT EXISTS，可重复执行。
--   student_group_member 是物理删除（无 is_deleted）：(group_id, student_id)
--   复合唯一键若叠加软删，移出后重新加入会撞唯一键。
--
--   ⚠️ 若库里已存在旧结构的 student_group（如缺 teacher_id 列，报
--      Unknown column 'teacher_id' in 'field list'），CREATE TABLE IF NOT EXISTS
--      会静默跳过、旧表原样保留。该功能首次上线前任何同名表都不可能有有效数据，
--      直接先 DROP 再执行下面的建表语句：
--        DROP TABLE IF EXISTS `student_group_member`;
--        DROP TABLE IF EXISTS `student_group`;
-- ============================================================================

CREATE TABLE IF NOT EXISTS `student_group` (
  `id` bigint NOT NULL COMMENT '分组ID（雪花ID，由业务代码生成，非自增）',
  `teacher_id` bigint NOT NULL COMMENT '所属教师ID（关联sys_user.id）',
  `name` varchar(50) NOT NULL COMMENT '分组名称',
  `description` varchar(200) DEFAULT NULL COMMENT '分组描述',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_student_group_teacher` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生分组表';

CREATE TABLE IF NOT EXISTS `student_group_member` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `group_id` bigint NOT NULL COMMENT '分组ID（关联student_group.id）',
  `student_id` bigint NOT NULL COMMENT '学生ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_student` (`group_id`,`student_id`),
  KEY `idx_group_member_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生分组成员表（物理删除）';

-- ============================================================================
-- 段 1.13：sys_role.role_code 去重 + 唯一索引（P0 权限守护栏配套）
--
-- 背景：role_code 是整条鉴权链的锚 —— UserDetailsServiceImpl 的超管 ADMIN 注入、
--   SecurityConfig 的 hasAuthority、UserBusinessInfoUtils.isAdmin() 全按它匹配。
--   init.sql 的列注释写「唯一」但从未建索引，而 种子数据.sql 段 11 只按主键幂等
--   （其注释明确警告过「同 code 不同 id 会插出重复角色」）。重复编码一旦出现，
--   所有按 code 反查角色的代码路径行为都不可预测。
--
-- 顺序：1.13.1 去重 → 1.13.2 已软删行改码 → 1.13.3 建索引。三条都要跑，
--   直接建索引会被重复行/已删行撞 1062。
--
-- ⚠️ 与代码的约定：新版 AdminPermissionServiceImpl.deleteRole 软删自定义角色时
--   会同步把编码改成 <code>_del_<id>，腾出原编码供同名新角色使用（否则唯一索引
--   会让"删了再建同名"永远 1062）。本段 1.13.2 负责用同样思路清历史已删行。
--
-- 可重复执行：1.13.1/1.13.2 天然幂等（保留行判定 + _del_ 前缀判断），
--   1.13.3 重复执行报 1061，--force 下是预期的「已执行过」信号。
-- ============================================================================

-- 1.13.0 先看清现状：重复编码、以及还占着干净编码的已删行（动手前留档对照）
SELECT role_code, COUNT(*) AS cnt,
       SUM(CASE WHEN is_deleted = 0 THEN 1 ELSE 0 END) AS live_cnt
  FROM sys_role
 GROUP BY role_code
HAVING cnt > 1 OR (cnt = 1 AND live_cnt = 0)
 ORDER BY cnt DESC;

-- 1.13.1 去重：同一 role_code 保留一行，其余行软删，并把引用它们的用户
--   （含挂在已删重复行上的悬空用户）改挂到保留行。
--   保留行 = 未删行里 id 最小的一个（init.sql 的 1001/2001/4001 与
--   种子数据.sql 段 11 的 SUPER_ADMIN 都是最小 id 先建）；全删时才退而取最小 id。
CREATE TEMPORARY TABLE tmp_role_keep AS
SELECT role_code,
       COALESCE(MIN(CASE WHEN is_deleted = 0 THEN id END), MIN(id)) AS keep_id
  FROM sys_role
 GROUP BY role_code;

UPDATE sys_user u
  JOIN sys_role dup ON dup.id = u.role_id
  JOIN tmp_role_keep k ON k.role_code = dup.role_code
   SET u.role_id = k.keep_id
 WHERE dup.id <> k.keep_id;

-- 软删非保留行（只动未删行；已删行保持原状，交 1.13.2 改名）
UPDATE sys_role r
  JOIN tmp_role_keep k ON k.role_code = r.role_code
   SET r.is_deleted = 1,
       r.delete_time = NOW()
 WHERE r.id <> k.keep_id
   AND r.is_deleted = 0;

DROP TEMPORARY TABLE tmp_role_keep;

-- 1.13.2 已软删行改码腾位：<code>_del_<id>。没有这一步，唯一索引会让已删行
--   永远占住编码。LOCATE 守卫保证重复执行不二次追加；超长时截断基名防 1406。
UPDATE sys_role
   SET role_code = IF(CHAR_LENGTH(role_code) + CHAR_LENGTH(CONCAT('_del_', id)) > 50,
                      CONCAT(LEFT(role_code, 50 - CHAR_LENGTH(CONCAT('_del_', id))), '_del_', id),
                      CONCAT(role_code, '_del_', id))
 WHERE is_deleted = 1
   AND LOCATE('_del_', role_code) = 0;

-- 1.13.3 唯一索引（init.sql 的建表语句已同步声明，新库无需本条）
ALTER TABLE `sys_role` ADD UNIQUE INDEX `uk_role_code` (`role_code`);

-- ============================================================================
-- 段 1.14：system_metric_minute 系统资源指标分钟表（SystemMonitoring 性能趋势图配套）
--
-- 背景：/api/admin/monitoring/performance 的 1h/24h/7d 趋势数据由
--   ResourceMetricsService 每分钟采样写入本表。旧版后端用 Random 伪造历史曲线，
--   新版改为真实采样落库，故本表是新版趋势图的唯一数据源。
--   DDL 与 init.sql 逐字一致；CREATE TABLE IF NOT EXISTS，可重复执行。
--   部署顺序：先跑本段，再部署新 jar。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `system_metric_minute` (
  `id`             bigint   NOT NULL                                    COMMENT '主键ID（雪花ID，由业务代码生成，非自增）',
  `metric_time`    datetime NOT NULL                                    COMMENT '指标时间（分钟精度，秒置零）',
  `cpu_usage`      double   DEFAULT NULL                                COMMENT '整机CPU使用率%',
  `jvm_cpu_usage`  double   DEFAULT NULL                                COMMENT 'JVM进程CPU使用率%',
  `memory_usage`   double   DEFAULT NULL                                COMMENT '整机物理内存使用率%',
  `jvm_heap_usage` double   DEFAULT NULL                                COMMENT 'JVM堆内存使用率%',
  `disk_usage`     double   DEFAULT NULL                                COMMENT '磁盘使用率%（jar所在分区）',
  `db_pool_usage`  double   DEFAULT NULL                                COMMENT '数据库连接池使用率%',
  `db_active_conn` int      DEFAULT NULL                                COMMENT '数据库活跃连接数',
  `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
  `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`     tinyint  NOT NULL DEFAULT 0                          COMMENT '软删除标记(0-未删除,1-已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metric_time` (`metric_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统资源指标分钟表';

-- ============================================================================
-- 段 1.15：sys_user 个人设置「通知开关 + 个人偏好」6 列
--
-- 背景：管理员端个人设置页（UserSettings.vue）的「通知设置」（系统异常/安全警告/
--   系统公告/邮件通知 4 个开关）与「系统偏好」（操作日志保留天数、数据备份频率）
--   此前是纯前端 UI——保存时跟着 PUT /api/admin/users/{userId} 一起提交，但
--   UserUpdateReq 没有对应字段，被 Jackson 静默丢弃，刷新后全部回到默认值。
--   新版后端把这 6 个字段落到 sys_user（每用户一份，跟随本人资料走
--   updateUser 的本人自助更新路径）。
--   列全部 NOT NULL 带默认值：存量行由 ALTER 自动回填默认值，无需额外 UPDATE。
--   ⚠️ 必须是「先跑本段，再部署后端」：SysUser 实体一旦带上这些字段，
--   MyBatis-Plus 生成的每一条 SELECT 都会引用它们，缺列即全站 500（同 1.11 教训）。
-- ============================================================================

ALTER TABLE `sys_user`
  ADD COLUMN `notify_system_error`        tinyint     NOT NULL DEFAULT '1'     COMMENT '通知开关-系统异常：0-关，1-开（管理员个人设置页）',
  ADD COLUMN `notify_security_warning`    tinyint     NOT NULL DEFAULT '1'     COMMENT '通知开关-安全警告：0-关，1-开（管理员个人设置页）',
  ADD COLUMN `notify_system_announcement` tinyint     NOT NULL DEFAULT '1'     COMMENT '通知开关-系统公告：0-关，1-开',
  ADD COLUMN `notify_email`               tinyint     NOT NULL DEFAULT '1'     COMMENT '通知开关-邮件通知：0-关，1-开',
  ADD COLUMN `log_retention_days`         int         NOT NULL DEFAULT '90'    COMMENT '个人偏好-操作日志保留天数（30~365）',
  ADD COLUMN `backup_frequency`           varchar(20) NOT NULL DEFAULT 'daily' COMMENT '个人偏好-数据备份频率：daily/weekly/monthly';

-- ============================================================================
-- 段 1.16：论文全生命周期 P1~P6（阶段门 / 评阅 / 答辩 / 引擎底座 / 导师评价）
--
-- 背景：P1~P6 各期曾各有一份「补论文全生命周期P*.sql」，2026-10 归并进本段，
--   原散文件已删。空库由 init.sql 一次建好，无需本段；本段是给
--   「结构落后于 P1~P6 代码」的存量库补差量用的，全段幂等、可重复执行。
--
-- ⚠️ 部署顺序（硬约束）：**先跑完本段，再部署新 jar**。新版实体
--   （GuidanceRecord / ReviewAssignment / ReviewWorkflowProgress /
--   DefenseBatch / DefenseGroupMember / DefenseRecord / CheckSentenceHit /
--   PaperCheckSignoff / CheckPolicy / CheckCollegeQuota / AdvisorEvaluation，
--   及 CheckTask / CheckResult / CheckReport / PaperInfo / SystemParam 新列）
--   全部引用本段的表与列，漏跑即对应接口全站 500（同 1.11 教训）。
--
-- 子段：1.16.1 建表（11 张，CREATE TABLE IF NOT EXISTS）
--       1.16.2 存量表扩列（幂等 ALTER，information_schema + 预处理语句范式）
--       1.16.3 字典种子（70xx 论文状态 / 71xx 评阅 / 72xx 答辩 / 73xx 学位 /
--              74xx 导师评价标签；与 种子数据.sql 段 10 同一份清单，
--              INSERT IGNORE 两边重跑互不干扰）
-- 设计要点见 docs/论文全生命周期落地方案.md（P1~P6 分期）。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1.16.1 建表（11 张，按 P1→P6 顺序）
-- ----------------------------------------------------------------------------

-- 指导记录表（P1：教师发起 → 学生确认，多轮次）
CREATE TABLE IF NOT EXISTS `guidance_record` (
  `id`               bigint       NOT NULL COMMENT '主键ID（雪花）',
  `paper_id`         bigint       NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `student_id`       bigint       NOT NULL COMMENT '学生ID（关联sys_user.id）',
  `teacher_id`       bigint       NOT NULL COMMENT '指导教师ID（关联sys_user.id）',
  `round`            int          NOT NULL COMMENT '指导轮次（同论文内自增，(paper_id,round)唯一）',
  `guidance_type`    varchar(16)  NOT NULL DEFAULT 'online' COMMENT '指导方式：online-线上 / offline-线下',
  `guidance_time`    datetime     NOT NULL COMMENT '指导时间',
  `content`          text         COMMENT '指导内容（纯文本，入库前已做XSS清洗）',
  `student_feedback` text         COMMENT '学生反馈（确认时填写，可空）',
  `attachment_id`    bigint       DEFAULT NULL COMMENT '附件ID（关联file_info.id，可空）',
  `confirm_status`   tinyint      NOT NULL DEFAULT '0' COMMENT '确认状态：0-待学生确认 / 1-已确认 / 2-有异议',
  `confirm_time`     datetime     DEFAULT NULL COMMENT '学生确认时间',
  `create_by`        bigint       DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`      datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`        bigint       DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`      datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`       tinyint      DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`        bigint       DEFAULT NULL COMMENT '删除人ID',
  `delete_time`      datetime     DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_round` (`paper_id`, `round`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='指导记录表（论文全生命周期P1）';

-- 评阅任务表（P2：1~N 名教师评阅，线上直接评 / 线下回填）
CREATE TABLE IF NOT EXISTS `review_assignment` (
  `id`             bigint        NOT NULL COMMENT '主键ID（雪花）',
  `paper_id`       bigint        NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `submit_version` int           NOT NULL DEFAULT '1' COMMENT '评阅针对的论文版本号（paper_submit.submit_version，版本演进后历史评阅不失效）',
  `reviewer_id`    bigint        NOT NULL COMMENT '评阅教师ID（关联sys_user.id）',
  `review_type`    varchar(16)   NOT NULL DEFAULT 'peer' COMMENT '评阅类型：advisor-导师评阅 / peer-交叉评阅 / blind-盲审（查询层脱敏）',
  `method`         varchar(16)   NOT NULL DEFAULT 'online' COMMENT '评阅方式：online-线上 / offline-线下（结果回填）',
  `status`         tinyint       NOT NULL DEFAULT '0' COMMENT '状态：0-待评阅 / 1-评阅中 / 2-已提交 / 3-已超时转派',
  `score`          decimal(5,2)  DEFAULT NULL COMMENT '评分（0-100，线下任务可提交时回填）',
  `comment`        text          COMMENT '评阅意见（线下任务可为空，以扫描件为准）',
  `attachment_id`  bigint        DEFAULT NULL COMMENT '线下评阅表扫描件（关联file_info.id，可空）',
  `deadline`       datetime      DEFAULT NULL COMMENT '评阅截止时间（超时扫描挂定时任务）',
  `assign_type`    varchar(16)   NOT NULL DEFAULT 'manual' COMMENT '分配方式：auto-自动 / manual-手动',
  `create_by`      bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`    datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`     tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`      bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`    datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_version_reviewer` (`paper_id`, `submit_version`, `reviewer_id`),
  KEY `idx_reviewer_status` (`reviewer_id`, `status`),
  KEY `idx_paper_id` (`paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评阅任务表（论文全生命周期P2）';

-- 多级初审投票表（P2：ReviewWorkflow 配置壳的真实驱动留痕；all/any 语义由 Service 裁决）
CREATE TABLE IF NOT EXISTS `review_workflow_progress` (
  `id`          bigint    NOT NULL COMMENT '主键ID（雪花）',
  `paper_id`    bigint    NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `step_index`  int       NOT NULL COMMENT '步骤序号（1-based，对应 review_workflow.steps 数组下标+1）',
  `approver_id` bigint    NOT NULL COMMENT '审批人ID（关联sys_user.id）',
  `vote_result` tinyint   NOT NULL COMMENT '投票结果：1-通过 / 2-驳回',
  `opinion`     text      COMMENT '审批意见（可空）',
  `vote_time`   datetime  NOT NULL COMMENT '投票时间',
  `create_by`   bigint    DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime  DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint    DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  tinyint   DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`   bigint    DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime  DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_step_approver` (`paper_id`, `step_index`, `approver_id`),
  KEY `idx_paper_step` (`paper_id`, `step_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多级初审投票表（论文全生命周期P2）';

-- 答辩批次表（P3：中期/终期共用，batch_type 区分；mode 区分线上/线下）
CREATE TABLE IF NOT EXISTS `defense_batch` (
  `id`            bigint        NOT NULL COMMENT '主键ID（雪花）',
  `batch_no`      varchar(32)   NOT NULL COMMENT '批次编号（人工或系统生成，唯一）',
  `batch_type`    varchar(16)   NOT NULL DEFAULT 'final' COMMENT '批次类型：mid_term-中期答辩 / final-终期答辩',
  `college_id`    bigint        DEFAULT NULL COMMENT '学院ID（关联college.id，可空表示全校批次）',
  `defense_date`  datetime      NOT NULL COMMENT '答辩时间',
  `location`      varchar(128)  DEFAULT NULL COMMENT '地点：线下填教室、线上填会议链接',
  `mode`          varchar(16)   NOT NULL DEFAULT 'offline' COMMENT '答辩方式：online-线上 / offline-线下',
  `leader_id`     bigint        DEFAULT NULL COMMENT '答辩组长ID（关联sys_user.id，组长主持并汇总结论）',
  `secretary_id`  bigint        DEFAULT NULL COMMENT '答辩秘书ID（关联sys_user.id，负责记录与线下回填）',
  `status`        tinyint       NOT NULL DEFAULT '0' COMMENT '批次状态：0-筹备中 / 1-进行中 / 2-已结束',
  `remark`        varchar(255)  DEFAULT NULL COMMENT '备注（可空）',
  `create_by`     bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`   datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`     bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`    tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`     bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`   datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_no` (`batch_no`),
  KEY `idx_batch_type_date` (`batch_type`, `defense_date`),
  KEY `idx_college_status` (`college_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答辩批次表（论文全生命周期P3，中期/终期共用）';

-- 答辩组成员表（P3：1 名组长 leader + N 名成员 member；秘书在 defense_batch.secretary_id）
CREATE TABLE IF NOT EXISTS `defense_group_member` (
  `id`          bigint       NOT NULL COMMENT '主键ID（雪花）',
  `batch_id`    bigint       NOT NULL COMMENT '批次ID（关联defense_batch.id）',
  `teacher_id`  bigint       NOT NULL COMMENT '教师ID（关联sys_user.id）',
  `group_role`  varchar(16)  NOT NULL DEFAULT 'member' COMMENT '组角色：leader-组长 / member-成员',
  `create_by`   bigint       DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint       DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  tinyint      DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`   bigint       DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime     DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_teacher` (`batch_id`, `teacher_id`),
  KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答辩组成员表（论文全生命周期P3）';

-- 答辩记录表（P3：每篇论文在每个批次一条，(batch_id, paper_id) 唯一；
--   终期 pass 时由 Service 回写 paper_info.final_score 并推进 defense_passed）
CREATE TABLE IF NOT EXISTS `defense_record` (
  `id`             bigint        NOT NULL COMMENT '主键ID（雪花）',
  `batch_id`       bigint        NOT NULL COMMENT '批次ID（关联defense_batch.id）',
  `paper_id`       bigint        NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `student_id`     bigint        NOT NULL COMMENT '学生ID（关联sys_user.id）',
  `defense_order`  int           DEFAULT NULL COMMENT '答辩顺序号（同批次内人工编排，可空）',
  `score`          decimal(5,2)  DEFAULT NULL COMMENT '综合得分（0-100，组长汇总后写入）',
  `grade`          varchar(8)    DEFAULT NULL COMMENT '等级：优/良/中/及格/不及格',
  `result`         varchar(16)   DEFAULT NULL COMMENT '结论：pass-通过 / fail-不通过 / postpone-缓辩',
  `comment`        text          COMMENT '答辩记录（纯文本，入库前已做XSS清洗）',
  `attachment_id`   bigint        DEFAULT NULL COMMENT '线下答辩表扫描件（关联file_info.id，可空）',
  `submit_by`      bigint        DEFAULT NULL COMMENT '结论提交人ID（通常为组长，关联sys_user.id）',
  `submit_time`    datetime      DEFAULT NULL COMMENT '结论提交时间',
  `create_by`      bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`    datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`     tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`      bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`    datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_paper` (`batch_id`, `paper_id`),
  KEY `idx_paper_id` (`paper_id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='答辩记录表（论文全生命周期P3）';

-- 句级标红明细表（P5：命中句（句相似度 ≥ check.sentence.threshold，默认 50%）逐条落库；
--   学生端检测明细页与来源聚合列表的数据源）
CREATE TABLE IF NOT EXISTS `check_sentence_hit` (
  `id`              bigint        NOT NULL COMMENT '主键ID（雪花）',
  `task_id`         bigint        NOT NULL COMMENT '查重任务ID（关联check_task.id）',
  `sentence_index`  int           NOT NULL COMMENT '句子序号（正文剔除参考文献区块后，从0开始）',
  `sentence_text`   varchar(1000) NOT NULL COMMENT '句子文本（超1000字符截断）',
  `start_offset`    int           DEFAULT NULL COMMENT '句子在正文中的起始偏移',
  `similarity`      decimal(5,2)  NOT NULL COMMENT '句相似度（如82.35表示82.35%）',
  `is_citation`     tinyint       NOT NULL DEFAULT '0' COMMENT '是否引用句：1-句尾带[n]引注且文档存在参考文献区块（引用命中不计入总相似比）/ 0-普通命中',
  `hit_paper_id`    bigint        DEFAULT NULL COMMENT '命中来源论文ID（关联paper_info.id）',
  `hit_paper_title` varchar(200)  DEFAULT NULL COMMENT '命中来源论文标题（冗余，明细页直接展示）',
  `create_by`       bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`       bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`      tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`       bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`     datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_csh_task` (`task_id`),
  KEY `idx_csh_hit_paper` (`hit_paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='句级标红明细表（论文全生命周期P5）';

-- 查重报告导师签核表（P5：评阅分配前必须存在 qualified=1 记录；
--   唯一键 (paper_id, task_id)：论文重新提交再检产生新任务后可再次签核）
CREATE TABLE IF NOT EXISTS `paper_check_signoff` (
  `id`             bigint        NOT NULL COMMENT '主键ID（雪花）',
  `paper_id`       bigint        NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `task_id`        bigint        NOT NULL COMMENT '查重任务ID（关联check_task.id，签核对象）',
  `report_id`      bigint        DEFAULT NULL COMMENT '查重报告ID（关联check_report.id，可空）',
  `advisor_id`     bigint        NOT NULL COMMENT '签核导师ID（关联sys_user.id，必须是论文teacher_id本人）',
  `qualified`      tinyint       NOT NULL COMMENT '签核结论：1-合格放行 / 0-不合格退回修改',
  `guidance_score` decimal(5,2)  DEFAULT NULL COMMENT '指导评分（0-100，成绩加权合成的指导分数据源）',
  `comment`        varchar(500)  DEFAULT NULL COMMENT '签核意见',
  `sign_time`      datetime      NOT NULL COMMENT '签核时间',
  `create_by`      bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`    datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`     tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`      bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`    datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_task` (`paper_id`, `task_id`),
  KEY `idx_pcs_advisor` (`advisor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='查重报告导师签核表（论文全生命周期P5）';

-- 学位检测策略集（P5：超管端 CRUD，CheckPolicyResolver 按 degree_type 命中，
--   未命中回落 SimilarityThresholdResolver 既有链路）
CREATE TABLE IF NOT EXISTS `check_policy` (
  `id`             bigint        NOT NULL COMMENT '主键ID（雪花）',
  `degree_type`    varchar(20)   NOT NULL COMMENT '学位类型：undergraduate-本科 / master-硕士 / doctor-博士（唯一）',
  `threshold`      decimal(5,2)  NOT NULL COMMENT '合格阈值（总相似比百分比，如20.00表示20%）',
  `max_per_source` decimal(5,2)  DEFAULT NULL COMMENT '单篇最大相似比上限（百分比，可空=不启用该项校验）',
  `max_check_count` int          DEFAULT NULL COMMENT '每篇论文最多查重次数（可空=沿用全局 admin.check-rule.default-max-count）',
  `check_interval` int           DEFAULT NULL COMMENT '两次查重间隔秒数（可空=沿用全局 admin.check-rule.default-interval）',
  `enabled`        tinyint       NOT NULL DEFAULT '1' COMMENT '是否启用：1-启用 / 0-停用',
  `create_by`      bigint        DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`    datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`      bigint        DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`     tinyint       DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`      bigint        DEFAULT NULL COMMENT '删除人ID',
  `delete_time`    datetime      DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_degree_type` (`degree_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学位检测策略集（论文全生命周期P5，超管维护）';

-- 学院月度查重额度表（P5：查重任务创建时原子扣减；
--   唯一键 (college_id, quota_month)，跨月自然重置无需清零任务）
CREATE TABLE IF NOT EXISTS `check_college_quota` (
  `id`          bigint   NOT NULL COMMENT '主键ID（雪花）',
  `college_id`  bigint   NOT NULL COMMENT '学院ID（关联college.id）',
  `quota_month` char(7)  NOT NULL COMMENT '额度月份（yyyy-MM，如2026-10）',
  `total_quota` int      NOT NULL COMMENT '当月总额度（次）',
  `used_quota`  int      NOT NULL DEFAULT '0' COMMENT '当月已用额度（次）',
  `create_by`   bigint   DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`   bigint   DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`  tinyint  DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by`   bigint   DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_college_month` (`college_id`, `quota_month`),
  KEY `idx_ccq_month` (`quota_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学院月度查重额度表（论文全生命周期P5）';

-- 导师评价表（P6：uk_paper_id 每篇论文一次、提交后不可改；
--   教师端 API 只做聚合查询、永不返回单条明细，样本数 <3 时返回掩码）
CREATE TABLE IF NOT EXISTS `advisor_evaluation` (
  `id`                 bigint       NOT NULL COMMENT '主键ID（雪花）',
  `paper_id`           bigint       NOT NULL COMMENT '论文ID（关联paper_info.id）',
  `student_id`         bigint       NOT NULL COMMENT '评价学生ID（关联sys_user.id；仅管理端实名可见，教师端永不返回）',
  `teacher_id`         bigint       NOT NULL COMMENT '被评价导师ID（关联sys_user.id，提交时取 paper_info.teacher_id 快照）',
  `score_timeliness`   tinyint      NOT NULL COMMENT '指导及时性（1-5星）',
  `score_quality`      tinyint      NOT NULL COMMENT '反馈质量（1-5星）',
  `score_attitude`     tinyint      NOT NULL COMMENT '责任心态度（1-5星）',
  `score_professional` tinyint      NOT NULL COMMENT '专业能力（1-5星）',
  `score_overall`      tinyint      NOT NULL COMMENT '总体满意度（1-5星；导师评价分=avg/5×100）',
  `tags`               varchar(500) DEFAULT NULL COMMENT '预设标签（JSON数组，元素为字典 advisor_eval_tag 的 dict_value）',
  `comment`            varchar(500) DEFAULT NULL COMMENT '文字评语（选填，jsoup 纯文本清洗后落库）',
  `create_by`          bigint       DEFAULT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time`        datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by`          bigint       DEFAULT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time`        datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`         tinyint      DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_id` (`paper_id`),
  KEY `idx_teacher` (`teacher_id`, `is_deleted`),
  KEY `idx_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导师评价表（论文全生命周期P6a：学生对指导教师评价，教师端仅见聚合）';

-- ----------------------------------------------------------------------------
-- 1.16.2 存量表扩列（幂等 ALTER：MySQL 不支持 ADD COLUMN IF NOT EXISTS，
--   用 information_schema + 预处理语句；拼接统一用相邻字符串常量，
--   不依赖 sql_mode 的 PIPES_AS_CONCAT）
-- ----------------------------------------------------------------------------

-- paper_info 加当前多级初审步骤列（P2）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'paper_info'
     AND column_name = 'workflow_step'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `paper_info` ADD COLUMN `workflow_step` int NOT NULL DEFAULT 0 COMMENT ''当前多级初审步骤：0-未进入工作流 / 1..N-第N步审批中（review_workflow.steps 顺序）'' AFTER `paper_status`',
  'SELECT ''paper_info.workflow_step 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- check_task 加单篇最大相似比 + 引用率（P5）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'check_task'
     AND column_name = 'max_per_source'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `check_task` '
  'ADD COLUMN `max_per_source` decimal(5,2) DEFAULT NULL COMMENT ''单篇最大相似比（P5 新口径：所有来源中单篇贡献的最大重复占比，如 12.34 表示 12.34%）。存量行为旧口径，不迁移'' AFTER `check_rate`, '
  'ADD COLUMN `citation_rate` decimal(5,2) DEFAULT NULL COMMENT ''引用率（P5：引用命中句字数 / 正文字数，如 3.21 表示 3.21%）'' AFTER `max_per_source`',
  'SELECT ''check_task.max_per_source/citation_rate 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- check_result 加单篇最大相似比 + 引用率（P5）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'check_result'
     AND column_name = 'max_per_source'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `check_result` '
  'ADD COLUMN `max_per_source` decimal(5,2) DEFAULT NULL COMMENT ''单篇最大相似比（P5 新口径，与 check_task 同义）'' AFTER `repeat_rate`, '
  'ADD COLUMN `citation_rate` decimal(5,2) DEFAULT NULL COMMENT ''引用率（P5 新口径，与 check_task 同义）'' AFTER `max_per_source`',
  'SELECT ''check_result.max_per_source/citation_rate 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- check_report 加内容哈希（P5 报告验真）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'check_report'
     AND column_name = 'content_hash'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `check_report` '
  'ADD COLUMN `content_hash` varchar(64) DEFAULT NULL COMMENT ''报告内容哈希（P5：SHA-256(关键要素JSON+盐) 小写 hex，公开验真接口重算比对；历史报告为 NULL 视为不可验真）'' AFTER `total_similarity`',
  'SELECT ''check_report.content_hash 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- paper_info 加学位类型（P5，存量行随 DEFAULT 回填为本科）+ 查询索引
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'paper_info'
     AND column_name = 'degree_type'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `paper_info` '
  'ADD COLUMN `degree_type` varchar(20) DEFAULT ''undergraduate'' COMMENT ''学位类型（P5 检测策略集匹配维度：undergraduate-本科 / master-硕士 / doctor-博士，对应字典 degree_type；存量默认本科）'' AFTER `similarity_rate`',
  'SELECT ''paper_info.degree_type 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'paper_info'
     AND index_name = 'idx_pi_degree'
);
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE `paper_info` ADD INDEX `idx_pi_degree` (`degree_type`)',
  'SELECT ''paper_info.idx_pi_degree 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- paper_info 补正文/分词 MinIO 路径两列（架构修复 P0：init.sql 建表已含，
-- 但存量库与实体映射双双缺失——实体被错误标为 exist=false 且从未回填，
-- 导致本地比对库恒为空、查重结果恒 0%。列必须先于新 jar 部署就位）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'paper_info'
     AND column_name = 'content_path'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `paper_info` '
  'ADD COLUMN `content_path` varchar(255) DEFAULT NULL COMMENT ''论文正文在MinIO中的存储路径（架构修复P0：查重入库管道回填）'' AFTER `degree_type`, '
  'ADD COLUMN `segmented_path` varchar(255) DEFAULT NULL COMMENT ''IK分词后文本在MinIO中的存储路径'' AFTER `content_path`',
  'SELECT ''paper_info.content_path/segmented_path 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- system_param 加成绩加权三项权重（P5：指导/评阅/答辩，默认 20/30/50）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'system_param'
     AND column_name = 'guidance_weight'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `system_param` '
  'ADD COLUMN `guidance_weight` decimal(5,2) DEFAULT 20.00 COMMENT ''成绩加权合成 · 指导分权重（百分比，默认20；三项合计≠100 抛 40915）'', '
  'ADD COLUMN `review_weight` decimal(5,2) DEFAULT 30.00 COMMENT ''成绩加权合成 · 评阅分权重（百分比，默认30）'', '
  'ADD COLUMN `defense_weight` decimal(5,2) DEFAULT 50.00 COMMENT ''成绩加权合成 · 答辩分权重（百分比，默认50）''',
  'SELECT ''system_param 成绩加权三列已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 1.16.3 字典种子（ID 段约定：70xx 论文状态 / 71xx 评阅 / 72xx 答辩 /
--   73xx 学位类型 / 74xx 导师评价标签；与 种子数据.sql 段 10 同一份清单）
-- ----------------------------------------------------------------------------

-- paper_status：7 个存量状态 + 6 个 P1 新增阶段门状态
INSERT IGNORE INTO `sys_dict_type`
  (`id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7000, '论文状态', 'paper_status', '0', '1546278765432123458', NOW(),
   '论文全生命周期状态（P1 扩展：新增评阅/答辩/归档阶段门）');

INSERT IGNORE INTO `sys_dict_data`
  (`id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7001, 1,  '待分配',    'pending',        'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态'),
  (7002, 2,  '已分配',    'assigned',       'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态'),
  (7003, 3,  '待查重',    'checking',       'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态'),
  (7004, 4,  '待审核',    'auditing',       'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态（语义收窄为导师初审）'),
  (7005, 5,  '审核通过',  'completed',      'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态（语义收窄为初审通过）'),
  (7006, 6,  '审核不通过','rejected',       'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态'),
  (7007, 7,  '已取消',    'withdrawn',      'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态'),
  (7008, 8,  '评阅中',    'reviewing',      'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P2 评阅任务启用）'),
  (7009, 9,  '中期答辩',  'mid_defense',    'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P3 答辩管理启用）'),
  (7010, 10, '终期答辩',  'final_defense',  'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P3 答辩管理启用）'),
  (7011, 11, '答辩通过',  'defense_passed', 'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P3 答辩管理启用）'),
  (7012, 12, '定稿',      'finalized',      'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P4 归档启用）'),
  (7013, 13, '已归档',    'archived',       'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P4 归档启用）');

-- 评阅任务字典（P2：状态 / 类型 / 方式）
INSERT IGNORE INTO `sys_dict_type`
  (`id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7100, '评阅任务状态', 'review_assign_status', '0', '1546278765432123458', NOW(), '多人评阅任务状态（论文全生命周期P2）'),
  (7110, '评阅任务类型', 'review_assign_type',   '0', '1546278765432123458', NOW(), '导师评阅/交叉评阅/盲审（论文全生命周期P2）'),
  (7120, '评阅任务方式', 'review_assign_method', '0', '1546278765432123458', NOW(), '线上评阅/线下评阅结果回填（论文全生命周期P2）');

INSERT IGNORE INTO `sys_dict_data`
  (`id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7101, 1, '待评阅',     '0', 'review_assign_status', 'N', '0', '1546278765432123458', NOW(), '任务已下发'),
  (7102, 2, '评阅中',     '1', 'review_assign_status', 'N', '0', '1546278765432123458', NOW(), '预留（P2 未开放开始动作）'),
  (7103, 3, '已提交',     '2', 'review_assign_status', 'N', '0', '1546278765432123458', NOW(), '评阅结果已提交'),
  (7104, 4, '已超时转派', '3', 'review_assign_status', 'N', '0', '1546278765432123458', NOW(), '超时扫描标记，原任务失效'),
  (7111, 1, '导师评阅',   'advisor', 'review_assign_type', 'N', '0', '1546278765432123458', NOW(), ''),
  (7112, 2, '交叉评阅',   'peer',    'review_assign_type', 'N', '0', '1546278765432123458', NOW(), '默认类型'),
  (7113, 3, '盲审',       'blind',   'review_assign_type', 'N', '0', '1546278765432123458', NOW(), '查询层对学生/导师信息脱敏'),
  (7121, 1, '线上评阅',   'online',  'review_assign_method', 'N', '0', '1546278765432123458', NOW(), '系统内直接打分'),
  (7122, 2, '线下评阅',   'offline', 'review_assign_method', 'N', '0', '1546278765432123458', NOW(), '结果回填 + 扫描件佐证');

-- 答辩相关字典（P3：批次状态 / 批次类型 / 答辩方式 / 组角色 / 结论 / 等级）
INSERT IGNORE INTO `sys_dict_type`
  (`id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7200, '答辩批次状态', 'defense_batch_status', '0', '1546278765432123458', NOW(), '答辩批次状态（论文全生命周期P3）'),
  (7210, '答辩批次类型', 'defense_batch_type',   '0', '1546278765432123458', NOW(), '中期/终期共用（论文全生命周期P3）'),
  (7220, '答辩方式',     'defense_mode',         '0', '1546278765432123458', NOW(), '线上/线下（论文全生命周期P3）'),
  (7230, '答辩组角色',   'defense_group_role',   '0', '1546278765432123458', NOW(), '组长/成员（论文全生命周期P3）'),
  (7240, '答辩结论',     'defense_result',       '0', '1546278765432123458', NOW(), '通过/不通过/缓辩（论文全生命周期P3）'),
  (7250, '答辩等级',     'defense_grade',        '0', '1546278765432123458', NOW(), '优/良/中/及格/不及格（论文全生命周期P3）');

INSERT IGNORE INTO `sys_dict_data`
  (`id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7201, 1, '筹备中', '0', 'defense_batch_status', 'Y', '0', '1546278765432123458', NOW(), '批次新建，未开始答辩'),
  (7202, 2, '进行中', '1', 'defense_batch_status', 'N', '0', '1546278765432123458', NOW(), '答辩进行中'),
  (7203, 3, '已结束', '2', 'defense_batch_status', 'N', '0', '1546278765432123458', NOW(), '批次已结束（组长已汇总提交结论）'),
  (7211, 1, '中期答辩', 'mid_term', 'defense_batch_type', 'N', '0', '1546278765432123458', NOW(), '中期检查答辩'),
  (7212, 2, '终期答辩', 'final',    'defense_batch_type', 'Y', '0', '1546278765432123458', NOW(), '终期/毕业答辩（成绩回写 final_score）'),
  (7221, 1, '线上答辩', 'online',  'defense_mode', 'N', '0', '1546278765432123458', NOW(), '线上会议，location 填会议链接'),
  (7222, 2, '线下答辩', 'offline', 'defense_mode', 'Y', '0', '1546278765432123458', NOW(), '线下教室，location 填教室地址'),
  (7231, 1, '组长', 'leader', 'defense_group_role', 'N', '0', '1546278765432123458', NOW(), '主持并汇总结论'),
  (7232, 2, '成员', 'member', 'defense_group_role', 'Y', '0', '1546278765432123458', NOW(), '答辩组成员'),
  (7241, 1, '通过',     'pass',     'defense_result', 'Y', '0', '1546278765432123458', NOW(), '答辩通过'),
  (7242, 2, '不通过',   'fail',     'defense_result', 'N', '0', '1546278765432123458', NOW(), '答辩不通过（退回修改）'),
  (7243, 3, '缓辩',     'postpone', 'defense_result', 'N', '0', '1546278765432123458', NOW(), '缓辩（择期重排批次）'),
  (7251, 1, '优',     '优',     'defense_grade', 'N', '0', '1546278765432123458', NOW(), ''),
  (7252, 2, '良',     '良',     'defense_grade', 'N', '0', '1546278765432123458', NOW(), ''),
  (7253, 3, '中',     '中',     'defense_grade', 'N', '0', '1546278765432123458', NOW(), ''),
  (7254, 4, '及格',   '及格',   'defense_grade', 'N', '0', '1546278765432123458', NOW(), ''),
  (7255, 5, '不及格', '不及格', 'defense_grade', 'N', '0', '1546278765432123458', NOW(), '');

-- 学位类型字典（P5）
INSERT IGNORE INTO `sys_dict_type`
  (`id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7300, '学位类型', 'degree_type', '0', '1546278765432123458', NOW(), '论文学位类型（论文全生命周期P5，检测策略集匹配维度）');

INSERT IGNORE INTO `sys_dict_data`
  (`id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7301, 1, '本科', 'undergraduate', 'degree_type', 'Y', '0', '1546278765432123458', NOW(), '本科学位论文'),
  (7302, 2, '硕士', 'master',        'degree_type', 'N', '0', '1546278765432123458', NOW(), '硕士学位论文'),
  (7303, 3, '博士', 'doctor',        'degree_type', 'N', '0', '1546278765432123458', NOW(), '博士学位论文');

-- 导师评价标签字典（P6：负向标签与正向标签同列，由前端按 remark 展示分组）
INSERT IGNORE INTO `sys_dict_type`
  (`id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7400, '导师评价标签', 'advisor_eval_tag', '0', '1546278765432123458', NOW(), '学生对指导教师评价的预设标签（论文全生命周期P6a）');

INSERT IGNORE INTO `sys_dict_data`
  (`id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7401, 1, '回复及时',   'reply_timely',       'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '正向'),
  (7402, 2, '反馈细致',   'feedback_detailed',  'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '正向'),
  (7403, 3, '要求严格',   'strict_demanding',   'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '正向'),
  (7404, 4, '因材施教',   'tailored_guidance',  'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '正向'),
  (7405, 5, '资源丰富',   'resource_support',   'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '正向'),
  (7406, 6, '指导偏少',   'guidance_lacking',  'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '负向'),
  (7407, 7, '反馈较慢',   'feedback_slow',      'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '负向');

-- ============================================================================
-- 段 1.17：paper_info 补 version / parent_paper_id 两列（审核不通过→修改版关联）
--
-- 背景：init.sql 的 paper_info 建表语句早已声明这两列，但 PaperInfo 实体长期未映射，
--   学生端「审核不通过后重新提交」（StudentReviewServiceImpl.reSubmitPaper）插入修改版时
--   也从未写入——修改版与原论文之间只剩「标题 + '(修改版_日期)'」的字符串约定，
--   查询只能全局 LIKE '%修改版%'，A 论文的重提记录会串到 B 论文上。
--   本段给老库补齐两列与查询索引；新库（init.sql 建库）无需执行。
--   幂等性：MySQL 8 的 ADD COLUMN/ADD INDEX 不支持 IF NOT EXISTS，
--   重复执行报 1060（列已存在）/1061（索引已存在）均属预期，用 --force 跑。
-- ============================================================================

ALTER TABLE `paper_info`
  ADD COLUMN `version` int DEFAULT '1' COMMENT '论文版本号（初版为1，审核不通过后每重新提交一次在上一版基础上+1）' AFTER `check_source`,
  ADD COLUMN `parent_paper_id` bigint DEFAULT NULL COMMENT '父版本论文ID（修改版指向被驳回的原论文paper_info.id，链式重提始终指向根论文；初版为NULL）' AFTER `version`;

ALTER TABLE `paper_info`
  ADD KEY `idx_pi_parent_paper_id` (`parent_paper_id`);

-- ============================================================================
-- 段 1.18：paper_info 补 audit_comment 审核意见列（S12 审核链路一致性）
--
-- 背景：审核意见此前混写进 check_result（查重结果），与「查重完成，相似度：x%」
--   等查重文案互相覆盖；前端（学生端 MyPapers.vue「审核意见」栏）按 paper.feedback
--   读取，后端从未输出。S12 起审核意见独立存 audit_comment，PaperInfo 实体以
--   @JsonProperty("feedback") 对齐前端契约，check_result 只承载查重结果。
--   存量 check_result 里的历史审核意见不做迁移（与查重文案无法可靠区分），
--   以新审核产生的意见为准。
-- 部署顺序：必须「先跑本段，再部署后端 jar」（实体已映射新列）。
-- 幂等性：information_schema 探测列是否存在，重复执行输出"已存在，跳过"。
-- ============================================================================

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'paper_info'
     AND column_name = 'audit_comment'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `paper_info` '
  'ADD COLUMN `audit_comment` text COMMENT ''审核意见（初审/退回意见，独立于 check_result 查重结果存储）'' AFTER `third_party_check_result`',
  'SELECT ''paper_info.audit_comment 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 段 1.19：sys_operation_type 操作类型字典补录 58 条（2026-10-18）
--
-- 背景：全项目 @OperationLog 共 100 个 type，字典种子仅覆盖 42 个（139 条），
--   P1~P5 新增的答辩/评阅/性能配置/知识库/通知/学科等操作 type 全部缺失。
--   日志中心「模块使用率」按 LEFT JOIN sys_operation_type 取 module，
--   join 不上的 type 统一落 COALESCE 的「未知」——线上实测
--   GET /api/admin/operation-logs/module-usage 返回单行 {moduleName:"未知", count:58}。
-- 部署顺序：先跑本段（无需重启/重部署，纯数据补充，实时生效）。
-- 幂等性：uk_type 唯一键 ON DUPLICATE KEY UPDATE，重复执行仅更新不新增。
-- 执行后自检：SELECT COUNT(*) FROM sys_operation_type;  期望 197。
-- ============================================================================

INSERT INTO `sys_operation_type` (`id`, `type`, `name`, `description`, `module`, `permission_level`, `status`, `create_time`, `update_time`) VALUES
(140,'admin_advisor_eval_export','管理员导出导师评价明细','管理员导出导师评价明细','教师管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(141,'admin_backup_settings_update','管理员更新备份设置','管理员更新备份设置','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(142,'admin_backup_trigger','管理员手动触发数据库备份','管理员手动触发数据库备份','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(143,'admin_check_quota_save','管理员保存学院月度查重额度','管理员保存学院月度查重额度','查重管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(144,'admin_config_performance_reset','管理员重置性能配置为默认值','管理员重置性能配置为默认值','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(145,'admin_config_performance_test','管理员测试应用性能配置','管理员测试应用性能配置','系统管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(146,'admin_config_performance_update','管理员更新性能配置','管理员更新性能配置','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(147,'admin_defense_batch_create','管理员新建答辩批次','管理员新建答辩批次','答辩管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(148,'admin_defense_batch_delete','管理员删除答辩批次','管理员删除答辩批次','答辩管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(149,'admin_defense_batch_update','管理员编辑答辩批次','管理员编辑答辩批次','答辩管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(150,'admin_defense_enroll','管理员安排学生入答辩批次','管理员安排学生入答辩批次','答辩管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(151,'admin_defense_members_assign','管理员分配答辩组成员','管理员分配答辩组成员','答辩管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(152,'admin_manual_assign','管理员手动分配指导老师','管理员手动分配指导老师','教师管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(153,'admin_review_assign_allocate','管理员分配评阅任务','管理员分配评阅任务','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(154,'admin_role_create','管理员创建角色','管理员创建角色','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(155,'admin_role_delete','管理员删除角色','管理员删除角色','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(156,'admin_role_update','管理员更新角色','管理员更新角色','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(157,'admin_thesis_score_recompute','管理员重算论文总评成绩','管理员重算论文总评成绩','论文管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(158,'admin_user_role_assign','管理员分配用户角色','管理员分配用户角色','用户管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(159,'assign_advisor','分配导师','分配导师','教师管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(160,'check_policy_delete','删除检测策略','删除检测策略','查重管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(161,'check_policy_save','保存检测策略','保存检测策略','查重管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(162,'config_email_test','管理员发送测试邮件','管理员发送测试邮件','系统管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(163,'knowledge_article_create','新增知识库文章','新增知识库文章','知识库','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(164,'knowledge_article_delete','删除知识库文章','删除知识库文章','知识库','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(165,'knowledge_article_status_update','更新知识库文章状态','更新知识库文章状态','知识库','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(166,'knowledge_article_update','更新知识库文章','更新知识库文章','知识库','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(167,'message_template_status_update','更新消息模板启用状态','更新消息模板启用状态','系统管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(168,'notice_batch_delete','批量删除通知','批量删除通知','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(169,'notice_batch_read','批量标记通知已读','批量标记通知已读','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(170,'notice_clear_all','清空通知','清空通知','通知管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(171,'notice_delete','删除通知','删除通知','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(172,'notice_read','标记通知已读','标记通知已读','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(173,'notice_read_all','全部标记通知已读','全部标记通知已读','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(174,'notification_paper_review','发送论文审核结果通知','发送论文审核结果通知','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(175,'notification_paper_status','发送论文状态变更通知','发送论文状态变更通知','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(176,'review_workflow_update','更新审核工作流配置','更新审核工作流配置','论文管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(177,'similarity_threshold_update','更新相似度阈值设置','更新相似度阈值设置','查重管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(178,'student_advisor_eval_submit','学生提交导师评价','学生提交导师评价','学生管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(179,'student_group_add_students','添加学生到分组','添加学生到分组','学生管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(180,'student_group_create','创建学生分组','创建学生分组','学生管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(181,'student_group_delete','删除学生分组','删除学生分组','学生管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(182,'student_group_remove_student','从分组移除学生','从分组移除学生','学生管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(183,'student_group_update','更新学生分组','更新学生分组','学生管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(184,'student_guidance_confirm','学生确认指导记录','学生确认指导记录','学生管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(185,'subject_create','新增学科领域','新增学科领域','学科管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(186,'subject_delete','删除学科领域','删除学科领域','学科管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(187,'subject_enabled','启用或停用学科领域','启用或停用学科领域','学科管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(188,'subject_update','修改学科领域','修改学科领域','学科管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(189,'teacher_batch_review','教师批量审核论文','教师批量审核论文','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(190,'teacher_check_signoff','教师签核查重报告','教师签核查重报告','查重管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(191,'teacher_confirm_assignment','教师确认论文分配','教师确认论文分配','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(192,'teacher_defense_score_submit','答辩组长提交评分结论','答辩组长提交评分结论','答辩管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(193,'teacher_guidance_create','教师发起指导记录','教师发起指导记录','教师管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(194,'teacher_paper_review','教师审核论文','教师审核论文','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(195,'teacher_reject_assignment','教师拒绝论文分配','教师拒绝论文分配','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(196,'teacher_review_assign_submit','教师提交评阅结果','教师提交评阅结果','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(197,'teacher_workflow_vote','教师多级初审投票','教师多级初审投票','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00')
ON DUPLICATE KEY UPDATE `type` = VALUES(`type`), `name` = VALUES(`name`), `description` = VALUES(`description`), `module` = VALUES(`module`), `permission_level` = VALUES(`permission_level`), `status` = VALUES(`status`);

-- ============================================================================
-- 段 1.20：system_message / review_workflow 的 is_deleted 历史 NULL 归零
--   （2026-10-19，段 2 同款坑第二、三次复发）
--
-- 背景：system_message.is_deleted 与 review_workflow.is_deleted 在 init.sql 里是
--   DEFAULT NULL（全库其余 74 张表都是 DEFAULT '0'），与 category_similarity_threshold
--   当年踩的是同一个坑：BaseEntity.isDeleted 只标 @TableLogic、无默认值也无
--   fill 自动填充，插入时 MyBatis-Plus 省略该列 → 行带着 NULL 落库；而 MP 所有查询
--   都拼 `is_deleted = 0`，NULL = 0 永不成立，行被静默过滤，无任何报错。
--   实测症状：导师分配/论文提交后站内信正常落库，但师生消息中心永远「暂无消息」，
--   GET /api/message/list 恒返回空列表（2026-10-19 教师看不到「待确认」分配通知）。
--   init.sql 两处默认值已同步修正为 NOT NULL DEFAULT '0'，本段负责捞回存量 NULL 行。
-- 部署顺序：先跑本段（纯数据 + 列默认值修正，无需重启后端，跑完实时生效）。
-- 幂等性：UPDATE 只动 NULL 行，重复执行无副作用；MODIFY 为幂等 DDL。
-- 执行后自检：见段 5.15。
-- ============================================================================

UPDATE `system_message` SET `is_deleted` = 0 WHERE `is_deleted` IS NULL;
ALTER TABLE `system_message`
  MODIFY COLUMN `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除标记：0-未删除，1-已删除';

UPDATE `review_workflow` SET `is_deleted` = 0 WHERE `is_deleted` IS NULL;
ALTER TABLE `review_workflow`
  MODIFY COLUMN `is_deleted` int NOT NULL DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）';

-- ============================================================================
-- 段 1.21：教师确认/拒绝通知模板文案改为学生视角
--   （2026-10-19，收件人与文案视角错位）
--
-- 背景：TeacherAssignmentServiceImpl.sendConfirmationNoticeToStudent 在教师确认/
--   拒绝分配后，把 TEACHER_CONFIRM_NOTICE / TEACHER_REJECT_NOTICE 发给学生
--   （收件人=论文学生，这是有意设计）。但种子数据里这两个模板的正文是教师视角
--   （"尊敬的{teacherName}老师：您已成功确认接收……请及时开展后续指导工作"），
--   导致学生收到一条称呼别人、以老师口吻写的消息。正确的学生视角模板
--   STUDENT_CONFIRM_RESULT（种子 id=4）反而无任何调用方，属死模板（本段不动它，
--   保留备查）。修复只改 2/3 两条模板的名称、标题、正文（学生视角），变量集不变：
--   确认用 studentName/paperTitle/teacherName/confirmTime，拒绝追加 rejectReason/rejectTime。
--   模板渲染实时读库，本段执行即生效，无需重启后端。
-- 幂等性：UPDATE 按编码覆写，重复执行无副作用。
-- ============================================================================

UPDATE `message_template` SET
  `template_name` = '教师确认结果通知',
  `title_template` = '你的论文指导老师已确认',
  `content_template` = '亲爱的{studentName}同学：\n\n你的论文《{paperTitle}》的指导老师{teacherName}已确认接收指导任务，后续将由{teacherName}老师为你提供指导。\n\n确认时间：{confirmTime}'
WHERE `template_code` = 'TEACHER_CONFIRM_NOTICE';

UPDATE `message_template` SET
  `template_name` = '教师拒绝结果通知',
  `title_template` = '你的论文指导任务被拒绝',
  `content_template` = '亲爱的{studentName}同学：\n\n很抱歉，你的论文《{paperTitle}》的指导老师{teacherName}未能接收指导任务。\n\n拒绝原因：{rejectReason}\n\n系统将为你重新分配其他指导老师，请耐心等待。\n\n拒绝时间：{rejectTime}'
WHERE `template_code` = 'TEACHER_REJECT_NOTICE';

-- ============================================================================
-- 段 1.22：check_task 补 paper_version / submit_id（M3.1 版本—报告绑定）
--
-- 背景：报告此前只能经 check_task.paper_id 反查到论文主记录，无法确认这份报告
--   对应的是论文的哪一版、哪一次提交（paper_info 重提会 version+1，paper_submit
--   每次提交新增一行）。补两列后，报告 → 任务 → 提交记录可直连，多版本论文的
--   报告不再混在一起。存量任务保持 NULL（历史任务无法可靠回溯是第几次提交），
--   不做回填。
-- 部署顺序：必须「先跑本段，再部署后端 jar」（实体已映射新列）。
-- 幂等性：information_schema 探测列/索引是否存在，重复执行输出"已存在，跳过"。
-- 执行后自检：见段 5.16。
-- ============================================================================

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'check_task'
     AND column_name = 'paper_version'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `check_task` '
  'ADD COLUMN `paper_version` int DEFAULT NULL COMMENT ''论文版本号（创建任务时快照 paper_info.version；存量任务为 NULL）'' AFTER `paper_id`',
  'SELECT ''check_task.paper_version 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'check_task'
     AND column_name = 'submit_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `check_task` '
  'ADD COLUMN `submit_id` bigint DEFAULT NULL COMMENT ''论文提交记录ID（关联 paper_submit.id，创建任务时取最新一次提交；存量任务为 NULL）'' AFTER `paper_version`',
  'SELECT ''check_task.submit_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'check_task'
     AND index_name = 'idx_ct_paper_submit'
);
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE `check_task` ADD KEY `idx_ct_paper_submit` (`paper_id`, `submit_id`)',
  'SELECT ''check_task.idx_ct_paper_submit 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 段 1.23：check_task 补 quota_charged（M4.1 额度与流程联动）
--
-- 背景：学院月度额度在任务创建时扣减，但任务失败后此前不会返还，失败任务白占额度。
--   仅凭 used_quota 无法判断某个任务是否真的扣过额度（未配额度的学院不扣），
--   因此用本列记录"该任务是否实际扣减过"，失败返还时按 1 -> 0 原子翻转，
--   保证同一任务最多返还一次；重试时重新扣减并置 1，避免重复/漏返还。
-- 部署顺序：必须「先跑本段，再部署后端 jar」（实体已映射新列）。
-- 幂等性：information_schema 探测列是否存在，重复执行输出"已存在，跳过"。
-- 执行后自检：见段 5.17。
-- ============================================================================

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'check_task'
     AND column_name = 'quota_charged'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `check_task` '
  'ADD COLUMN `quota_charged` tinyint NOT NULL DEFAULT 0 COMMENT ''是否已扣减学院月度额度（1-已扣，0-未扣/已返还）'' AFTER `submit_id`',
  'SELECT ''check_task.quota_charged 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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


-- ---------------------------------------------------------------------
-- 4.3 修复 C：回填 sys_user.user_type 为 NULL 的行
--
--   症状：管理端「用户管理」列表整个打不开（接口 500），日志里是
--     NullPointerException: Cannot invoke "String.hashCode()" because "<local4>" is null
--     at AdminUserServiceImpl.convertToDTO(AdminUserServiceImpl.java:350)
--   <local4> 是 switch 选择表达式的临时槽位。Java 的 switch 遇到 null 选择表达式
--   **直接抛 NPE、不会走 default**，所以原代码里那句 `default -> "未知"` 兜不住。
--   一行脏数据就能中断整个 stream 的 collect，于是整页列表一起挂 —— 不是只坏一行。
--
--   user_type 是 sys_role.role_code 的冗余副本。全项目 4 条 sys_user 写入路径里，
--   只有自注册 AuthServiceImpl.register 没写这一列；而 DDL 是 `DEFAULT NULL`，
--   于是脏数据静默落库。代码侧已同步修复（register 补写 + 列表角色名改从
--   sys_role 取），本段只负责清存量。
--
--   本段只动 NULL / 空串的行，可重复执行。回填值取 role_code 而不是写死三值：
--   库里本来就存在 SUPER_ADMIN 这类取值（管理端人员类型筛选用的是同一列），
--   统一成 role_code 才自洽。
-- ---------------------------------------------------------------------

-- 4.3.0 先看清要动哪些行。注意左连接后 role_code 为空的行**回填不出来**，
--       得人工确认该用户到底该是什么角色（通常意味着 role_id 指向了已删角色）。
SELECT u.id, u.username, u.real_name, u.role_id, r.role_code, r.role_name, u.create_time
  FROM sys_user u
  LEFT JOIN sys_role r ON r.id = u.role_id
 WHERE u.user_type IS NULL OR u.user_type = ''
 ORDER BY u.create_time;

-- 4.3.1 按角色编码回填（含已软删的用户行：它们仍会被别处的查询捞出来）
UPDATE sys_user u
  JOIN sys_role r ON r.id = u.role_id
   SET u.user_type = r.role_code
 WHERE (u.user_type IS NULL OR u.user_type = '')
   AND r.role_code IS NOT NULL
   AND r.role_code <> '';


-- ============================================================================
-- 段 4.4：paper_info.similarity_rate 未检测语义归位（0.00 → NULL，2026-10-19）
--
-- 背景：PaperInfoServiceImpl 旧逻辑在创建/重新提交论文时把 similarity_rate
--   落成 0.00，而语义约定是 NULL = 未检测。前端「我的论文」按
--   similarityRate != null 显示「查重报告」按钮、按 null 显示「未检测」，
--   于是从未查重的论文也露出报告入口，点击必报「未找到该论文的查重报告」，
--   总相似比列也错显示 0%。
-- 规则：凡是没有任何一条 completed 查重任务的论文，similarity_rate 一律置
--   NULL；查重成功过的行不动。幂等，可重复执行。
-- ============================================================================

-- 4.4.0 先看清要动多少行（similarity_rate 非 NULL 但无 completed 任务的行数）
SELECT COUNT(*) AS stale_similarity_rows
FROM paper_info p
WHERE p.similarity_rate IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM check_task t
      WHERE t.paper_id = p.id
        AND t.check_status = 'completed'
        AND t.is_deleted = 0
  );

-- 4.4.1 归位：无 completed 任务的论文 similarity_rate 置 NULL
UPDATE paper_info p
   SET p.similarity_rate = NULL
 WHERE p.similarity_rate IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM check_task t
       WHERE t.paper_id = p.id
         AND t.check_status = 'completed'
         AND t.is_deleted = 0
   );


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

-- 5.6b 段 1.14 执行后：期望 1
SELECT COUNT(*) AS has_system_metric_minute
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_metric_minute';

-- 5.7 段 1.10 学科领域重构执行后（和 0.7 对照）
--     期望：has_subject_tables=3 / subject_cols=2 / subject_level1=10 / subject_level2=90
--           orphan_subject_should_be_0=0（二级指不到一级门类）
--           subject_level_clash_should_be_0=0（level 与 parent_id 不自洽）
--           paper_backfill_missed_should_be_0=0（code 有对应学科、却仍没回填出 subject_id）
--           paper_code_unmapped_should_be_0=0（有 code 却指不到任何 subject.code；
--             历史脏 code 会让它非 0，**那种情况是正常的**，对着 0.7 的清单逐条看即可）
--           teacher_subject_dangling_should_be_0=0
--           major_subject_dangling_should_be_0=0（映射指不到学科）
--           major_subject_major_dangling_should_be_0=0（映射指不到专业 —— 这一项非 0
--             基本等于"本文件被抢在 种子数据.sql 之前跑了"，专业 id 还是旧的）
--           major_subject_pairs=128（= 种子数据.sql 段 8.8 的 pairs；两边不等就是清单漂了）
--     dict_subject_rows 与 subject_rows 的对照：字典行数是 100（本段"一行不删"的预期），
--     subject_rows 也是 100。两者不等不一定是错（字典可被 admin 加过行），但要能解释。
SELECT (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME IN ('subject','major_subject','teacher_subject'))               AS has_subject_tables,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND ((TABLE_NAME = 'paper_info'  AND COLUMN_NAME = 'subject_id')
             OR (TABLE_NAME = 'category_similarity_threshold' AND COLUMN_NAME = 'subject_id'))) AS subject_cols,
       (SELECT COUNT(*) FROM `subject` WHERE is_deleted = 0 AND level = 1)                AS subject_level1,
       (SELECT COUNT(*) FROM `subject` WHERE is_deleted = 0 AND level = 2)                AS subject_level2,
       (SELECT COUNT(*) FROM `subject` s LEFT JOIN `subject` p ON s.parent_id = p.id
         WHERE s.level = 2 AND (p.id IS NULL OR p.level <> 1))                            AS orphan_subject_should_be_0,
       (SELECT COUNT(*) FROM `subject` WHERE is_deleted = 0
          AND ((level = 1 AND parent_id IS NOT NULL) OR (level = 2 AND parent_id IS NULL))) AS subject_level_clash_should_be_0,
       -- 这一列查的是"回填该命中却没命中"：code 在 subject 表里存在、subject_id 却还是空。
       -- 与下一段的 paper_code_unmapped_should_be_0 不是一回事 —— 那个数的是"code 根本
       -- 映射不到学科"，历史脏 code 会让它非 0 且**属正常**；这一列非 0 只可能是 1.10.5
       -- 的回填没跑成（或被中途打断），所以期望值严格为 0。
       (SELECT COUNT(*) FROM `paper_info` p JOIN `subject` s ON TRIM(p.subject_code) = s.code
         WHERE p.is_deleted = 0 AND p.subject_id IS NULL)                                 AS paper_backfill_missed_should_be_0;

SELECT (SELECT COUNT(*) FROM `paper_info` p LEFT JOIN `subject` s ON p.subject_id = s.id
         WHERE p.subject_id IS NOT NULL AND s.id IS NULL)                                 AS paper_subject_id_dangling_should_be_0,
       (SELECT COUNT(*) FROM `paper_info` p LEFT JOIN `subject` s ON TRIM(p.subject_code) = s.code
         WHERE p.subject_code IS NOT NULL AND TRIM(p.subject_code) <> '' AND s.id IS NULL) AS paper_code_unmapped_should_be_0,
       (SELECT COUNT(*) FROM `teacher_subject` ts LEFT JOIN `subject` s ON ts.subject_id = s.id
         WHERE ts.is_deleted = 0 AND s.id IS NULL)                                        AS teacher_subject_dangling_should_be_0,
       (SELECT COUNT(*) FROM `major_subject` ms LEFT JOIN `subject` s ON ms.subject_id = s.id
         WHERE ms.is_deleted = 0 AND s.id IS NULL)                                        AS major_subject_dangling_should_be_0,
       -- major 侧同样要查：1.10.4b 的 major_id 写死的是 种子数据.sql 改绑后的新专业 id，
       -- 若本文件被抢在 种子数据.sql 之前跑（或那 114 个专业没进去），这一列会整片悬空
       (SELECT COUNT(*) FROM `major_subject` ms LEFT JOIN `major` m ON ms.major_id = m.id
         WHERE ms.is_deleted = 0 AND m.id IS NULL)                                        AS major_subject_major_dangling_should_be_0,
       (SELECT COUNT(*) FROM `major_subject` WHERE is_deleted = 0)                        AS major_subject_pairs,
       (SELECT COUNT(*) FROM `sys_dict_data` WHERE dict_type = 'subject_field')           AS dict_subject_rows,
       (SELECT COUNT(*) FROM `subject` WHERE is_deleted = 0)                              AS subject_rows;

-- 5.8 段 4.3 执行后：期望 user_type_null_rows = 0
--     仍非 0 就去对照 4.3.0 的清单 —— 那些行的 role_id 在 sys_role 里指不到东西，
--     回填不出来，得人工定角色后再补：
--       UPDATE sys_user SET user_type = '<ROLE_CODE>' WHERE id = <id>;
SELECT COUNT(*) AS user_type_null_rows FROM sys_user WHERE user_type IS NULL OR user_type = '';

-- 5.9 段 1.11 论文类型维度执行后（和 0.9 对照）：期望 1 / 1 / 0
--     pt_col       列到位
--     pt_index     索引到位（数 COUNT(DISTINCT INDEX_NAME) 而不是 COUNT(*)：
--                  STATISTICS 每个索引的每一列一行，该索引两列，数行会得到 2）
--     empty_pt     空串行数为 0（'' 既匹配不到通用档、也不会被带类型的查询命中，必须是 0）
SELECT (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'category_similarity_threshold'
           AND COLUMN_NAME = 'paper_type')                                        AS pt_col,
       (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'category_similarity_threshold'
           AND INDEX_NAME = 'idx_type_paper_type')                                AS pt_index,
       (SELECT COUNT(*) FROM category_similarity_threshold WHERE paper_type = '') AS empty_pt;

-- 5.9b 存量配置的生效范围没有被动过：期望 all_types_rows = 总行数、并存的类型档行数为 0 或新增数
--      本段执行前全部行都是 NULL（= 适用全部类型），执行后除人工新增外应一律仍为 NULL。
SELECT COUNT(*)                                            AS total_rows,
       SUM(CASE WHEN paper_type IS NULL THEN 1 ELSE 0 END) AS all_types_rows,
       COUNT(DISTINCT paper_type)                          AS distinct_types
  FROM category_similarity_threshold
 WHERE is_deleted = 0;

-- 5.10 段 1.13 角色编码唯一性执行后：期望 dup_codes=0 / deleted_still_clean=0 / uk_index=1
--      dup_codes           重复编码组数（去重没跑干净会 > 0，索引也就建不上）
--      deleted_still_clean 还占着干净编码的已删行数（1.13.2 漏跑会 > 0）
--      uk_index            唯一索引是否到位
SELECT (SELECT COUNT(*) FROM (
            SELECT role_code FROM sys_role GROUP BY role_code HAVING COUNT(*) > 1) d)      AS dup_codes,
       (SELECT COUNT(*) FROM sys_role
         WHERE is_deleted = 1 AND LOCATE('_del_', role_code) = 0)                          AS deleted_still_clean,
       (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role'
           AND INDEX_NAME = 'uk_role_code')                                                AS uk_index;

-- 5.10b 悬空引用：每个未删除用户的 role_id 都应指向未删除的角色（去重改挂后应恒为 0）
SELECT COUNT(*) AS dangling_user_role
  FROM sys_user u
  LEFT JOIN sys_role r ON r.id = u.role_id AND r.is_deleted = 0
 WHERE u.is_deleted = 0
   AND r.id IS NULL;

-- 5.11 段 1.15 个人设置 6 列执行后（和 0.10 对照）：期望 6
SELECT COUNT(*) AS sys_user_pref_cols_present FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
   AND COLUMN_NAME IN ('notify_system_error','notify_security_warning',
                       'notify_system_announcement','notify_email',
                       'log_retention_days','backup_frequency');

-- 5.12 段 1.16 论文全生命周期 P1~P6 执行后（和 0.11 对照）
--   期望：lifecycle_tables=11 / lifecycle_new_cols=10 / 字典 13/9/17/3/7
SELECT COUNT(*) AS lifecycle_tables FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME IN ('guidance_record','review_assignment','review_workflow_progress',
                      'defense_batch','defense_group_member','defense_record',
                      'check_sentence_hit','paper_check_signoff','check_policy',
                      'check_college_quota','advisor_evaluation');

SELECT COUNT(*) AS lifecycle_new_cols FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'paper_info'   AND COLUMN_NAME IN ('workflow_step','degree_type'))
     OR (TABLE_NAME = 'check_task'   AND COLUMN_NAME IN ('max_per_source','citation_rate'))
     OR (TABLE_NAME = 'check_result' AND COLUMN_NAME IN ('max_per_source','citation_rate'))
     OR (TABLE_NAME = 'check_report' AND COLUMN_NAME = 'content_hash')
     OR (TABLE_NAME = 'system_param' AND COLUMN_NAME IN ('guidance_weight','review_weight','defense_weight')));

-- 字典条数（sys_dict_data 表无 is_deleted 列，勿加此过滤）：
--   paper_status_dict=13 / review_dict=9 / defense_dict=17 / degree_dict=3 / eval_tag_dict=7
SELECT (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type = 'paper_status')              AS paper_status_dict,
       (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type IN ('review_assign_status',
              'review_assign_type','review_assign_method'))                               AS review_dict,
       (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type IN ('defense_batch_status',
              'defense_batch_type','defense_mode','defense_group_role',
              'defense_result','defense_grade'))                                          AS defense_dict,
       (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type = 'degree_type')               AS degree_dict,
       (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type = 'advisor_eval_tag')          AS eval_tag_dict;

-- 5.13 段 1.17 paper_info 版本关联两列执行后（和 0.12 对照）
--   期望：paper_version_cols=2
SELECT COUNT(*) AS paper_version_cols FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'paper_info'
   AND COLUMN_NAME IN ('version','parent_paper_id');

-- 5.14 段 1.18 paper_info 审核意见列执行后：期望 audit_comment_cols=1
SELECT COUNT(*) AS audit_comment_cols FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 'paper_info'
   AND COLUMN_NAME = 'audit_comment';

-- 5.15 段 1.20 站内信/审核工作流 is_deleted 归零执行后：
--   期望：msg_null_rows=0 / wf_null_rows=0 / msg_col=0 / wf_col=0
--   （col 两列数 information_schema 里 IS_NULLABLE='YES' 的 is_deleted，修正后应为 0；
--     若消息中心仍为空，再查 expire_time 和 receiver_id）
SELECT (SELECT COUNT(*) FROM system_message WHERE is_deleted IS NULL)               AS msg_null_rows,
       (SELECT COUNT(*) FROM review_workflow WHERE is_deleted IS NULL)              AS wf_null_rows,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_message'
           AND COLUMN_NAME = 'is_deleted' AND IS_NULLABLE = 'YES')                  AS msg_col,
       (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review_workflow'
           AND COLUMN_NAME = 'is_deleted' AND IS_NULLABLE = 'YES')                  AS wf_col;

-- 5.16 段 1.22 check_task 版本—报告绑定两列执行后：
--   期望：ct_version_submit_cols=2 / ct_paper_submit_idx=1
SELECT (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'check_task'
           AND COLUMN_NAME IN ('paper_version','submit_id'))                      AS ct_version_submit_cols,
       (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'check_task'
           AND INDEX_NAME = 'idx_ct_paper_submit')                                 AS ct_paper_submit_idx;

-- 5.17 段 1.23 check_task 额度扣减标记执行后：
--   期望：ct_quota_charged_col=1
SELECT (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'check_task'
           AND COLUMN_NAME = 'quota_charged')                                      AS ct_quota_charged_col;


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


-- ----------------------------------------------------------------------------
-- 附录 B：反向 —— 撤销「1.10 学科领域重构」
--
-- ⚠️ 先读这段再决定要不要跑：**不要为了回滚去删 sys_dict_data 的行**，本段也从没删过它们，
--    所以字典路径始终是完好的。回滚应用的步骤是「回滚镜像」，不是「撤 DDL」——
--    DEPLOY.md 第八节写明 deploy.sh 健康检查失败只回滚镜像、DDL 留着，
--    旧代码跑在新结构上是预期场景，而旧代码只读字典，照常工作。
--    下面的语句仅用于「确认新版不再上线、要彻底清掉新增结构」的场景，
--    且必须在所有引用方都回滚之后执行。
--
-- 顺序：先删引用列，再删关联表，最后删主表。
-- ----------------------------------------------------------------------------
-- ALTER TABLE `paper_info`                  DROP INDEX `idx_paper_info_subject_id`;
-- ALTER TABLE `paper_info`                  DROP COLUMN `subject_id`;
-- ALTER TABLE `category_similarity_threshold` DROP INDEX `idx_type_subject`;
-- ALTER TABLE `category_similarity_threshold` DROP COLUMN `subject_id`;
-- -- category_type 的注释与取值范围无需回改：旧代码只认 college / major，多一个取值不影响。
-- DROP TABLE IF EXISTS `teacher_subject`;
-- DROP TABLE IF EXISTS `major_subject`;
-- DROP TABLE IF EXISTS `subject`;
--
-- 另外，1.10.7 改过的 research_direction（ai→人工智能、software_engineering→软件工程）
-- 若要还原，需先备份（本段没有建备份表 —— 这两处改动是幂等且可人工改回的）。
-- 建议回滚前先跑一次：
--   SELECT id, user_type, research_direction FROM sys_user
--    WHERE user_type = 'TEACHER' AND research_direction IS NOT NULL;
