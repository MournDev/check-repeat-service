-- ============================================================================
-- 补论文全生命周期P2 —— 多人评阅任务 + ReviewWorkflow 接线初审（P2 期）
--
-- ⚠️ 部署顺序（硬约束）：**先在目标库执行本脚本，再部署新 jar**。
--    新版实体（ReviewAssignment / ReviewWorkflowProgress / PaperInfo.workflow_step）
--    引用了本脚本的表与列，漏跑脚本会导致评阅分配、多级初审等接口全站 500。
--
-- 本脚本幂等（CREATE TABLE IF NOT EXISTS + INSERT IGNORE + 列存在性判断），
-- 可重复执行。依赖：补论文全生命周期P1.sql 已执行（paper_status 字典已扩展）。
--
-- 内容：
--   1. 评阅任务表 review_assignment（1~N 名教师评阅，线上直接评 / 线下回填）
--   2. 多级初审投票表 review_workflow_progress（ReviewWorkflow steps 的执行留痕）
--   3. paper_info 增加 workflow_step 列（当前多级初审步骤，0=未进入工作流）
--   4. 评阅任务字典（状态 / 类型 / 方式）
--   5. 执行后自检
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 评阅任务表
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 2. 多级初审投票表（ReviewWorkflow 配置壳的真实驱动留痕）
--    步骤定义仍在 review_workflow.steps（JSON），本表只记录「谁在哪一步投了什么票」；
--    all/any 语义由 Service 层按本表投票记录裁决。
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 3. paper_info 增加当前工作流步骤列
--    MySQL 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema + 预处理语句做幂等。
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 4. 评阅任务字典（状态 / 类型 / 方式）
--    ID 段沿用 P1 约定：71xx 归评阅任务，避免与 paper_status（70xx）冲突。
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 5. 执行后自检：各条应返回非 0
-- ----------------------------------------------------------------------------
-- 5.1 评阅任务表已建：期望 1
SELECT COUNT(*) AS check_review_assignment FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'review_assignment';

-- 5.2 多级初审投票表已建：期望 1
SELECT COUNT(*) AS check_workflow_progress FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'review_workflow_progress';

-- 5.3 workflow_step 列已加：期望 1
SELECT COUNT(*) AS check_paper_workflow_step FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'paper_info' AND column_name = 'workflow_step';

-- 5.4 评阅任务字典条数：期望 9（sys_dict_data 表无 is_deleted 列，勿加此过滤）
SELECT COUNT(*) AS check_review_assign_dict FROM sys_dict_data
 WHERE dict_type IN ('review_assign_status', 'review_assign_type', 'review_assign_method');
