-- ============================================================================
-- 补论文全生命周期P1 —— 阶段门状态机扩展 + 指导记录（P1 期）
--
-- ⚠️ 部署顺序（硬约束）：**先在目标库执行本脚本，再部署新 jar**。
--    新版实体（GuidanceRecord / PaperStatusLog 字段调整）引用了本脚本的表与列，
--    漏跑脚本会导致指导记录、论文时间线等接口全站 500。
--
-- 本脚本幂等（CREATE TABLE IF NOT EXISTS + INSERT IGNORE），可重复执行。
-- 适用范围：任何「结构落后于 P1 代码」的库；空库跑 init.sql 时无需本文件
--           （但执行也无副作用，后续可择机把结构合并进 init.sql）。
--
-- 内容：
--   1. 指导记录表 guidance_record（教师发起 → 学生确认，多轮次）
--   2. 论文状态字典 paper_status：补齐原有 7 个状态 + P1 新增 6 个阶段门状态
--      （reviewing / mid_defense / final_defense / defense_passed / finalized / archived）
--   3. 执行后自检
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 指导记录表
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 2. 论文状态字典（paper_status）
--    历史上种子数据从未给 paper_status 播种，前端状态标签一直靠硬编码兜底；
--    这里一次性补齐 7 个存量状态 + 6 个 P1 新增阶段门状态，ID 固定 7001~7013。
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 3. 执行后自检：三条都应返回非 0
-- ----------------------------------------------------------------------------
-- 3.1 指导记录表已建：期望 1
SELECT COUNT(*) AS check_guidance_table FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'guidance_record';

-- 3.2 论文状态字典条数：期望 13（sys_dict_data 表无 is_deleted 列，勿加此过滤）
SELECT COUNT(*) AS check_paper_status_dict FROM sys_dict_data
 WHERE dict_type = 'paper_status';

-- 3.3 字典类型已注册：期望 1
SELECT COUNT(*) AS check_paper_status_type FROM sys_dict_type
 WHERE dict_type = 'paper_status';
