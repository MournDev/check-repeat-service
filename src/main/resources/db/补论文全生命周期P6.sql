-- ============================================================================
-- 补论文全生命周期P6.sql —— 导师评价反馈（学生对指导教师评价，P6a）
-- ----------------------------------------------------------------------------
-- 执行顺序：在 P1/P2/P3/P5 脚本之后、部署新 jar 之前执行（幂等，可重复执行）。
-- 内容：
--   1. 新表：advisor_evaluation（导师评价）
--   2. 字典：advisor_eval_tag（74xx 段，评价预设标签）
--   3. 执行后自检
--
-- 设计要点（对应 docs/P6 方案：学生→教师反向评价反馈环）：
--   * 每篇论文一次：uk_paper_id 唯一约束兜底，提交后不可改（防施压改评）；
--   * 时机：paper_status = defense_passed / finalized 后开放
--    （软提醒——不卡阶段门，终期答辩 pass 回写处发邀请通知）；
--   * 窗口：defense_passed 后 admin.advisor-eval.window-days（默认 30）天内可评，
--     超窗提交抛 40919；
--   * 匿名性：库存 student_id（管理端实名可查、防滥用追溯），
--     教师端 API 只做聚合查询、永不返回单条明细，样本数 <3 时返回掩码；
--   * 结果用途：导师考核输入（导师评价分 = avg(score_overall)/5×100），
--     不进学生成绩合成（ScoreComposeService 不动，切断利益关联）；
--   * 评价窗口起点：paper_status_log 中最新一条 new_status='defense_passed' 的时间。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 新表（CREATE TABLE IF NOT EXISTS，含完整审计字段）
-- ----------------------------------------------------------------------------

-- 1.1 导师评价表 advisor_evaluation
--     学生对指导教师的结构化评价：5 维度 1-5 星 + 预设标签 JSON + 选填文字评语。
--     teacher_id 冗余自 paper_info.teacher_id（提交时刻快照），
--     后续改派导师不影响已提交评价的归属统计。
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
-- 2. 评价标签字典（74xx 段；70xx 论文状态 / 71xx 评阅 / 72xx 答辩 / 73xx 学位类型）
--    学生提交时 tags 从本字典点选（可多选），后端按 dict_value 白名单校验。
--    负向标签与正向标签同列，由前端按 remark 展示分组。
-- ----------------------------------------------------------------------------

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
  (7406, 6, '指导偏少',   'guidance_lacking',   'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '负向'),
  (7407, 7, '反馈较慢',   'feedback_slow',      'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '负向');

-- ----------------------------------------------------------------------------
-- 3. 执行后自检
-- ----------------------------------------------------------------------------

-- 3.1 表存在性：期望 1
SELECT COUNT(*) AS check_advisor_evaluation_table
  FROM information_schema.tables
 WHERE table_schema = DATABASE() AND table_name = 'advisor_evaluation';

-- 3.2 唯一键 uk_paper_id：期望 1
SELECT COUNT(*) AS check_uk_paper_id
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND table_name = 'advisor_evaluation'
   AND index_name = 'uk_paper_id';

-- 3.3 标签字典条数：期望 7（sys_dict_data 表无 is_deleted 列，勿加此过滤）
SELECT COUNT(*) AS check_eval_tag_dict
  FROM sys_dict_data
 WHERE dict_type = 'advisor_eval_tag';
