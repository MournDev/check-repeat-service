package com.abin.checkrepeatservice.knowledge.service.Impl;

import com.abin.checkrepeatservice.knowledge.dto.KnowledgeArticleReq;
import com.abin.checkrepeatservice.knowledge.mapper.KnowledgeArticleMapper;
import com.abin.checkrepeatservice.knowledge.service.KnowledgeArticleService;
import com.abin.checkrepeatservice.knowledge.service.KnowledgeCategoryService;
import com.abin.checkrepeatservice.pojo.entity.KnowledgeArticle;
import com.abin.checkrepeatservice.pojo.entity.KnowledgeCategory;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库文章服务实现
 *
 * create_time / update_time 由库的 DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP
 * 填充，insert / update 前【不要】setCreateTime / setUpdateTime。
 */
@Slf4j
@Service
public class KnowledgeArticleServiceImpl extends ServiceImpl<KnowledgeArticleMapper, KnowledgeArticle>
        implements KnowledgeArticleService {

    /**
     * 文章状态：已发布（学生端唯一可见的状态）
     */
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    /**
     * 文章状态：草稿（新增时不传 status 的兜底，与库里 DEFAULT 'DRAFT' 一致）
     */
    private static final String STATUS_DRAFT = "DRAFT";

    /**
     * 列表页不返回正文：knowledge_article.content 是 mediumtext，
     * 一页 12 条 Markdown 正文会让列表接口体积膨胀到几十 KB。
     * 正文只在管理端列表（编辑要回填）和详情接口里给。
     */
    private static final String COLUMN_CONTENT = "content";

    @Resource
    private KnowledgeArticleMapper knowledgeArticleMapper;

    @Resource
    private KnowledgeCategoryService knowledgeCategoryService;

    // ------------------------------ 查询 ------------------------------

    @Override
    public Page<KnowledgeArticle> getAdminArticlePage(String keyword, String status, Integer page, Integer size) {
        Page<KnowledgeArticle> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<KnowledgeArticle> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(KnowledgeArticle::getTitle, keyword);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(KnowledgeArticle::getStatus, status);
        }
        // 同秒创建的多篇文章顺序不稳定，翻页会重复/漏行，用雪花 id 兜底做二级排序
        wrapper.orderByDesc(KnowledgeArticle::getCreateTime).orderByDesc(KnowledgeArticle::getId);

        Page<KnowledgeArticle> result = knowledgeArticleMapper.selectPage(pageParam, wrapper);
        fillCategoryNames(result.getRecords());
        return result;
    }

    @Override
    public Page<KnowledgeArticle> getPublishedArticlePage(String categoryCode, String keyword, Integer page, Integer size) {
        Page<KnowledgeArticle> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<KnowledgeArticle> wrapper = new LambdaQueryWrapper<>();

        // 分类筛选传的是编码，先换成ID；编码不存在时直接返回空页，不要退化成「不筛选」
        if (StringUtils.hasText(categoryCode)) {
            KnowledgeCategory category = knowledgeCategoryService.getByCode(categoryCode);
            if (category == null) {
                // 编码不存在 → 空页。new Page<>(current, size) 的 records 是空集合、total 是 0
                return new Page<>(normalizePage(page), normalizeSize(size));
            }
            wrapper.eq(KnowledgeArticle::getCategoryId, category.getId());
        }
        if (StringUtils.hasText(keyword)) {
            // 关键字同时匹配标题与摘要；不括起来会和上面的 eq 变成 or 优先级错误
            String kw = keyword.trim();
            wrapper.and(w -> w.like(KnowledgeArticle::getTitle, kw)
                    .or().like(KnowledgeArticle::getSummary, kw));
        }
        wrapper.eq(KnowledgeArticle::getStatus, STATUS_PUBLISHED)
                .select(KnowledgeArticle.class, field -> !COLUMN_CONTENT.equals(field.getColumn()))
                .orderByDesc(KnowledgeArticle::getCreateTime)
                .orderByDesc(KnowledgeArticle::getId);

        Page<KnowledgeArticle> result = knowledgeArticleMapper.selectPage(pageParam, wrapper);
        fillCategoryNames(result.getRecords());
        return result;
    }

    @Override
    public List<KnowledgeArticle> getPopularArticles(Integer limit) {
        int size = (limit == null || limit < 1) ? 5 : Math.min(limit, 50);
        // searchCount=false：只要记录，不需要总数，省一次 count
        Page<KnowledgeArticle> pageParam = new Page<>(1, size, false);

        LambdaQueryWrapper<KnowledgeArticle> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeArticle::getStatus, STATUS_PUBLISHED)
                .select(KnowledgeArticle.class, field -> !COLUMN_CONTENT.equals(field.getColumn()))
                // 浏览量相同时用 id 兜底，避免每次刷新顺序乱跳
                .orderByDesc(KnowledgeArticle::getViewCount)
                .orderByDesc(KnowledgeArticle::getId);

        List<KnowledgeArticle> records = knowledgeArticleMapper.selectPage(pageParam, wrapper).getRecords();
        fillCategoryNames(records);
        return records;
    }

    @Override
    public KnowledgeArticle getPublishedArticleDetail(Long id) {
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null || !STATUS_PUBLISHED.equals(article.getStatus())) {
            return null;
        }

        // 浏览数 +1；即使这一步失败也不该让详情打不开，所以只记日志不下发错误
        if (knowledgeArticleMapper.incrementViewCount(id) > 0) {
            article.setViewCount(article.getViewCount() == null ? 1L : article.getViewCount() + 1);
        } else {
            log.warn("知识库文章浏览数自增失败（详情仍正常返回）: id={}", id);
        }

        fillCategoryNames(List.of(article));
        return article;
    }

    // ------------------------------ 写入 ------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createArticle(KnowledgeArticleReq req) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(req.getTitle());
        article.setContent(req.getContent());
        article.setSummary(req.getSummary());
        article.setCategoryId(req.getCategoryId());
        article.setTags(req.getTags());
        article.setSort(req.getSort() == null ? 0 : req.getSort());
        article.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : STATUS_DRAFT);
        // 作者取当前登录管理员，不接受前端传入，否则作者可以被伪造
        article.setAuthorId(UserBusinessInfoUtils.getCurrentUserId());
        article.setAuthorName(UserBusinessInfoUtils.getCurrentRealName());
        article.setViewCount(0L);
        article.setIsDeleted(0);

        knowledgeArticleMapper.insert(article);
        log.info("新增知识库文章成功: id={}, title={}, status={}", article.getId(), article.getTitle(), article.getStatus());
        return article.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateArticle(Long id, KnowledgeArticleReq req) {
        if (knowledgeArticleMapper.selectById(id) == null) {
            return false;
        }

        KnowledgeArticle update = new KnowledgeArticle();
        update.setId(id);
        update.setTitle(req.getTitle());
        update.setContent(req.getContent());
        update.setSummary(req.getSummary());
        update.setTags(req.getTags());
        if (req.getCategoryId() != null) {
            update.setCategoryId(req.getCategoryId());
        }
        if (req.getSort() != null) {
            update.setSort(req.getSort());
        }
        if (StringUtils.hasText(req.getStatus())) {
            update.setStatus(req.getStatus());
        }

        boolean updated = knowledgeArticleMapper.updateById(update) > 0;
        if (updated) {
            log.info("更新知识库文章成功: id={}, title={}", id, req.getTitle());
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateArticleStatus(Long id, String status) {
        if (knowledgeArticleMapper.selectById(id) == null) {
            return false;
        }
        KnowledgeArticle update = new KnowledgeArticle();
        update.setId(id);
        update.setStatus(status);

        boolean updated = knowledgeArticleMapper.updateById(update) > 0;
        if (updated) {
            log.info("更新知识库文章状态成功: id={}, status={}", id, status);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteArticle(Long id) {
        // @TableLogic + 全局 logic-delete-field=isDeleted → 这里是逻辑删除，行仍在库里
        boolean deleted = knowledgeArticleMapper.deleteById(id) > 0;
        if (deleted) {
            log.info("删除知识库文章成功（逻辑删除）: id={}", id);
        }
        return deleted;
    }

    // ------------------------------ 内部工具 ------------------------------

    /**
     * 批量回填 categoryName，避免每行再查一次分类表
     */
    private void fillCategoryNames(Collection<KnowledgeArticle> articles) {
        if (articles == null || articles.isEmpty()) {
            return;
        }
        Set<Long> categoryIds = articles.stream()
                .map(KnowledgeArticle::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = knowledgeCategoryService.getCategoryNameMap(categoryIds);
        for (KnowledgeArticle article : articles) {
            if (article.getCategoryId() != null) {
                article.setCategoryName(nameMap.get(article.getCategoryId()));
            }
        }
    }

    private int normalizePage(Integer page) {
        return (page == null || page < 1) ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        // 上限 100，防止一个请求把整表拖出来
        return Math.min(size, 100);
    }
}
