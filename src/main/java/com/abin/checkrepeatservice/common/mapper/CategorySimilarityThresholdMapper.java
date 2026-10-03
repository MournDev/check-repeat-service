package com.abin.checkrepeatservice.common.mapper;

import com.abin.checkrepeatservice.pojo.entity.CategorySimilarityThreshold;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学科/专业相似度阈值 Mapper 接口
 */
@Mapper
public interface CategorySimilarityThresholdMapper extends BaseMapper<CategorySimilarityThreshold> {

    /**
     * 清空整张表（物理删除）。
     *
     * 为什么不用 mapper.delete(null)：实体上的 @TableLogic 会把 delete 改写成
     * UPDATE ... SET is_deleted = 1 的软删。而 PUT 的语义是"提交完整集合"，
     * 整表覆盖最简且幂等；走软删则每次保存都堆积一批再也读不出来的垃圾行。
     * 配置表无审计价值，故直接物理删除。
     */
    @Delete("DELETE FROM category_similarity_threshold")
    int deleteAll();

}
