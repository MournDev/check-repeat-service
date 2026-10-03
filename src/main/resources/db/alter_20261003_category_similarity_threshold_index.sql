-- 分类相似度阈值：给解析时的等值查询补索引。
--
-- SimilarityThresholdResolver.categoryThreshold() 固定按
--   WHERE category_type = ? AND college_id = ? / major_id = ?
--   ORDER BY id DESC LIMIT 1
-- 取单行，全表扫描在配置条数少时无感，但每篇论文查重都要查两次
-- （先专业、未命中再学院），条数一多就是纯浪费。
--
-- 表很小，索引可以随时后补；重复执行会因索引已存在而报错，属预期。

ALTER TABLE category_similarity_threshold
    ADD INDEX idx_type_college (category_type, college_id);

ALTER TABLE category_similarity_threshold
    ADD INDEX idx_type_major (category_type, major_id);
