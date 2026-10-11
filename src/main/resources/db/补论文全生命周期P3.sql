-- ============================================================================
-- 补论文全生命周期P3 —— 答辩管理三件套（P3 期）
--
-- ⚠️ 部署顺序（硬约束）：**先在目标库执行本脚本，再部署新 jar**。
--    新版实体（DefenseBatch / DefenseGroupMember / DefenseRecord）
--    引用了本脚本的表与列，漏跑脚本会导致答辩批次管理、评分录入等接口全站 500。
--
-- 本脚本幂等（CREATE TABLE IF NOT EXISTS + INSERT IGNORE），可重复执行。
-- 依赖：补论文全生命周期P1.sql 已执行（paper_status 字典已含
--        mid_defense / final_defense / defense_passed / finalized / archived）。
--
-- 内容：
--   1. 答辩批次表 defense_batch（中期/终期共用，batch_type 区分——一套代码两用）
--   2. 答辩组成员表 defense_group_member（组长 leader / 成员 member）
--   3. 答辩记录表 defense_record（按论文归档：综合得分/等级/结论/记录）
--   4. 答辩相关字典（批次状态 / 批次类型 / 答辩方式 / 组角色 / 结论 / 等级）
--   5. 执行后自检
--
-- 设计要点（见 docs/论文全生命周期落地方案.md §6.3 / §8）：
--   * defense_batch.batch_type = mid_term / final 复用同一套代码与表结构；
--   * mode = online / offline，线下评审仅做「结果回填」不做流程分支；
--   * 同一 (batch_id, paper_id) 唯一，避免同一论文在同一批次重复入册；
--   * 终期批次由组长提交结论后回写 paper_info.final_score，
--     并经 PaperStageGate 推进 paper_status：reviewing → mid_defense →
--     final_defense → defense_passed → finalized（finalized 由指导教师定稿确认，
--     归档 P4 接）。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 答辩批次表 defense_batch
--    中期/终期共用：batch_type 区分（mid_term / final）；mode 区分线上/线下
--    （线上批次 location 填会议链接，线下批次填教室地址）
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 2. 答辩组成员表 defense_group_member
--    每批次可配 1 名组长（leader）+ N 名成员（member）；组长主持并汇总结论，
--    秘书负责记录与线下回填（秘书字段在 defense_batch.secretary_id，不在本表）
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 3. 答辩记录表 defense_record
--    按论文归档：每篇论文在每个批次有一条记录（(batch_id, paper_id) 唯一）
--    组长汇总后写入 score/grade/result/comment；终期 pass 时由 Service 回写
--    paper_info.final_score 并推进状态 defense_passed
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 4. 答辩相关字典
--    ID 段沿用 P1/P2 约定：72xx 归答辩，避免与 paper_status（70xx）、评阅任务（71xx）冲突。
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 5. 执行后自检：各条应返回非 0
-- ----------------------------------------------------------------------------
-- 5.1 答辩批次表已建：期望 1
SELECT COUNT(*) AS check_defense_batch FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'defense_batch';

-- 5.2 答辩组成员表已建：期望 1
SELECT COUNT(*) AS check_defense_group_member FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'defense_group_member';

-- 5.3 答辩记录表已建：期望 1
SELECT COUNT(*) AS check_defense_record FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'defense_record';

-- 5.4 答辩相关字典条数：期望 18（6 个字典类型 × 合计 18 条数据）
--     sys_dict_data 表无 is_deleted 列，勿加此过滤
SELECT COUNT(*) AS check_defense_dict FROM sys_dict_data
 WHERE dict_type IN ('defense_batch_status', 'defense_batch_type', 'defense_mode',
                     'defense_group_role', 'defense_result', 'defense_grade');
