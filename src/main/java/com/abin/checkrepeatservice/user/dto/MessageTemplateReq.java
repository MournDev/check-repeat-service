package com.abin.checkrepeatservice.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 消息模板新增/更新请求
 *
 * 对应前端 src/views/admin/MessageTemplateManagement.vue 的新增/编辑弹窗。
 * 更新接口把 id 一并放在请求体里（前端 updateTemplate(data) 传的是整个 form），
 * 所以这里保留 id 字段。
 */
@Data
public class MessageTemplateReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模板ID（仅更新时使用；新增时忽略）
     */
    private Long id;

    /**
     * 模板编码（唯一，如 PAPER_STATUS_CHANGE）
     */
    @NotBlank(message = "模板编码不能为空")
    @Size(max = 100, message = "模板编码长度不能超过100字符")
    private String templateCode;

    /**
     * 模板名称
     */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称长度不能超过100字符")
    private String templateName;

    /**
     * 模板类型（如 assignment-分配, confirm-确认, reject-拒绝）
     */
    @NotBlank(message = "模板类型不能为空")
    @Size(max = 50, message = "模板类型长度不能超过50字符")
    private String templateType;

    /**
     * 标题模板（占位符写法为 {变量}，见 TemplateRenderUtils）
     */
    @NotBlank(message = "标题模板不能为空")
    @Size(max = 200, message = "标题模板长度不能超过200字符")
    private String titleTemplate;

    /**
     * 内容模板（占位符写法为 {变量}，见 TemplateRenderUtils）
     */
    @NotBlank(message = "内容模板不能为空")
    private String contentTemplate;

    /**
     * 是否启用：0-禁用，1-启用（不传时新增默认启用）
     */
    @Min(value = 0, message = "启用状态只能是0或1")
    @Max(value = 1, message = "启用状态只能是0或1")
    private Integer isActive;
}
