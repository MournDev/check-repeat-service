package com.abin.checkrepeatservice.knowledge.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.knowledge.dto.KnowledgeArticleReq;
import com.abin.checkrepeatservice.knowledge.service.KnowledgeArticleService;
import com.abin.checkrepeatservice.pojo.entity.KnowledgeArticle;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Set;

/**
 * 知识库「管理端」控制器
 *
 * 对应前端 src/api/knowledge.js 的管理端部分（页面 views/admin/KnowledgeManagement.vue）。
 *
 * ⚠️ 权限：真正的门禁在 SecurityConfig 的 URL 规则
 * `.requestMatchers("/api/knowledge/admin/**").hasAuthority("ADMIN")`。类上的 @PreAuthorize
 * 是纵深防御，但【不能只有它】—— GlobalExceptionHandler 只有一个 @ExceptionHandler(Exception.class)
 * 兜底、没有 AccessDeniedException 处理器，方法级安全拒绝会被兜底成 HTTP 500，
 * 而过滤器链上的规则才能给出正确的 403（同 NoticeController 的处理）。
 */
@RestController
@RequestMapping("/api/knowledge/admin/articles")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
@Tag(name = "知识库管理", description = "管理端知识库文章的增删改查与状态管理")
public class KnowledgeAdminController {

    /**
     * 合法的文章状态，与前端筛选下拉、实体注释保持一致
     */
    private static final Set<String> VALID_STATUS = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");

    @Resource
    private KnowledgeArticleService knowledgeArticleService;

    /**
     * 分页查询文章列表（含草稿/归档；含正文，编辑弹窗要回填）
     *
     * 出参：data.records / data.total（MyBatis-Plus Page 的序列化结构）
     */
    @Operation(summary = "分页查询文章列表", description = "含草稿/归档文章及正文，供编辑弹窗回填")
    @GetMapping
    public Result<Page<KnowledgeArticle>> listArticles(
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "搜索关键字（可选）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "文章状态（可选）")
            @RequestParam(required = false) String status) {
        log.info("接收知识库文章管理列表请求: page={}, size={}, keyword={}, status={}", page, size, keyword, status);
        return Result.success(knowledgeArticleService.getAdminArticlePage(keyword, status, page, size));
    }

    /**
     * 新增文章（作者取当前登录管理员）
     */
    @Operation(summary = "新增文章", description = "作者取当前登录管理员")
    @PostMapping
    @OperationLog(type = "knowledge_article_create", description = "新增知识库文章")
    public Result<Long> createArticle(@Parameter(description = "知识库文章新增请求体", required = true) @Valid @RequestBody KnowledgeArticleReq req) {
        log.info("接收新增知识库文章请求: title={}", req.getTitle());
        Long id = knowledgeArticleService.createArticle(req);
        return Result.success("创建成功", id);
    }

    /**
     * 更新文章
     */
    @Operation(summary = "更新文章")
    @PutMapping("/{id}")
    @OperationLog(type = "knowledge_article_update", description = "更新知识库文章")
    public Result<String> updateArticle(@Parameter(description = "文章ID", required = true) @PathVariable("id") Long id,
                                        @Parameter(description = "知识库文章更新请求体", required = true) @Valid @RequestBody KnowledgeArticleReq req) {
        log.info("接收更新知识库文章请求: id={}, title={}", id, req.getTitle());
        if (!knowledgeArticleService.updateArticle(id, req)) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "文章不存在或已删除");
        }
        return Result.success("更新成功", null);
    }

    /**
     * 更新文章状态（发布 / 下架归档）
     *
     * 前端把 status 放在请求体里（{ "status": "PUBLISHED" }），不是查询参数。
     */
    @Operation(summary = "更新文章状态", description = "发布 / 下架归档，status 放在请求体中")
    @PutMapping("/{id}/status")
    @OperationLog(type = "knowledge_article_status_update", description = "更新知识库文章状态")
    public Result<String> updateArticleStatus(@Parameter(description = "文章ID", required = true) @PathVariable("id") Long id,
                                              @Parameter(description = "请求体：status 为 DRAFT/PUBLISHED/ARCHIVED", required = true) @RequestBody Map<String, String> body) {
        String status = body == null ? null : body.get("status");
        log.info("接收更新知识库文章状态请求: id={}, status={}", id, status);
        if (status == null || !VALID_STATUS.contains(status)) {
            return Result.error(ResultCode.PARAM_ERROR, "文章状态只能是DRAFT、PUBLISHED或ARCHIVED");
        }
        if (!knowledgeArticleService.updateArticleStatus(id, status)) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "文章不存在或已删除");
        }
        return Result.success("PUBLISHED".equals(status) ? "已发布" : "已下架", null);
    }

    /**
     * 删除文章（逻辑删除）
     */
    @Operation(summary = "删除文章", description = "逻辑删除")
    @DeleteMapping("/{id}")
    @OperationLog(type = "knowledge_article_delete", description = "删除知识库文章")
    public Result<String> deleteArticle(@Parameter(description = "文章ID", required = true) @PathVariable("id") Long id) {
        log.info("接收删除知识库文章请求: id={}", id);
        if (!knowledgeArticleService.deleteArticle(id)) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "文章不存在或已删除");
        }
        return Result.success("删除成功", null);
    }
}
