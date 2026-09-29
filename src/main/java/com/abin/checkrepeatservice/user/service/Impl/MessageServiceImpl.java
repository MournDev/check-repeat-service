package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.websocket.WebSocketMessage;
import com.abin.checkrepeatservice.common.websocket.WebSocketSender;
import com.abin.checkrepeatservice.pojo.entity.MessageTemplate;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import com.abin.checkrepeatservice.user.dto.MessageSendDTO;
import com.abin.checkrepeatservice.user.mapper.MessageTemplateMapper;
import com.abin.checkrepeatservice.user.mapper.SystemMessageMapper;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MessageServiceImpl implements MessageService {

    @Resource
    private SystemMessageMapper systemMessageMapper;

    @Resource
    private MessageTemplateMapper messageTemplateMapper;

    @Resource
    private WebSocketSender webSocketSender;

    @Override
    public Result<PageResultVO<SystemMessage>> getMessageList(Long userId, String messageType,
                                                              Integer isRead, Integer pageNum, Integer pageSize,
                                                              String sortBy, String sortOrder) {
        try {
            // 参数校验和默认值设置
            if (pageNum == null || pageNum < 1) pageNum = 1;
            if (pageSize == null || pageSize < 1 || pageSize > 100) pageSize = 20;
            
            // 设置默认排序
            if (sortBy == null || sortBy.trim().isEmpty()) {
                sortBy = "createTime";
            }
            if (sortOrder == null || !sortOrder.toLowerCase().matches("^(asc|desc)$")) {
                sortOrder = "desc";
            }
            
            // 构建查询条件
            LambdaQueryWrapper<SystemMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SystemMessage::getReceiverId, userId)
                   .eq(SystemMessage::getIsDeleted, 0);
            
            // 消息类型筛选
            if (StringUtils.hasText(messageType)) {
                wrapper.eq(SystemMessage::getMessageType, messageType);
            }
            
            // 已读状态筛选
            if (isRead != null) {
                wrapper.eq(SystemMessage::getIsRead, isRead);
            }
            
            // 过期消息过滤
            wrapper.gt(SystemMessage::getExpireTime, LocalDateTime.now());
            
            // 排序
            if ("createTime".equals(sortBy)) {
                if ("desc".equalsIgnoreCase(sortOrder)) {
                    wrapper.orderByDesc(SystemMessage::getCreateTime);
                } else {
                    wrapper.orderByAsc(SystemMessage::getCreateTime);
                }
            } else if ("priority".equals(sortBy)) {
                if ("desc".equalsIgnoreCase(sortOrder)) {
                    wrapper.orderByDesc(SystemMessage::getPriority, SystemMessage::getCreateTime);
                } else {
                    wrapper.orderByAsc(SystemMessage::getPriority, SystemMessage::getCreateTime);
                }
            }
            
            // 分页查询
            Page<SystemMessage> page = new Page<>(pageNum, pageSize);
            Page<SystemMessage> resultPage = systemMessageMapper.selectPage(page, wrapper);
            
            // 转换为PageResultVO格式
            PageResultVO<SystemMessage> pageResult = new PageResultVO<>(
                    (int) resultPage.getCurrent(),
                    (int) resultPage.getSize(),
                    (int) resultPage.getTotal(),
                    (int) resultPage.getPages(),
                    resultPage.getRecords()
            );
            
            log.debug("获取消息列表成功 - 用户ID: {}, 总数: {}, 当前页: {}", 
                     userId, resultPage.getTotal(), resultPage.getCurrent());
            
            return Result.success("获取消息列表成功", pageResult);
            
        } catch (Exception e) {
            log.error("获取消息列表失败 - 用户ID: {}", userId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取消息列表失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> sendMessage(SystemMessage message) {
        try {
            if (message.getReceiverId() == null) {
                return Result.error(ResultCode.PARAM_ERROR,"接收者ID不能为空");
            }
            if (!StringUtils.hasText(message.getTitle())) {
                return Result.error(ResultCode.PARAM_ERROR,"消息标题不能为空");
            }
            if (!StringUtils.hasText(message.getContent())) {
                return Result.error(ResultCode.PARAM_ERROR,"消息内容不能为空");
            }

            // 设置默认值
            if (message.getSenderId() == null) {
                message.setSenderId(0L); // 系统发送
            }
            if (message.getIsRead() == null) {
                message.setIsRead(0);
            }
            if (message.getPriority() == null) {
                message.setPriority(DictConstants.MessagePriority.NORMAL);
            }
            if (message.getExpireTime() == null) {
                message.setExpireTime(LocalDateTime.now().plusDays(7)); // 默认7天过期
            }

            message.setCreateTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());

            int result = systemMessageMapper.insert(message);
            if (result > 0) {
                pushNotification(message);
                return Result.success("消息发送成功", true);
            }
            return Result.error(ResultCode.SYSTEM_ERROR,"消息发送失败");

        } catch (Exception e) {
            log.error("发送站内信失败 - 接收者: {}, 标题: {}", message.getReceiverId(), message.getTitle(), e);
            return Result.error(ResultCode.SYSTEM_ERROR,"消息发送异常");
        }
    }

    /**
     * 站内信落库后实时推送给接收者（前端订阅 /user/queue/messages）
     *
     * 推送只是"锦上添花"：接收者不在线时 WebSocketSender 会直接返回，
     * 消息本身已在库里，前端下次拉未读数就能看到。因此这里的任何异常
     * 都只记日志，绝不能影响消息发送的结果。
     */
    private void pushNotification(SystemMessage message) {
        try {
            WebSocketMessage wsMessage = new WebSocketMessage();
            wsMessage.setType("NOTIFICATION");
            wsMessage.setContent(message);
            wsMessage.setSenderId(message.getSenderId());
            wsMessage.setReceiverId(message.getReceiverId());
            webSocketSender.sendToUser(message.getReceiverId(), "/queue/messages", wsMessage);
        } catch (Exception e) {
            log.warn("站内信实时推送失败（不影响消息入库）- 接收者: {}", message.getReceiverId(), e);
        }
    }

    @Override
    public Result<Boolean> sendBusinessMessage(String messageType, Long receiverId,
                                               Long relatedId, String relatedType,
                                               String title, String content) {
        try {
            SystemMessage message = new SystemMessage();
            message.setTitle(title);
            message.setContent(content);
            message.setMessageType(messageType);
            message.setSenderId(0L); // 系统发送
            message.setReceiverId(receiverId);
            message.setRelatedId(relatedId);
            message.setRelatedType(relatedType);
            message.setIsRead(0);
            message.setPriority(DictConstants.MessagePriority.IMPORTANT);
            message.setExpireTime(LocalDateTime.now().plusDays(7));

            return sendMessage(message);

        } catch (Exception e) {
            log.error("发送业务消息失败 - 接收者: {}, 类型: {}", receiverId, messageType, e);
            return Result.error(ResultCode.SYSTEM_ERROR,"业务消息发送失败");
        }
    }

    @Override
    public Result<Boolean> sendMessageByTemplate(String templateCode, Long receiverId,
                                                 Long relatedId, String relatedType,
                                                 Map<String, Object> params) {
        try {
            // 查询消息模板
            MessageTemplate template = messageTemplateMapper.selectByCode(templateCode);
            if (template == null) {
                return Result.error(ResultCode.PARAM_ERROR,"消息模板不存在");
            }
            if (!template.getIsActive().equals(1)) {
                return Result.error(ResultCode.PARAM_ERROR,"消息模板已禁用");
            }

            // 渲染模板内容
            String title = renderTemplate(template.getTitleTemplate(), params);
            String content = renderTemplate(template.getContentTemplate(), params);

            // 发送消息
            return sendBusinessMessage(template.getTemplateType(), receiverId,
                    relatedId, relatedType, title, content);

        } catch (Exception e) {
            log.error("使用模板发送消息失败 - 模板: {}, 接收者: {}", templateCode, receiverId, e);
            return Result.error(ResultCode.PARAM_ERROR,"模板消息发送失败");
        }
    }

    /**
     * 渲染模板
     */
    private String renderTemplate(String template, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return template;
        }

        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    @Override
    public Result<Page<SystemMessage>> getUnreadMessages(Long userId, Integer pageNum, Integer pageSize) {
        try {
            if (userId == null) {
                return Result.error(ResultCode.PARAM_ERROR,"用户ID不能为空");
            }

            Page<SystemMessage> page = new Page<>(pageNum, pageSize);

            LambdaQueryWrapper<SystemMessage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SystemMessage::getReceiverId, userId)
                    .eq(SystemMessage::getIsRead, 0)
                    .and(wrapper -> wrapper.isNull(SystemMessage::getExpireTime)
                            .or().gt(SystemMessage::getExpireTime, LocalDateTime.now()))
                    .orderByDesc(SystemMessage::getCreateTime);

            Page<SystemMessage> messagePage = systemMessageMapper.selectPage(page, queryWrapper);

            return Result.success("获取未读消息列表成功",messagePage);

        } catch (Exception e) {
            log.error("获取未读消息列表失败 - 用户: {}", userId, e);
            return Result.error(ResultCode.SYSTEM_ERROR,"获取未读消息列表失败");
        }
    }

    @Override
    public Result<Page<SystemMessage>> getMessagesByRelated(Long userId, String relatedType,
                                                            Long relatedId, Integer pageNum, Integer pageSize) {
        try {
            if (userId == null) {
                return Result.error(ResultCode.PARAM_ERROR,"用户ID不能为空");
            }

            Page<SystemMessage> page = new Page<>(pageNum, pageSize);

            LambdaQueryWrapper<SystemMessage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SystemMessage::getReceiverId, userId)
                    .eq(SystemMessage::getRelatedType, relatedType)
                    .eq(SystemMessage::getRelatedId, relatedId)
                    .and(wrapper -> wrapper.isNull(SystemMessage::getExpireTime)
                            .or().gt(SystemMessage::getExpireTime, LocalDateTime.now()))
                    .orderByDesc(SystemMessage::getCreateTime);

            Page<SystemMessage> messagePage = systemMessageMapper.selectPage(page, queryWrapper);

            return Result.success("获取关联消息列表成功",messagePage);

        } catch (Exception e) {
            log.error("获取关联消息列表失败 - 用户: {}, 关联类型: {}, 关联ID: {}",
                    userId, relatedType, relatedId, e);
            return Result.error(ResultCode.SYSTEM_ERROR,"获取关联消息列表失败");
        }
    }

    @Override
    public Result<Page<SystemMessage>> getUserMessages(Long userId, String messageType,
                                                       Integer pageNum, Integer pageSize) {
        try {
            if (userId == null) {
                return Result.error(ResultCode.PARAM_ERROR,"用户ID不能为空");
            }

            // 设置分页参数默认值
            if (pageNum == null || pageNum < 1) {
                pageNum = 1;
            }
            if (pageSize == null || pageSize < 1 || pageSize > 100) {
                pageSize = 20;
            }

            // 使用 MP 分页
            Page<SystemMessage> page = new Page<>(pageNum, pageSize);

            // 构建查询条件
            LambdaQueryWrapper<SystemMessage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SystemMessage::getReceiverId, userId)
                    .and(wrapper -> wrapper.isNull(SystemMessage::getExpireTime)
                            .or().gt(SystemMessage::getExpireTime, LocalDateTime.now()));

            if (StringUtils.hasText(messageType)) {
                queryWrapper.eq(SystemMessage::getMessageType, messageType);
            }

            queryWrapper.orderByDesc(SystemMessage::getCreateTime);

            // 执行分页查询
            Page<SystemMessage> messagePage = systemMessageMapper.selectPage(page, queryWrapper);

            return Result.success( "获取消息列表成功", messagePage);

        } catch (Exception e) {
            log.error("获取用户消息列表失败 - 用户: {}, 类型: {}", userId, messageType, e);
            return Result.error(ResultCode.SYSTEM_ERROR,"获取消息列表失败");
        }
    }


    @Override
    public Result<Boolean> markAsRead(Long messageId, Long userId) {
        try {
            if (messageId == null || userId == null) {
                return Result.error(ResultCode.PARAM_ERROR,"参数不能为空");
            }

            int result = systemMessageMapper.markAsRead(messageId, userId);
            return result > 0 ? Result.success( "标记已读成功",true) : Result.error(ResultCode.PARAM_ERROR,"标记已读失败");

        } catch (Exception e) {
            log.error("标记消息已读失败 - 消息ID: {}, 用户ID: {}", messageId, userId, e);
            return Result.error(ResultCode.PARAM_ERROR,"标记已读失败");
        }
    }

    @Override
    public Result<Boolean> batchMarkAsRead(List<Long> messageIds, Long userId) {
        try {
            if (messageIds == null || messageIds.isEmpty() || userId == null) {
                return Result.error(ResultCode.PARAM_ERROR,"参数不能为空");
            }

            int result = systemMessageMapper.batchMarkAsRead(messageIds, userId);
            return result > 0 ? Result.success( "批量标记已读成功",true) : Result.error(ResultCode.SYSTEM_ERROR,"批量标记已读失败");

        } catch (Exception e) {
            log.error("批量标记消息已读失败 - 消息数量: {}, 用户ID: {}", messageIds.size(), userId, e);
            return Result.error(ResultCode.SYSTEM_ERROR,"批量标记已读失败");
        }
    }

    @Override
    public Result<Boolean> deleteMessage(Long messageId, Long userId) {
        try {
            if (messageId == null || userId == null) {
                return Result.error(ResultCode.PARAM_ERROR,"参数不能为空");
            }

            // 使用 MP 的删除方法，确保只能删除自己的消息
            LambdaQueryWrapper<SystemMessage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SystemMessage::getId, messageId)
                    .eq(SystemMessage::getReceiverId, userId);

            int result = systemMessageMapper.delete(queryWrapper);
            return result > 0 ? Result.success("删除消息成功", true) : Result.error(ResultCode.SYSTEM_ERROR,"删除消息失败");

        } catch (Exception e) {
            log.error("删除消息失败 - 消息ID: {}, 用户ID: {}", messageId, userId, e);
            return Result.error(ResultCode.SYSTEM_ERROR,"删除消息失败");
        }
    }

    @Override
    public Result<Integer> cleanExpiredMessages() {
        try {
            // 先查询过期的消息（用于日志）
            List<SystemMessage> expiredMessages = systemMessageMapper.selectExpiredMessages();

            // 删除过期消息
            int deletedCount = systemMessageMapper.deleteExpiredMessages();

            log.info("清理过期消息完成 - 删除数量: {}", deletedCount);
            return Result.success( "清理过期消息成功", deletedCount);

        } catch (Exception e) {
            log.error("清理过期消息失败", e);
            return Result.error(ResultCode.PARAM_ERROR,"清理过期消息失败");
        }
    }

    @Override
    public Result<SystemMessage> getMessageDetail(Long messageId, Long userId) {
        try {
            if (messageId == null || userId == null) {
                return Result.error(ResultCode.PARAM_ERROR,"参数不能为空");
            }

            // 使用 MP 的查询方法，确保只能查询自己的消息
            LambdaQueryWrapper<SystemMessage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SystemMessage::getId, messageId)
                    .eq(SystemMessage::getReceiverId, userId);

            SystemMessage message = systemMessageMapper.selectOne(queryWrapper);

            if (message == null) {
                return Result.error(ResultCode.PARAM_ERROR,"消息不存在或无权访问");
            }

            // 如果是未读消息，自动标记为已读
            if (message.getIsRead() == 0) {
                systemMessageMapper.markAsRead(messageId, userId);
                message.setIsRead(1);
                message.setReadTime(LocalDateTime.now());
            }

            return Result.success( "获取消息详情成功",message);

        } catch (Exception e) {
            log.error("获取消息详情失败 - 消息ID: {}, 用户ID: {}", messageId, userId, e);
            return Result.error(ResultCode.PARAM_ERROR,"获取消息详情失败");
        }
    }

    @Override
    public Result<Integer> getUnreadCount(Long userId) {
        try {
            if (userId == null) {
                return Result.error(ResultCode.PARAM_ERROR, "用户ID不能为空");
            }

            LambdaQueryWrapper<SystemMessage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SystemMessage::getReceiverId, userId)
                    .eq(SystemMessage::getIsRead, 0)
                    .and(wrapper -> wrapper.isNull(SystemMessage::getExpireTime)
                            .or().gt(SystemMessage::getExpireTime, LocalDateTime.now()));

            Integer unreadCount = systemMessageMapper.selectCount(queryWrapper).intValue();
            return Result.success("获取未读消息数量成功", unreadCount);

        } catch (Exception e) {
            log.error("获取未读消息数量失败 - 用户: {}", userId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取未读消息数量失败");
        }
    }

    @Override
    public Result<Boolean> deleteAllMessages(List<Long> messageIds, Long userId) {
        try {
            // 参数校验
            if (messageIds == null || messageIds.isEmpty() || userId == null) {
                return Result.error(ResultCode.PARAM_ERROR, "参数不能为空");
            }

            // 验证消息是否属于当前用户
            LambdaQueryWrapper<SystemMessage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(SystemMessage::getId, messageIds)
                    .eq(SystemMessage::getReceiverId, userId);

            // 检查是否有权限删除这些消息
            List<SystemMessage> messagesToDelete = systemMessageMapper.selectList(queryWrapper);
            if (messagesToDelete.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "没有可删除的消息或无权访问");
            }

            // 执行删除操作
            int result = systemMessageMapper.delete(queryWrapper);

            if (result > 0) {
                log.info("批量删除成功 - 删除数量: {}, 用户ID: {}", result, userId);
                return Result.success("批量删除消息成功", true);
            } else {
                log.warn("批量删除失败 - 用户ID: {}", userId);
                return Result.error(ResultCode.SYSTEM_ERROR, "批量删除消息失败");
            }

        } catch (Exception e) {
            log.error("批量删除消息失败 - 消息数量: {}, 用户ID: {}", messageIds.size(), userId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量删除消息失败: " + e.getMessage());
        }
    }

}
