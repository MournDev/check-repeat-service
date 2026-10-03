package com.abin.checkrepeatservice.knowledge.mapper;

import com.abin.checkrepeatservice.pojo.entity.KnowledgeArticle;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 知识库文章Mapper接口
 *
 * 分页/筛选统一走 MyBatis-Plus 的 LambdaQueryWrapper（实体驱动，列清单由实体决定）。
 * 这里只保留一条手写 SQL：浏览数自增。写成 `view_count = view_count + 1` 而不是
 * 先查后写，避免并发阅读时互相覆盖。
 */
@Mapper
public interface KnowledgeArticleMapper extends BaseMapper<KnowledgeArticle> {

    /**
     * 浏览次数 +1
     *
     * 显式带上 is_deleted = 0：这条是手写 SQL，不走 MP 的逻辑删除注入，
     * 不加会被软删除的文章也能累加浏览数。
     *
     * @param id 文章ID
     * @return 影响行数（0 表示文章不存在或已删除）
     */
    @Update("UPDATE knowledge_article SET view_count = view_count + 1 WHERE id = #{id} AND is_deleted = 0")
    int incrementViewCount(@Param("id") Long id);
}
