-- =====================================================================
-- V8: 让线上库结构追上代码（实体类 + Mapper XML）的预期
--
-- 背景：V1 是从旧的一体化系统导出的库结构；本仓库的实体类与 Mapper 是按
--       另一套「教师/学生属性合并进 sys_user」的结构写的。两者差异会让
--       MyBatis-Plus 的 selectById/selectList（按实体拼全列）报 Unknown column。
--       V1 已打基线（checksum 会被校验），因此只能新增迁移，绝不改动 V1。
--
-- 本文件只做「库向代码对齐」，不含破坏性删表：
--   teacher_info / student_info 保留（本轮只做数据回填，不删除）。
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. sys_user 合并教师 / 学生扩展属性
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
-- 2. 审计列改名：created_* / updated_* → create_* / update_*
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
-- 3. paper_attachment_rel：create_user_id → create_by（对齐实体字段名）
-- ---------------------------------------------------------------------
ALTER TABLE `paper_attachment_rel`
  CHANGE COLUMN `create_user_id` `create_by` bigint DEFAULT NULL COMMENT '创建人ID（学生ID）';


-- ---------------------------------------------------------------------
-- 4. check_report.paper_id 放松为可空
--    实体 CheckReport 没有 paperId 字段、三处写入路径也都未赋值；表内已有 60 行。
-- ---------------------------------------------------------------------
ALTER TABLE `check_report`
  MODIFY COLUMN `paper_id` bigint DEFAULT NULL COMMENT '对应的论文ID（关联paper_info.id，可空）';


-- ---------------------------------------------------------------------
-- 5. paper_submit 补 3 列
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

-- 回填年级：取自学生用户
UPDATE `paper_submit` ps
  JOIN `sys_user` u ON ps.student_id = u.id
   SET ps.grade = u.grade
 WHERE ps.grade IS NULL;


-- ---------------------------------------------------------------------
-- 6. teacher_audit：TeacherAuditMapper 的两条统计查询依赖此表，线上不存在
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
