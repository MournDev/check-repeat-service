package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.pojo.entity.MessageTemplate;
import com.abin.checkrepeatservice.user.dto.MessageTemplateReq;
import com.abin.checkrepeatservice.user.vo.TemplateRenderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 消息模板管理服务接口
 *
 * 与 MessageService.sendMessageByTemplate（按模板发消息，只认启用模板）不同，
 * 本接口面向「消息模板管理」页，查询要能看到【已禁用】的模板，否则管理员无法重新启用。
 */
public interface MessageTemplateService extends IService<MessageTemplate> {

    /**
     * 分页查询模板（含已禁用）
     *
     * 返回 MyBatis-Plus 的 Page（序列化为 {records,total,size,current,pages}）。
     *
     * @param templateType 模板类型筛选（null/空=不筛选）
     * @param templateName 模板名称模糊筛选（null/空=不筛选）
     * @param page         页码（从1开始）
     * @param size         每页条数
     */
    Page<MessageTemplate> getTemplatePage(String templateType, String templateName, Integer page, Integer size);

    /**
     * 按编码查模板（含已禁用，供管理端编辑/预览使用）
     *
     * 注意与 MessageTemplateMapper.selectByCode 的区别：那个带 `is_active = 1` 硬过滤，
     * 是给「按模板发消息」用的，管理端拿它会查不到已禁用的模板。
     *
     * @return 模板；不存在返回 null
     */
    MessageTemplate getTemplateByCode(String templateCode);

    /**
     * 按类型查模板列表（含已禁用）
     */
    List<MessageTemplate> getTemplatesByType(String templateType);

    /**
     * 新增模板
     *
     * @return 新增模板的ID
     */
    Long createTemplate(MessageTemplateReq req);

    /**
     * 更新模板（id 取自请求体）
     *
     * @return true=更新成功；false=模板不存在
     */
    boolean updateTemplate(MessageTemplateReq req);

    /**
     * 删除模板（该表没有 is_deleted 列，是物理删除）
     *
     * @return true=删除成功；false=模板不存在
     */
    boolean deleteTemplate(Long id);

    /**
     * 启用/禁用模板
     *
     * @param isActive 0-禁用，1-启用
     * @return true=更新成功；false=模板不存在
     */
    boolean updateTemplateStatus(Long id, Integer isActive);

    /**
     * 按编码渲染模板（仅预览，不发送）
     *
     * @param variables 变量表，键对应模板里的 {key} / ${key}
     * @return 渲染结果；模板不存在返回 null
     */
    TemplateRenderVO renderTemplate(String templateCode, Map<String, Object> variables);
}
