package com.abin.checkrepeatservice.knowledge.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.knowledge.service.KnowledgeArticleService;
import com.abin.checkrepeatservice.knowledge.service.KnowledgeCategoryService;
import com.abin.checkrepeatservice.pojo.entity.KnowledgeArticle;
import com.abin.checkrepeatservice.pojo.entity.KnowledgeCategory;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/**
 * 知识库「学生端」控制器
 *
 * 对应前端 src/api/knowledge.js 的学生端部分（页面 views/student/HelpCenter.vue）。
 *
 * ⚠️ 路径保持 /api/knowledge/**，与前端既有 api 封装一致；管理端在同一前缀下的
 * /api/knowledge/admin/**（见 KnowledgeAdminController），并在 SecurityConfig 里
 * 单独加了一条 ADMIN 规则 —— /api/knowledge/** 本身不在 /api/admin/** 之下，
 * 靠 anyRequest().authenticated() 只能保证「登录可见」，不是管理员专属。
 *
 * ⚠️ 参数名 page / size 是 admin 系接口的主流约定，本模块统一沿用。
 */
@RestController
@RequestMapping("/api/knowledge")
@Slf4j
@Tag(name = "知识库", description = "学生端知识库分类、文章查询与搜索")
public class KnowledgeController {

    @Resource
    private KnowledgeArticleService knowledgeArticleService;

    @Resource
    private KnowledgeCategoryService knowledgeCategoryService;

    /**
     * 分类列表（含每个分类下已发布文章数）
     */
    @Operation(summary = "分类列表", description = "含每个分类下已发布文章数")
    @GetMapping("/categories")
    public Result<List<KnowledgeCategory>> getCategories() {
        return Result.success(knowledgeCategoryService.listActiveWithArticleCount());
    }

    /**
     * 已发布文章分页列表（不含正文）
     *
     * @param category 分类编码（对应 knowledge_category.code），不是分类ID
     */
    @Operation(summary = "已发布文章分页列表", description = "返回文章列表，不含正文")
    @GetMapping("/articles")
    public Result<Page<KnowledgeArticle>> getArticles(
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "分类编码（可选，对应 knowledge_category.code）")
            @RequestParam(required = false) String category,
            @Parameter(description = "搜索关键字（可选）")
            @RequestParam(required = false) String keyword) {
        log.info("接收知识库文章列表请求: page={}, size={}, category={}, keyword={}", page, size, category, keyword);
        return Result.success(knowledgeArticleService.getPublishedArticlePage(category, keyword, page, size));
    }

    /**
     * 热门文章（按浏览次数倒序）
     */
    @Operation(summary = "热门文章", description = "按浏览次数倒序返回")
    @GetMapping("/articles/popular")
    public Result<List<KnowledgeArticle>> getPopularArticles(@Parameter(description = "返回条数，默认 5") @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(knowledgeArticleService.getPopularArticles(limit));
    }

    /**
     * 文章详情（仅已发布可见），并累加浏览次数
     *
     * 注意与 /articles/popular 的关系：两条都是 4 段路径，Spring 的路径匹配会优先
     * 命中字面量 popular，不会走到这里把 "popular" 当成 id 解析。
     */
    @Operation(summary = "文章详情", description = "仅已发布文章可见，并累加浏览次数")
    @GetMapping("/articles/{id}")
    public Result<KnowledgeArticle> getArticle(@Parameter(description = "文章ID", required = true) @PathVariable("id") Long id) {
        KnowledgeArticle article = knowledgeArticleService.getPublishedArticleDetail(id);
        if (article == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "文章不存在或未发布");
        }
        return Result.success(article);
    }

    /**
     * 文章搜索（标题或摘要命中，仅已发布）
     */
    @Operation(summary = "文章搜索", description = "按标题或摘要命中，仅已发布文章")
    @GetMapping("/search")
    public Result<Page<KnowledgeArticle>> searchArticles(
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "搜索关键字（可选）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "分类编码（可选）")
            @RequestParam(required = false) String category) {
        log.info("接收知识库文章搜索请求: page={}, size={}, keyword={}, category={}", page, size, keyword, category);
        return Result.success(knowledgeArticleService.getPublishedArticlePage(category, keyword, page, size));
    }
}
