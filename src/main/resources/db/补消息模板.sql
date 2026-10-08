-- ============================================================================
-- 初始消息模板 message_template（19 行）
--
-- 来源：种子数据.sql 段 6 抽出，唯一改动是把 11 行里的 ${key} 写成 {key}（见下）。
--
-- 为什么必须补：
--   MessageServiceImpl.sendMessageByTemplate 先按 template_code 查这一行，
--   查不到就直接返回「消息模板不存在」，那类通知整条发不出去，只在日志里留一句。
--   代码里在用、而真实库/种子只覆盖了 19 个 code，少一个就哑一类。
--
-- 占位符写法（这次改动的原因）：
--   TemplateRenderUtils 只认 {key}。写成 ${key} 并不是"原样输出"那么无害——
--   ${paperTitle} 里正好包含子串 {paperTitle}，会被替换掉，留下一个孤零零的 $，
--   于是通知正文一直是「您的论文《$论文标题》…」。改动前的 11 行（6/7/8/9/10/11/
--   12/16/17/18/19）都有这个毛病，管理端预览走同一个渲染工具
--   （MessageTemplateServiceImpl:158），所以从预览里也看不出来。
--
-- 变量名必须与调用方 params.put(...) 的键完全一致，改模板里的变量名要同步改发送点
-- （见 NotificationServiceImpl 里各 send* 方法的注释）。
--
-- ⚠️ {riskLevel} 传进来时已经是「低风险 / 中等风险 / 高风险 / 极高风险」整词
--    （RiskLevelUtils.of），模板里不要再补「风险」二字，否则渲染成「中等风险风险」。
--
-- 幂等：唯一键 uk_template_code，全部 ON DUPLICATE KEY UPDATE，可重复执行。
--       这张表**没有 is_deleted 列**，不参与软删，删模板是物理删。
-- ============================================================================

SET NAMES utf8mb4;

-- ---------- 1~12、16~19：真实库现有的 16 行 ----------
INSERT INTO `message_template` (`id`, `template_code`, `template_name`, `template_type`, `title_template`, `content_template`, `is_active`, `create_time`, `update_time`) VALUES
(1,'TEACHER_ASSIGNMENT_NOTICE','教师分配通知','assignment','新的论文指导任务待确认','尊敬的{teacherName}老师：\n\n您有一个新的论文指导任务需要确认。\n\n论文标题：{paperTitle}\n论文ID：{paperId}\n\n请于 {deadline} 前在系统中确认是否接收此指导任务。如超时未确认，系统将自动重新分配。\n\n感谢您的配合！',1,'2025-11-22 15:31:00','2025-11-22 15:31:00'),
(2,'TEACHER_CONFIRM_NOTICE','教师确认通知','confirm','论文指导任务已确认','尊敬的{teacherName}老师：\n\n您已成功确认接收论文《{paperTitle}》的指导任务。\n\n请及时开展后续指导工作。',1,'2025-11-22 15:31:00','2025-11-22 15:31:00'),
(3,'TEACHER_REJECT_NOTICE','教师拒绝通知','reject','论文指导任务已拒绝','尊敬的{teacherName}老师：\n\n您已拒绝接收论文《{paperTitle}》的指导任务。\n\n拒绝原因：{rejectReason}',1,'2025-11-22 15:31:00','2025-11-22 15:31:00'),
(4,'STUDENT_CONFIRM_RESULT','学生确认结果通知','confirm','论文指导老师确认结果','同学：\n\n您的论文《{paperTitle}》的指导老师{teacherName}已{confirmed}接收指导任务。\n\n{rejectReason}',1,'2025-11-22 15:31:00','2025-11-22 15:31:00'),
(5,'TIMEOUT_REJECT_NOTICE','超时拒绝通知','reject','论文指导任务超时自动拒绝','亲爱的{studentName}同学：\n\n您的论文《{paperTitle}》的指导老师{teacherName}在指定时间内未确认接收指导任务，系统已自动拒绝并释放该分配。\n\n超时时间：{timeoutTime}\n\n系统将为您重新分配其他指导老师，请耐心等待。',1,'2025-11-22 16:42:02','2025-11-22 16:42:02'),
(6,'SYSTEM_NOTICE','系统通知','system','{title}','{content}\n时间：{sendTime}',1,'2025-11-22 18:27:37','2025-11-22 21:49:04'),
(7,'PAPER_SYSTEM_NOTICE','论文系统通知','system','{title}','{content}\n论文：{paperTitle}\n时间：{sendTime}',1,'2025-11-22 18:27:37','2025-11-22 21:49:16'),
(8,'STUDENT_ASSIGNMENT_RESULT','学生分配结果通知','assignment','指导老师分配成功','亲爱的{studentName}同学：\n您的论文《{paperTitle}》已成功分配指导老师{teacherName}。\n分配时间：{assignmentTime}',1,'2025-11-22 18:27:37','2025-11-22 21:49:24'),
(9,'PAPER_SUBMIT_SUCCESS','论文提交成功','system','论文提交成功','亲爱的{userName}同学：\n您的论文《{paperTitle}》已成功提交，系统将自动分配指导老师并进行查重检测。\n提交时间：{sendTime}',1,'2025-11-22 18:27:37','2025-11-22 21:49:33'),
(10,'PAPER_SUBMIT_FAILED','论文提交失败','system','论文提交失败','亲爱的{userName}同学：\n您的论文《{paperTitle}》提交失败。\n失败原因：{content}\n请稍后重试或联系管理员。',1,'2025-11-22 18:27:37','2025-11-22 21:49:40'),
(11,'ADVISOR_ASSIGN_SUCCESS','指导老师分配成功','assignment','指导老师分配成功','亲爱的{userName}同学：\n您的论文《{paperTitle}》已成功分配指导老师，系统将开始进行查重检测。\n分配时间：{sendTime}',1,'2025-11-22 18:27:37','2025-11-22 21:49:50'),
(12,'ADVISOR_ASSIGN_FAILED','指导老师分配失败','assignment','指导老师分配失败','亲爱的{userName}同学：\n您的论文《{paperTitle}》指导老师分配失败。\n失败原因：{content}\n请稍后重试或联系管理员。',1,'2025-11-22 18:27:37','2025-11-22 21:49:58'),
(16,'CHECK_SUCCESS','查重成功通知','system','查重检测完成','亲爱的{userName}同学：\n您的论文《{paperTitle}》查重检测已完成。\n相似度：{content}%\n检测结果：符合要求，论文已进入审核阶段。',1,'2025-11-22 18:27:37','2025-11-22 21:50:06'),
(17,'CHECK_FAILED','查重失败通知','system','查重检测失败','亲爱的{userName}同学：\n您的论文《{paperTitle}》查重检测失败。\n失败原因：{content}\n请根据要求修改后重新提交。',1,'2025-11-22 18:27:37','2025-11-22 21:50:11'),
(18,'AUDIT_SUCCESS','审核成功通知','system','论文审核通过','恭喜{userName}同学！\n您的论文《{paperTitle}》已通过审核。\n审核意见：{content}\n审核人：{teacherName}',1,'2025-11-22 18:27:37','2025-11-22 21:50:23'),
(19,'AUDIT_FAILED','审核失败通知','system','论文审核未通过','亲爱的{userName}同学：\n您的论文《{paperTitle}》未通过审核。\n审核意见：{content}\n审核人：{teacherName}\n请根据意见修改后重新提交。',1,'2025-11-22 18:27:37','2025-11-22 21:50:31')
ON DUPLICATE KEY UPDATE `template_name` = VALUES(`template_name`), `template_type` = VALUES(`template_type`), `title_template` = VALUES(`title_template`), `content_template` = VALUES(`content_template`), `is_active` = VALUES(`is_active`);

-- ---------------------------------------------------------------------------
-- 13~15：代码正在用、而库里缺 3 个
--
-- 发送点逐个核对过，占位符与实际传入的键一一对应：
--   PAPER_STATUS_CHANGE     NotificationServiceImpl.sendPaperStatusChangeNotification
--                           studentId,paperId,paperTitle,newStatus,remarks,changeTime
--   PAPER_REVIEW_RESULT     NotificationServiceImpl.sendPaperReviewResultNotification
--                           studentId,paperId,paperTitle,reviewResult,reviewComments,reviewTime
--   SIMILARITY_CHECK_RESULT NotificationServiceImpl.sendSimilarityCheckResultNotification
--                           studentId,paperId,paperTitle,similarityRate,riskLevel,checkTime
--
-- 13/14/15 是空闲号（真实库占了 1-12、16-19），不会覆盖任何既有模板。
-- ---------------------------------------------------------------------------
INSERT INTO `message_template`
  (`id`, `template_code`, `template_name`, `template_type`,
   `title_template`, `content_template`, `is_active`)
VALUES
  (13, 'PAPER_STATUS_CHANGE', '论文状态变更通知', 'notice',
   '论文状态变更通知',
   '您的论文《{paperTitle}》状态已更新为：{newStatus}\n备注：{remarks}\n变更时间：{changeTime}\n请及时查看并处理相关事宜。',
   1),

  (14, 'PAPER_REVIEW_RESULT', '论文审核结果通知', 'notice',
   '论文审核结果通知',
   '您的论文《{paperTitle}》审核结果：{reviewResult}\n审核意见：{reviewComments}\n审核时间：{reviewTime}\n请登录系统查看详情。',
   1),

  (15, 'SIMILARITY_CHECK_RESULT', '论文查重结果通知', 'notice',
   '论文查重结果通知',
   '您的论文《{paperTitle}》查重已完成。\n相似度：{similarityRate}%（{riskLevel}）\n查重时间：{checkTime}\n请及时查看详细报告。',
   1)
ON DUPLICATE KEY UPDATE
  `template_name`    = VALUES(`template_name`),
  `template_type`    = VALUES(`template_type`),
  `title_template`   = VALUES(`title_template`),
  `content_template` = VALUES(`content_template`),
  `is_active`        = VALUES(`is_active`);

-- ---------- 自检 ----------
-- 期望 total=19 / active=19 / with_legacy_dollar=0
SELECT COUNT(*)                                              AS total,
       SUM(`is_active` = 1)                                  AS active,
       SUM(title_template LIKE '%${%' OR content_template LIKE '%${%') AS with_legacy_dollar,
       COUNT(DISTINCT `template_code`)                       AS distinct_codes
  FROM `message_template`;

SELECT `id`, `template_code`, `template_name`, `is_active` FROM `message_template` ORDER BY `id`;
