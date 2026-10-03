package com.abin.checkrepeatservice.knowledge.service.Impl;

import com.abin.checkrepeatservice.knowledge.mapper.KnowledgeArticleMapper;
import com.abin.checkrepeatservice.knowledge.mapper.KnowledgeCategoryMapper;
import com.abin.checkrepeatservice.knowledge.service.KnowledgeCategoryService;
import com.abin.checkrepeatservice.pojo.entity.KnowledgeArticle;
import com.abin.checkrepeatservice.pojo.entity.KnowledgeCategory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库分类服务实现
 */
@Slf4j
@Service
public class KnowledgeCategoryServiceImpl extends ServiceImpl<KnowledgeCategoryMapper, KnowledgeCategory>
        implements KnowledgeCategoryService {

    /**
     * 分类启用状态标识（与 init.sql 的 DEFAULT 'ACTIVE' 一致）
     */
    private static final String STATUS_ACTIVE = "ACTIVE";

    @Resource
    private KnowledgeCategoryMapper knowledgeCategoryMapper;

    @Resource
    private KnowledgeArticleMapper knowledgeArticleMapper;

    @Override
    public List<KnowledgeCategory> listActiveWithArticleCount() {
        LambdaQueryWrapper<KnowledgeCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeCategory::getStatus, STATUS_ACTIVE)
                // 只按 sort 排序时同值分类顺序不稳定，用 id 兜底做二级排序
                .orderByAsc(KnowledgeCategory::getSort)
                .orderByAsc(KnowledgeCategory::getId);

        List<KnowledgeCategory> categories = knowledgeCategoryMapper.selectList(wrapper);

        // 分类数量是「个位数」量级，逐个 count 比 GROUP BY 更好读；真涨到几百个再换成聚合查询
        for (KnowledgeCategory category : categories) {
            LambdaQueryWrapper<KnowledgeArticle> countWrapper = new LambdaQueryWrapper<>();
            countWrapper.eq(KnowledgeArticle::getCategoryId, category.getId())
                    .eq(KnowledgeArticle::getStatus, "PUBLISHED");
            category.setArticleCount(Math.toIntExact(knowledgeArticleMapper.selectCount(countWrapper)));
        }
        return categories;
    }

    @Override
    public Map<Long, String> getCategoryNameMap(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> ids = categoryIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        // selectBatchIds 会带上逻辑删除条件（is_deleted = 0），已删除分类不会出现在结果里
        List<KnowledgeCategory> categories = knowledgeCategoryMapper.selectBatchIds(ids);

        Map<Long, String> nameMap = new HashMap<>(categories.size());
        for (KnowledgeCategory category : categories) {
            nameMap.put(category.getId(), category.getName());
        }
        return nameMap;
    }

    @Override
    public KnowledgeCategory getByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        LambdaQueryWrapper<KnowledgeCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeCategory::getCode, code);
        // code 在库里是唯一键，最多一条；用 selectOne 若出现重复会抛异常，这里取第一条更稳
        wrapper.last("LIMIT 1");
        return knowledgeCategoryMapper.selectOne(wrapper);
    }
}
