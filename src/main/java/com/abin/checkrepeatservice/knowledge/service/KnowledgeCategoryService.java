package com.abin.checkrepeatservice.knowledge.service;

import com.abin.checkrepeatservice.pojo.entity.KnowledgeCategory;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 知识库分类服务接口
 */
public interface KnowledgeCategoryService extends IService<KnowledgeCategory> {

    /**
     * 查询启用中的分类（按 sort 升序），并回填每个分类下的已发布文章数
     *
     * 前端 HelpCenter 的分类徽标读的是 articleCount，库里没有该列，
     * 必须在这里算出来，否则徽标恒为空。
     */
    List<KnowledgeCategory> listActiveWithArticleCount();

    /**
     * 按ID批量取分类名，供文章列表回填 categoryName（避免逐行查库）
     *
     * @param categoryIds 分类ID集合（允许含 null，内部过滤）
     * @return 分类ID -> 分类名；查不到的ID不会出现在结果里
     */
    Map<Long, String> getCategoryNameMap(Collection<Long> categoryIds);

    /**
     * 按分类编码查分类
     *
     * @param code 分类编码
     * @return 分类；不存在返回 null
     */
    KnowledgeCategory getByCode(String code);
}
