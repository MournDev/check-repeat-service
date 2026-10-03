package com.abin.checkrepeatservice.knowledge.service;

import com.abin.checkrepeatservice.knowledge.dto.KnowledgeArticleReq;
import com.abin.checkrepeatservice.pojo.entity.KnowledgeArticle;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 知识库文章服务接口
 *
 * Service 只返回领域对象 / 布尔结果，不返回 Result —— 统一由 Controller 包装成 Result。
 * 与最新的 NoticeController + SysNoticeService 一套保持一致。
 */
public interface KnowledgeArticleService extends IService<KnowledgeArticle> {

    /**
     * 管理端分页查询文章（含草稿/归档，含正文，供编辑回填）
     *
     * 返回 MyBatis-Plus 的 Page（序列化为 {records,total,size,current,pages}），
     * 不要换成 PageResultVO —— 它没有 total 字段，前端分页器会静默显示 0。
     *
     * @param keyword 标题模糊筛选（null/空=不筛选）
     * @param status  状态筛选：DRAFT/PUBLISHED/ARCHIVED（null/空=不筛选）
     * @param page    页码（从1开始）
     * @param size    每页条数
     */
    Page<KnowledgeArticle> getAdminArticlePage(String keyword, String status, Integer page, Integer size);

    /**
     * 学生端分页查询已发布文章（不含正文，正文走 getPublishedArticleDetail）
     *
     * @param categoryCode 分类编码筛选（null/空=全部，对应 knowledge_category.code）
     * @param keyword      关键字（同时匹配标题与摘要，null/空=不筛选）
     * @param page         页码（从1开始）
     * @param size         每页条数
     */
    Page<KnowledgeArticle> getPublishedArticlePage(String categoryCode, String keyword, Integer page, Integer size);

    /**
     * 学生端热门文章（按浏览次数倒序，仅已发布，不含正文）
     *
     * @param limit 条数（内部收敛到 1~50）
     */
    List<KnowledgeArticle> getPopularArticles(Integer limit);

    /**
     * 学生端文章详情（仅 PUBLISHED 可见），并把浏览次数 +1
     *
     * @param id 文章ID
     * @return 文章；不存在 / 未发布返回 null
     */
    KnowledgeArticle getPublishedArticleDetail(Long id);

    /**
     * 新增文章（作者信息取当前登录用户，不接受前端传入）
     *
     * @return 新增文章的ID
     */
    Long createArticle(KnowledgeArticleReq req);

    /**
     * 更新文章
     *
     * @return true=更新成功；false=文章不存在或已删除
     */
    boolean updateArticle(Long id, KnowledgeArticleReq req);

    /**
     * 更新文章状态（上架/下架/归档）
     *
     * @param status DRAFT/PUBLISHED/ARCHIVED，由 Controller 校验取值
     * @return true=更新成功；false=文章不存在或已删除
     */
    boolean updateArticleStatus(Long id, String status);

    /**
     * 删除文章（逻辑删除，行仍在库里）
     *
     * @return true=删除成功；false=文章不存在或已删除
     */
    boolean deleteArticle(Long id);
}
