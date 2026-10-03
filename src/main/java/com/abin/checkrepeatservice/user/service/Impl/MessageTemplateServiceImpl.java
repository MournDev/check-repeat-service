package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.utils.TemplateRenderUtils;
import com.abin.checkrepeatservice.pojo.entity.MessageTemplate;
import com.abin.checkrepeatservice.user.dto.MessageTemplateReq;
import com.abin.checkrepeatservice.user.mapper.MessageTemplateMapper;
import com.abin.checkrepeatservice.user.service.MessageTemplateService;
import com.abin.checkrepeatservice.user.vo.TemplateRenderVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 消息模板管理服务实现
 *
 * message_template 表没有 is_deleted 列，也没有 create_by / update_by：
 * 所以删除是物理删除，create_time / update_time 靠库的
 * DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP 填充。
 */
@Slf4j
@Service
public class MessageTemplateServiceImpl extends ServiceImpl<MessageTemplateMapper, MessageTemplate>
        implements MessageTemplateService {

    @Resource
    private MessageTemplateMapper messageTemplateMapper;

    // ------------------------------ 查询 ------------------------------

    @Override
    public Page<MessageTemplate> getTemplatePage(String templateType, String templateName,
                                                 Integer page, Integer size) {
        Page<MessageTemplate> pageParam = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<MessageTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(templateType)) {
            wrapper.eq(MessageTemplate::getTemplateType, templateType);
        }
        if (StringUtils.hasText(templateName)) {
            wrapper.like(MessageTemplate::getTemplateName, templateName);
        }
        // 同秒创建的多条模板顺序不稳定，用 id 兜底做二级排序（雪花 id 单调递增）
        wrapper.orderByDesc(MessageTemplate::getCreateTime).orderByDesc(MessageTemplate::getId);

        return messageTemplateMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public MessageTemplate getTemplateByCode(String templateCode) {
        if (!StringUtils.hasText(templateCode)) {
            return null;
        }
        LambdaQueryWrapper<MessageTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageTemplate::getTemplateCode, templateCode);
        // template_code 在库里是唯一键，最多一条；LIMIT 1 兜住历史脏数据，避免 selectOne 抛异常
        wrapper.last("LIMIT 1");
        return messageTemplateMapper.selectOne(wrapper);
    }

    @Override
    public List<MessageTemplate> getTemplatesByType(String templateType) {
        LambdaQueryWrapper<MessageTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(templateType)) {
            wrapper.eq(MessageTemplate::getTemplateType, templateType);
        }
        wrapper.orderByAsc(MessageTemplate::getId);
        return messageTemplateMapper.selectList(wrapper);
    }

    // ------------------------------ 写入 ------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTemplate(MessageTemplateReq req) {
        MessageTemplate template = new MessageTemplate();
        template.setTemplateCode(req.getTemplateCode());
        template.setTemplateName(req.getTemplateName());
        template.setTemplateType(req.getTemplateType());
        template.setTitleTemplate(req.getTitleTemplate());
        template.setContentTemplate(req.getContentTemplate());
        template.setIsActive(req.getIsActive() == null ? 1 : req.getIsActive());

        messageTemplateMapper.insert(template);
        log.info("新增消息模板成功: id={}, code={}", template.getId(), template.getTemplateCode());
        return template.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTemplate(MessageTemplateReq req) {
        if (req.getId() == null || messageTemplateMapper.selectById(req.getId()) == null) {
            return false;
        }

        MessageTemplate update = new MessageTemplate();
        update.setId(req.getId());
        update.setTemplateName(req.getTemplateName());
        update.setTemplateType(req.getTemplateType());
        update.setTitleTemplate(req.getTitleTemplate());
        update.setContentTemplate(req.getContentTemplate());
        if (req.getIsActive() != null) {
            update.setIsActive(req.getIsActive());
        }
        // 模板编码是发消息时的定位键，前端在编辑态把它禁用了；这里也不更新，
        // 避免改码后已有调用方（代码里硬编码的 11 个编码）全部失联

        boolean updated = messageTemplateMapper.updateById(update) > 0;
        if (updated) {
            log.info("更新消息模板成功: id={}, code={}", req.getId(), req.getTemplateCode());
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTemplate(Long id) {
        // 该表无 is_deleted 列，这里是物理删除，删掉不可恢复
        boolean deleted = messageTemplateMapper.deleteById(id) > 0;
        if (deleted) {
            log.info("删除消息模板成功（物理删除）: id={}", id);
        }
        return deleted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTemplateStatus(Long id, Integer isActive) {
        if (messageTemplateMapper.selectById(id) == null) {
            return false;
        }
        MessageTemplate update = new MessageTemplate();
        update.setId(id);
        update.setIsActive(isActive);

        boolean updated = messageTemplateMapper.updateById(update) > 0;
        if (updated) {
            log.info("更新消息模板启用状态成功: id={}, isActive={}", id, isActive);
        }
        return updated;
    }

    // ------------------------------ 渲染 ------------------------------

    @Override
    public TemplateRenderVO renderTemplate(String templateCode, Map<String, Object> variables) {
        MessageTemplate template = getTemplateByCode(templateCode);
        if (template == null) {
            return null;
        }
        return new TemplateRenderVO(
                TemplateRenderUtils.render(template.getTitleTemplate(), variables),
                TemplateRenderUtils.render(template.getContentTemplate(), variables));
    }

    // ------------------------------ 内部工具 ------------------------------

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
