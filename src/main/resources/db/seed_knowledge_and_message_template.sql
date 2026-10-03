-- ============================================================================
-- 增量种子数据：知识库分类 + 消息模板
--
-- 用途：给【已有数据的库】补这两张表的初始数据。
--       db/init.sql 开头是 DROP TABLE IF EXISTS，只能对空库整份执行；
--       本文件不含任何 DDL，可安全地对生产库重复执行（幂等）。
--
-- 为什么需要这份数据：
--   1) knowledge_category 为空时，知识库管理页/帮助中心的分类下拉与分类徽标全空。
--   2) message_template 为空时，代码里硬编码引用的 11 个模板编码
--      （PAPER_STATUS_CHANGE、NEW_PAPER_SUBMISSION、TEACHER_REJECT_NOTICE 等）
--      在 MessageServiceImpl.sendMessageByTemplate 里全部走到「消息模板不存在」，
--      论文状态变更 / 审核结果 / 查重完成 / 指导分配等通知一条都发不出去。
--
-- 幂等方式：靠 template_code / code 上的唯一键做 ON DUPLICATE KEY UPDATE。
--          主键 id 显式给值（两列都是 bigint NOT NULL，无 AUTO_INCREMENT，不能省略），
--          取值 1~11 不会与 MP 生成的雪花ID 冲突。
--
-- 执行：docker exec -i check-repeat-mysql sh -c \
--         'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "check-repeat-system"' \
--         < db/seed_knowledge_and_message_template.sql
-- ============================================================================

-- 必须先声明连接字符集：本文件含中文，客户端若按 latin1 解析会写进乱码。
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. 知识库分类（前端按 code 筛选文章；articleCount 由接口实时统计，不是库里的列）
-- ---------------------------------------------------------------------------
INSERT INTO `knowledge_category` (`id`, `code`, `name`, `icon`, `sort`, `status`, `is_deleted`) VALUES
  (1, 'guide',        '使用指南', 'Notebook',     10, 'ACTIVE', 0),
  (2, 'faq',          '常见问题', 'QuestionFilled', 20, 'ACTIVE', 0),
  (3, 'format',       '格式规范', 'Document',     30, 'ACTIVE', 0),
  (4, 'plagiarism',   '查重规则', 'View',         40, 'ACTIVE', 0),
  (5, 'process',      '流程说明', 'Service',      50, 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE
  `name`   = VALUES(`name`),
  `icon`   = VALUES(`icon`),
  `sort`   = VALUES(`sort`),
  `status` = VALUES(`status`),
  `is_deleted` = 0;

-- ---------------------------------------------------------------------------
-- 2. 消息模板
--
-- 占位符写法：统一为 {key}。渲染工具（TemplateRenderUtils）只认这一种写法，
--            写成 ${key} 会原样输出、不会被替换；前端模板管理页的输入提示语也是这个格式。
--
-- ⚠️ 模板里的变量名必须与调用方 params.put(...) 的键完全一致，否则会原样留下
--    "{paperTitle}" 这种字面量。下表是从调用点逐个核对出来的：
--      PAPER_STATUS_CHANGE      studentId,paperId,paperTitle,newStatus,remarks,changeTime
--      PAPER_REVIEW_RESULT      studentId,paperId,paperTitle,reviewResult,reviewComments,reviewTime
--      NEW_PAPER_SUBMISSION     teacherId,studentId,studentName,paperId,paperTitle,submissionTime
--      SIMILARITY_CHECK_RESULT  studentId,paperId,paperTitle,similarityRate,riskLevel,checkTime
--      TEACHER_ASSIGNMENT_NOTICE teacherName,paperTitle,paperId,studentName,deadline
--      STUDENT_ASSIGNMENT_RESULT studentName,paperTitle,teacherName,assignmentTime
--      TEACHER_CONFIRM_NOTICE   studentName,paperTitle,teacherName,confirmTime
--      TEACHER_REJECT_NOTICE    studentName,paperTitle,teacherName,rejectReason,rejectTime
--      TIMEOUT_REJECT_NOTICE    studentName,paperTitle,teacherName,timeoutTime
--      SYSTEM_NOTICE            userName,title,content,noticeType,sendTime[,relatedId]
--      PAPER_SYSTEM_NOTICE      paperTitle,paperId,title,content,noticeType,sendTime,studentName
--
-- ★ TEACHER_CONFIRM_NOTICE / TEACHER_REJECT_NOTICE 各有两个发送方，原先传的变量不同：
--     InternalMessageNotificationService → studentName,paperTitle,teacherName,+confirmTime/rejectTime
--     TeacherAssignmentServiceImpl       → paperTitle,teacherName,confirmed,confirmTime
--   两者只有 paperTitle / teacherName 是共有的，所以模板一度只敢引用这两个变量。
--   现已统一：TeacherAssignmentServiceImpl 改为传与 InternalMessageNotificationService
--   完全相同的键名（并去掉用不上的 confirmed），两个模板可以放心引用上面的完整变量集。
-- ---------------------------------------------------------------------------
INSERT INTO `message_template`
  (`id`, `template_code`, `template_name`, `template_type`, `title_template`, `content_template`, `is_active`)
VALUES
  (1, 'PAPER_STATUS_CHANGE', '论文状态变更通知', 'notice',
   '论文状态变更通知',
   '您的论文《{paperTitle}》状态已更新为：{newStatus}\n备注：{remarks}\n变更时间：{changeTime}\n请及时查看并处理相关事宜。',
   1),

  (2, 'PAPER_REVIEW_RESULT', '论文审核结果通知', 'notice',
   '论文审核结果通知',
   '您的论文《{paperTitle}》审核结果：{reviewResult}\n审核意见：{reviewComments}\n审核时间：{reviewTime}\n请登录系统查看详情。',
   1),

  (3, 'NEW_PAPER_SUBMISSION', '新论文提交通知（发给指导教师）', 'notice',
   '新论文提交通知',
   '您的指导学生 {studentName} 提交了新论文《{paperTitle}》。\n提交时间：{submissionTime}\n请及时审核。',
   1),

  (4, 'SIMILARITY_CHECK_RESULT', '论文查重结果通知', 'notice',
   '论文查重结果通知',
   '您的论文《{paperTitle}》查重已完成。\n相似度：{similarityRate}%（{riskLevel}风险）\n查重时间：{checkTime}\n请及时查看详细报告。',
   1),

  (5, 'TEACHER_ASSIGNMENT_NOTICE', '指导任务分配通知（发给教师）', 'assignment',
   '指导任务分配通知',
   '{teacherName} 老师，您已被分配指导学生 {studentName} 的论文《{paperTitle}》。\n请于 {deadline} 前完成确认。',
   1),

  (6, 'STUDENT_ASSIGNMENT_RESULT', '指导老师分配结果通知（发给学生）', 'assignment',
   '指导老师分配结果通知',
   '{studentName} 同学，您的论文《{paperTitle}》已分配指导老师：{teacherName}。\n分配时间：{assignmentTime}',
   1),

  (7, 'TEACHER_CONFIRM_NOTICE', '教师确认接收通知', 'confirm',
   '指导老师确认通知',
   '{studentName} 同学，您的论文《{paperTitle}》的指导老师 {teacherName} 已确认接收。\n确认时间：{confirmTime}\n请登录系统查看详情。',
   1),

  (8, 'TEACHER_REJECT_NOTICE', '教师拒绝接收通知', 'reject',
   '指导老师拒绝通知',
   '{studentName} 同学，您的论文《{paperTitle}》的指导老师 {teacherName} 已拒绝接收。\n拒绝原因：{rejectReason}\n拒绝时间：{rejectTime}\n请登录系统查看详情并重新选择指导老师。',
   1),

  (9, 'TIMEOUT_REJECT_NOTICE', '超时自动拒绝通知', 'reject',
   '指导申请超时自动拒绝',
   '{studentName} 同学，您的论文《{paperTitle}》因指导老师 {teacherName} 超时未确认，已于 {timeoutTime} 自动拒绝。\n请重新选择指导老师。',
   1),

  (10, 'SYSTEM_NOTICE', '系统通知（通用）', 'notice',
   '{title}',
   '{userName} 您好：\n{content}\n通知类型：{noticeType}\n发送时间：{sendTime}',
   1),

  (11, 'PAPER_SYSTEM_NOTICE', '论文系统通知', 'notice',
   '{title}',
   '{studentName} 同学，关于论文《{paperTitle}》：\n{content}\n通知类型：{noticeType}\n发送时间：{sendTime}',
   1)
ON DUPLICATE KEY UPDATE
  `template_name`    = VALUES(`template_name`),
  `template_type`    = VALUES(`template_type`),
  `title_template`   = VALUES(`title_template`),
  `content_template` = VALUES(`content_template`),
  `is_active`        = VALUES(`is_active`);
