-- ============================================================================
-- 初始查重规则 check_rule（4 条，其中 1 条默认）
--
-- 来源：种子数据.sql 段 7 逐字节抽出，未做任何改动。
--
-- 为什么必须补：
--   学生端「当前查重规则」卡片走 GET /api/student/check-rule/current，
--   取 is_default=1 且未删除的那条；一条都取不到就返回 404/40401
--   「未配置默认查重规则」，卡片一直报错。
--
-- 取值沿用老库现有的这 4 条（id / 阈值 / 次数 / 间隔 / 描述逐字段照抄）：
--   UNDERGRADUATE 本科（默认）  MASTER 硕士  PHD 博士  COURSEWORK 课程作业
--
-- 六个容易踩的点：
--   1) id 是写死的固定值。命中主键就更新业务列，本段可重复执行；
--      空表跑完就是这 4 条。
--   2) is_default 只在 INSERT 时给，**更新列表里故意不含它**。否则库里若已有一条
--      默认规则，再跑一遍就会出现两条 is_default=1。读取端按 id 倒序取第一条，
--      线上自建规则的雪花 id 远大于这里的固定 id，所以真正生效的仍是线上那条。
--   3) pass_threshold 是 NOT NULL 的历史列，但**已废弃**：管理端编辑规则时不写它，
--      真正生效的合格线由 SimilarityThresholdResolver 按「专业 > 学院 > 全局 >
--      yml 默认 20.00」解析。这里填的值只为满足 NOT NULL，不参与判定，
--      所以它和界面上显示的阈值对不上是正常的。
--   4) compare_lib 同样是 NOT NULL 的历史列，存的是库名 JSON 文本，和 compare_lib
--      表没有任何关联（判定时走 CheckTaskServiceImpl.loadCompareLibraryData，全量取
--      「启用 + LOCAL」的库）。照抄老库取值即可，不要当成外键去改。
--   5) create_by / update_by 沿用老库的 10001 / 10002。sys_user 里没有这两个 id
--      （该列无外键约束，代码也不读），仅作溯源记录，别去补用户。
--   6) 本段不碰 is_deleted：某条规则被管理端软删过的话，重跑本段不会把它恢复。
--
-- ⚠️ check_rule 上只有主键，没有 rule_code 唯一键（DDL 注释说"唯一"但没建），
--    所以 ON DUPLICATE KEY UPDATE 只对固定 id 生效。
-- ============================================================================

SET NAMES utf8mb4;

INSERT INTO `check_rule`
  (`id`, `rule_name`, `rule_code`, `pass_threshold`, `max_re_submit_count`,
   `compare_lib`, `check_interval`, `max_check_count`, `is_default`, `description`,
   `create_by`, `update_by`)
VALUES
  (1623456789001, '本科毕业论文查重规则', 'UNDERGRADUATE', 20.00, 3,
   '["校内论文库","知网期刊库","互联网资源"]', 86400, 5, 1,
   '适用于本科毕业论文查重，重复率≤20%为合格', 10001, 10001),

  (1623456789002, '硕士研究生论文查重规则', 'MASTER', 15.00, 3,
   '["校内论文库","知网硕博库","知网期刊库","万方数据库","互联网资源"]', 172800, 3, 0,
   '适用于硕士研究生论文查重，重复率≤15%为合格', 10001, 10002),

  (1623456789003, '博士研究生论文查重规则', 'PHD', 10.00, 3,
   '["校内论文库","知网硕博库","知网期刊库","万方数据库","维普数据库","互联网资源","国际学术库"]', 259200, 2, 0,
   '适用于博士研究生论文查重，重复率≤10%为合格', 10002, 10002),

  (1623456789005, '课程作业查重规则', 'COURSEWORK', 30.00, 3,
   '["校内作业库","互联网资源"]', 3600, 1, 0,
   '适用于课程作业查重，重复率≤30%为合格', 10001, 10001)
ON DUPLICATE KEY UPDATE
  `rule_name`           = VALUES(`rule_name`),
  `rule_code`           = VALUES(`rule_code`),
  `pass_threshold`      = VALUES(`pass_threshold`),
  `max_re_submit_count` = VALUES(`max_re_submit_count`),
  `compare_lib`         = VALUES(`compare_lib`),
  `check_interval`      = VALUES(`check_interval`),
  `max_check_count`     = VALUES(`max_check_count`),
  `description`         = VALUES(`description`);
  -- is_default / create_by / update_by / is_deleted 故意不更新，原因见上面 2) 5) 6)

-- ---------- 自检 ----------
-- 期望 rules=4 / default_rules=1；default_rules=0 卡片就报 404，>1 学生端拿到的是
-- id 最大的那条（容易与预期不符）。
SELECT (SELECT COUNT(*) FROM `check_rule` WHERE `is_deleted` = 0)                   AS rules,
       (SELECT COUNT(*) FROM `check_rule` WHERE `is_deleted` = 0 AND `is_default` = 1) AS default_rules;

-- 学生端「当前查重规则」实际会取到的那一条
SELECT `id`, `rule_code`, `rule_name`, `max_check_count`, `max_re_submit_count`, `check_interval`
  FROM `check_rule`
 WHERE `is_deleted` = 0 AND `is_default` = 1
 ORDER BY `id` DESC LIMIT 1;

SELECT `id`, `rule_code`, `rule_name`, `is_default`, `check_interval`, `max_check_count`
  FROM `check_rule` WHERE `is_deleted` = 0 ORDER BY `id`;
