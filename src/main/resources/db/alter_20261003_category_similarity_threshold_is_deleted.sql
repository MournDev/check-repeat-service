-- ============================================================================
-- 增量 DDL：修正 category_similarity_threshold.is_deleted 的默认值
--
-- 用途：给【已有数据的库】补这个修正。
--       db/init.sql 开头是 DROP TABLE IF EXISTS，只能对空库整份执行；
--       本文件是对已存在库的增量脚本。
--
-- 问题：
--   init.sql 里 category_similarity_threshold.is_deleted 定义成 `int DEFAULT NULL`，
--   而它的姊妹表 similarity_threshold、以及 init.sql 其余所有表都是 `DEFAULT '0'`。
--   这一处不一致会让新写的行【插进去就读不回来】：
--
--     1. BaseEntity.isDeleted 只标了 @TableLogic，没有 fill = FieldFill.INSERT；
--     2. 而 MyMetaObjectHandler.insertFill 用的是 strictInsertFill，
--        该重载只填充带 fill 注解的字段（见 MyMetaObjectHandler.java:39）——
--        所以插入时应用侧【不会】补这个值；
--     3. DB 默认值又是 NULL，于是列里落的是 NULL；
--     4. MyBatis-Plus 后续所有查询都会拼上 `is_deleted = 0`
--        （全局配置 logic-not-delete-value: 0），而 SQL 里 NULL = 0 结果为 NULL，
--        永远不成立 —— 行被静默过滤掉。
--
--   表现：相似度阈值页保存成功，刷新后列表恒为空，且没有任何报错。
--
-- 与本脚本配套的代码侧保险：
--   SimilarityThresholdServiceImpl 插入前仍会显式 setIsDeleted(0)。
--   两处都做，任一处失效都不影响结果。
--
-- 执行顺序：先跑本脚本，再发布带 SimilarityThreshold 实体与
--           /api/admin/similarity-thresholds 接口的代码。
-- ============================================================================

ALTER TABLE `category_similarity_threshold`
    MODIFY COLUMN `is_deleted` int DEFAULT '0' COMMENT '软删除标记（0-未删除，1-已删除）';

-- 顺带把历史遗留的 NULL 行归零，否则本脚本执行前若已插入过数据，那些行依然读不出来。
UPDATE `category_similarity_threshold` SET `is_deleted` = 0 WHERE `is_deleted` IS NULL;
