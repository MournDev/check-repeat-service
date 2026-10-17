package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.MessageTemplate;
import com.abin.checkrepeatservice.user.dto.MessageTemplateReq;
import com.abin.checkrepeatservice.user.service.MessageTemplateService;
import com.abin.checkrepeatservice.user.vo.TemplateRenderVO;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 管理端「消息模板管理」控制器
 *
 * 对应前端 src/api/admin/templates.js（页面 views/admin/MessageTemplateManagement.vue）。
 *
 * ⚠️ 路径前缀是 /api/message-templates，不在 /api/admin/** 之下 —— 因此必须在
 * SecurityConfig 里单列一条 ADMIN 规则，理由同 /api/notices/**。
 *
 * ⚠️ 表 message_template 里【没有种子数据】时，代码中硬编码引用的 11 个模板编码
 * （PAPER_STATUS_CHANGE 等）都会查不到模板而发送失败，需要在管理页录入或跑种子脚本。
 */
@RestController
@RequestMapping("/api/message-templates")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
public class MessageTemplateController {

    @Resource
    private MessageTemplateService messageTemplateService;

    /**
     * 分页查询模板列表（含已禁用）
     *
     * 出参：data.records / data.total（MyBatis-Plus Page 的序列化结构）
     */
    @GetMapping("/list")
    public Result<Page<MessageTemplate>> getTemplateList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String templateType,
            @RequestParam(required = false) String templateName) {
        log.info("接收消息模板列表请求: page={}, size={}, templateType={}, templateName={}",
                page, size, templateType, templateName);
        try {
            return Result.success(messageTemplateService.getTemplatePage(templateType, templateName, page, size));
        } catch (Exception e) {
            log.error("查询消息模板列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询消息模板列表失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 按编码查询模板（含已禁用，管理端编辑用）
     */
    @GetMapping("/code/{templateCode}")
    public Result<MessageTemplate> getTemplateByCode(@PathVariable("templateCode") String templateCode) {
        try {
            MessageTemplate template = messageTemplateService.getTemplateByCode(templateCode);
            if (template == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "消息模板不存在");
            }
            return Result.success(template);
        } catch (Exception e) {
            log.error("按编码查询消息模板失败: code={}, {}", templateCode, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "按编码查询消息模板失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 按类型查询模板列表（含已禁用）
     */
    @GetMapping("/type/{templateType}")
    public Result<List<MessageTemplate>> getTemplatesByType(@PathVariable("templateType") String templateType) {
        try {
            return Result.success(messageTemplateService.getTemplatesByType(templateType));
        } catch (Exception e) {
            log.error("按类型查询消息模板失败: type={}, {}", templateType, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "按类型查询消息模板失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 新增模板
     */
    @PostMapping("/create")
    @OperationLog(type = "message_template_create", description = "新增消息模板")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<Long> createTemplate(@Valid @RequestBody MessageTemplateReq req) {
        log.info("接收新增消息模板请求: code={}, name={}", req.getTemplateCode(), req.getTemplateName());
        try {
            // template_code 是唯一键，先给出可读的提示；并发下仍由库的唯一约束兜底
            if (messageTemplateService.getTemplateByCode(req.getTemplateCode()) != null) {
                return Result.error(ResultCode.PARAM_ERROR, "模板编码已存在：" + req.getTemplateCode());
            }
            return Result.success("创建成功", messageTemplateService.createTemplate(req));
        } catch (Exception e) {
            log.error("新增消息模板失败: code={}, {}", req.getTemplateCode(), e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "新增消息模板失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 更新模板（id 在请求体里）
     */
    @PutMapping("/update")
    @OperationLog(type = "message_template_update", description = "更新消息模板")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<String> updateTemplate(@Valid @RequestBody MessageTemplateReq req) {
        log.info("接收更新消息模板请求: id={}, code={}", req.getId(), req.getTemplateCode());
        if (req.getId() == null) {
            return Result.error(ResultCode.PARAM_ERROR, "模板ID不能为空");
        }
        try {
            if (!messageTemplateService.updateTemplate(req)) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "消息模板不存在");
            }
            return Result.success("更新成功", null);
        } catch (Exception e) {
            log.error("更新消息模板失败: id={}, {}", req.getId(), e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新消息模板失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 删除模板（物理删除，不可恢复）
     */
    @DeleteMapping("/delete/{id}")
    @OperationLog(type = "message_template_delete", description = "删除消息模板")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<String> deleteTemplate(@PathVariable("id") Long id) {
        log.info("接收删除消息模板请求: id={}", id);
        try {
            if (!messageTemplateService.deleteTemplate(id)) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "消息模板不存在");
            }
            return Result.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除消息模板失败: id={}, {}", id, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除消息模板失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 启用/禁用模板
     *
     * isActive 走查询参数（前端 toggleTemplateStatus 用的是 params 而非 body）
     */
    @PutMapping("/status/{id}")
    @OperationLog(type = "message_template_status_update", description = "更新消息模板启用状态")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Result<String> updateTemplateStatus(@PathVariable("id") Long id,
                                               @RequestParam(required = false) Integer isActive) {
        log.info("接收更新消息模板状态请求: id={}, isActive={}", id, isActive);
        if (isActive == null || (isActive != 0 && isActive != 1)) {
            return Result.error(ResultCode.PARAM_ERROR, "启用状态只能是0或1");
        }
        try {
            if (!messageTemplateService.updateTemplateStatus(id, isActive)) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "消息模板不存在");
            }
            return Result.success(isActive == 1 ? "已启用" : "已禁用", null);
        } catch (Exception e) {
            log.error("更新消息模板状态失败: id={}, {}", id, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新消息模板状态失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 渲染模板预览（不发送）
     *
     * 请求体是【裸的变量对象】如 {"userName":"张三"}，不是包装结构；
     * 前端按钮在没填变量时也会 POST 一个空对象，所以 required = false。
     */
    @PostMapping("/render/{templateCode}")
    public Result<TemplateRenderVO> renderTemplate(@PathVariable("templateCode") String templateCode,
                                                   @RequestBody(required = false) Map<String, Object> variables) {
        log.info("接收消息模板渲染请求: code={}, 变量数={}",
                templateCode, variables == null ? 0 : variables.size());
        try {
            TemplateRenderVO rendered = messageTemplateService.renderTemplate(
                    templateCode, variables == null ? Collections.emptyMap() : variables);
            if (rendered == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "消息模板不存在");
            }
            return Result.success(rendered);
        } catch (Exception e) {
            log.error("渲染消息模板失败: code={}, {}", templateCode, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "渲染消息模板失败: " + "系统异常，请稍后重试");
        }
    }
}
