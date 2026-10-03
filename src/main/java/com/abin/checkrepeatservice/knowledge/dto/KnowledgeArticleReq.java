package com.abin.checkrepeatservice.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库文章新增/更新请求
 *
 * 对应前端 src/views/admin/KnowledgeManagement.vue 的编辑表单。
 * 注意：author_id / author_name / view_count 不在此处，它们由服务端填充，
 * 不接受前端传入（否则作者和浏览量可以被伪造）。
 */
@Data
public class KnowledgeArticleReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文章标题
     */
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 200, message = "文章标题长度不能超过200字符")
    private String title;

    /**
     * 正文（Markdown）
     */
    @NotBlank(message = "文章内容不能为空")
    private String content;

    /**
     * 摘要（选填）
     */
    @Size(max = 500, message = "摘要长度不能超过500字符")
    private String summary;

    /**
     * 所属分类ID（选填）
     */
    private Long categoryId;

    /**
     * 标签（逗号分隔，选填）
     */
    @Size(max = 500, message = "标签长度不能超过500字符")
    private String tags;

    /**
     * 状态：DRAFT-草稿，PUBLISHED-已发布，ARCHIVED-已归档
     */
    @Pattern(regexp = "^(DRAFT|PUBLISHED|ARCHIVED)$", message = "文章状态只能是DRAFT、PUBLISHED或ARCHIVED")
    private String status;

    /**
     * 排序值（选填，越小越靠前）
     */
    private Integer sort;
}
