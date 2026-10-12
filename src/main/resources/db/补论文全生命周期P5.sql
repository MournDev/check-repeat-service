-- ============================================================================
-- 补论文全生命周期P5 —— 引擎底座夯实 + 四角色端升级（P5 期）
--
-- ⚠️ 部署顺序（硬约束）：**先在目标库执行本脚本，再部署新 jar**。
--    新版实体（CheckSentenceHit / PaperCheckSignoff / CheckPolicy /
--    CheckCollegeQuota）以及 CheckTask / CheckResult / CheckReport /
--    PaperInfo / SystemParam 的新列均引用本脚本的表与列，
--    漏跑脚本会导致查重任务创建、句级明细、报告验真、签核、
--    成绩合成、检测策略、学院额度等接口全站 500。
--
-- 本脚本幂等（CREATE TABLE IF NOT EXISTS + INSERT IGNORE +
-- information_schema 检查式 ALTER），可重复执行。
-- 依赖：补论文全生命周期P1.sql / P2.sql / P3.sql 已执行。
-- 前置：MySQL 8.0+（建表使用 utf8mb4_0900_ai_ci 排序规则）。
--
-- 内容：
--   1. 存量表扩列（幂等 ALTER）：
--      check_task / check_result 加 max_per_source + citation_rate，
--      check_report 加 content_hash（报告验真），
--      paper_info 加 degree_type（存量默认本科）+ 索引，
--      system_param 加成绩加权三项权重（默认 20/30/50）
--   2. 新表：check_sentence_hit（句级标红明细）/ paper_check_signoff（报告签核）
--           / check_policy（学位检测策略集）/ check_college_quota（学院月度额度）
--   3. 字典：degree_type（73xx 段）
--   4. 执行后自检
--
-- 设计要点（见 .trae/documents/p5引擎底座夯实与四角色端升级实施计划.md）：
--   * 指标口径升级（仅对新查重任务生效，存量行不迁移）：
--     check_rate / paper_info.similarity_rate 语义升级为
--     「总相似比 = 非引用命中句字数 / 正文字数」（对齐行业「总文字复制比」），
--     max_per_source = 单篇最大相似比，citation_rate = 引用率；
--     句级分析失败时降级回落旧口径（整篇最大余弦）。
--   * check_report.content_hash = SHA-256(关键要素 JSON + 盐)，
--     盐取环境变量 REPORT_HASH_SALT（默认 check-repeat-report-salt），
--     公开验真接口 GET /api/report/verify/{reportNo} 重算比对。
--   * paper_check_signoff 唯一键 (paper_id, task_id)：
--     论文重新提交再检产生新任务后可再次签核。
--   * check_college_quota 唯一键 (college_id, quota_month)：
--     跨月自然重置，无需清零任务。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0. 权限预检（可选，若已由 DBA 提前授权可跳过）
--    注意：不要 GRANT information_schema，它随业务库权限自动可见。
-- ----------------------------------------------------------------------------
-- GRANT ALL PRIVILEGES ON `check-repeat-system`.* TO 'root'@'%';
-- FLUSH PRIVILEGES;

-- ----------------------------------------------------------------------------
-- 1. 存量表扩列（MySQL 不支持 ADD COLUMN IF NOT EXISTS，
--    用 information_schema + 预处理语句做幂等，范式同 P2）
--    拼接统一用相邻字符串常量，不依赖 sql_mode 的 PIPES_AS_CONCAT。
-- ----------------------------------------------------------------------------

-- 1.1 check_task 加单篇最大相似比 + 引用率
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

-- 1.2 check_result 加单篇最大相似比 + 引用率
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

-- 1.3 check_report 加内容哈希（报告验真）
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

-- 1.4 paper_info 加学位类型（存量行随 DEFAULT 回填为本科）+ 查询索引
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

-- 1.5 system_param 加成绩加权三项权重（指导/评阅/答辩，默认 20/30/50）
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
-- 2. 新表（CREATE TABLE IF NOT EXISTS，均含完整审计字段）
-- ----------------------------------------------------------------------------

-- 2.1 句级标红明细表 check_sentence_hit
--     每次查重任务对正文逐句与候选论文（整篇海明预筛通过者）句级比对，
--     命中（句相似度 ≥ check.sentence.threshold，默认 50%）的句子逐条落库；
--     学生端检测明细页与来源聚合列表的数据源。
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

-- 2.2 查重报告导师签核表 paper_check_signoff
--     导师对论文最新一次查重结果做「合格/不合格」签核并给出指导评分（0-100）；
--     check.signoff.required=true（默认）时，评阅分配前必须存在 qualified=1 记录。
--     唯一键 (paper_id, task_id)：论文重新提交再检产生新任务后可再次签核。
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

-- 2.3 学位检测策略集 check_policy
--     按学位类型差异化配置检测策略，解决单一全局阈值无法区分本科/硕士/博士的问题；
--     超管端 CRUD 维护，CheckPolicyResolver 按论文 degree_type 命中策略，
--     未命中回落 SimilarityThresholdResolver 既有链路。
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

-- 2.4 学院月度查重额度表 check_college_quota
--     管理端按学院+月份分配查重额度，查重任务创建时原子扣减；
--     唯一键 (college_id, quota_month)，跨月自然重置无需清零任务。
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

-- ----------------------------------------------------------------------------
-- 3. 学位类型字典
--    ID 段沿用 P1/P2/P3 约定：70xx 论文状态、71xx 评阅、72xx 答辩，73xx 归学位类型。
-- ----------------------------------------------------------------------------
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

-- ----------------------------------------------------------------------------
-- 4. 执行后自检：各条应返回期望值
-- ----------------------------------------------------------------------------
-- 4.1 新表已建：各期望 1
SELECT COUNT(*) AS check_check_sentence_hit FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'check_sentence_hit';
SELECT COUNT(*) AS check_paper_check_signoff FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'paper_check_signoff';
SELECT COUNT(*) AS check_check_policy FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'check_policy';
SELECT COUNT(*) AS check_check_college_quota FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'check_college_quota';

-- 4.2 存量表新列已加：期望 9
--     （check_task 2 + check_result 2 + check_report 1 + paper_info 1 + system_param 3）
SELECT COUNT(*) AS check_new_columns FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND ((table_name = 'check_task'   AND column_name IN ('max_per_source', 'citation_rate'))
     OR (table_name = 'check_result' AND column_name IN ('max_per_source', 'citation_rate'))
     OR (table_name = 'check_report' AND column_name = 'content_hash')
     OR (table_name = 'paper_info'   AND column_name = 'degree_type')
     OR (table_name = 'system_param' AND column_name IN ('guidance_weight', 'review_weight', 'defense_weight')));

-- 4.3 学位类型字典数据条数：期望 ≥3（本脚本插入 3 条；历史人工数据可能使条数更多）
--     sys_dict_data 表无 is_deleted 列，勿加此过滤
SELECT COUNT(*) AS check_degree_dict FROM sys_dict_data
 WHERE dict_type = 'degree_type';