package com.abin.checkrepeatservice.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 审核意见模板新增/更新请求。
 *
 * <p>字段名对齐前端表单（ReviewTemplates.vue 的 templateForm）的 camelCase，
 * 新建和编辑共用同一个 DTO —— 两者的可写字段完全相同，区分只体现在 URL 上
 * （POST /createTemplate 与 PUT /{id}）。</p>
 *
 * <p>id / createBy / usageCount / lastUsed 都不在这里：它们要么由路径参数给出，
 * 要么由服务端维护，不该由客户端指定（否则可以把使用次数刷成任意值）。</p>
 */
@Data
public class ReviewTemplateSaveReq {

    /** 模板标题 */
    @NotBlank(message = "模板标题不能为空")
    @Size(max = 255, message = "模板标题不能超过255个字符")
    private String title;

    /** 模板分类，取值由前端定义，后端不校验 */
    @Size(max = 100, message = "模板分类不能超过100个字符")
    private String category;

    /** 模板内容 */
    @NotBlank(message = "模板内容不能为空")
    private String content;

    /** 使用场景标签列表，落库前序列化成 JSON 数组文本 */
    private List<String> scenarios;

    /** 是否公开 */
    private Boolean isPublic;

}
