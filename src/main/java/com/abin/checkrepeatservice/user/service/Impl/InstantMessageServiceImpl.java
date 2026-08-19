package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.InstantMessage;
import com.abin.checkrepeatservice.user.mapper.InstantMessageMapper;
import com.abin.checkrepeatservice.user.service.InstantMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 即时通讯服务实现类
 */
@Slf4j
@Service
public class InstantMessageServiceImpl implements InstantMessageService {

    @Autowired
    private InstantMessageMapper instantMessageMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> sendMessage(InstantMessage message) {
        try {
            // 参数校验
            if (message.getSenderId() == null) {
                return Result.error(ResultCode.PARAM_ERROR, "发送者ID不能为空");
            }
            if (message.getReceiverId() == null) {
                return Result.error(ResultCode.PARAM_ERROR, "接收者ID不能为空");
            }
            if (!StringUtils.hasText(message.getContent())) {
                return Result.error(ResultCode.PARAM_ERROR, "消息内容不能为空");
            }

            // 设置默认值
            if (!StringUtils.hasText(message.getMessageType())) {
                message.setMessageType("PRIVATE"); // 默认私信
            }
            if (!StringUtils.hasText(message.getContentType())) {
                message.setContentType("TEXT"); // 默认文本
            }
            if (!StringUtils.hasText(message.getStatus())) {
                message.setStatus("SENT"); // 默认已发送
            }

            message.setSentTime(LocalDateTime.now());
            message.setCreateTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());

            // 插入消息
            int result = instantMessageMapper.insert(message);
            
            if (result > 0) {
                log.info("即时消息发送成功 - 发送者: {}, 接收者: {}, 消息ID: {}", 
                        message.getSenderId(), message.getReceiverId(), message.getId());
                return Result.success("消息发送成功", true);
            } else {
                return Result.error(ResultCode.SYSTEM_ERROR, "消息发送失败");
            }

        } catch (Exception e) {
            log.error("发送即时消息失败 - 发送者: {}, 接收者: {}", 
                    message.getSenderId(), message.getReceiverId(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "消息发送异常: " + e.getMessage());
        }
    }

    @Override
    public Result<List<InstantMessage>> getConversations(Long userId) {
        try {
            if (userId == null) {
                return Result.error(ResultCode.PARAM_ERROR, "用户ID不能为空");
            }

            List<InstantMessage> conversations = instantMessageMapper.getConversations(userId);
            
            log.debug("获取会话列表成功 - 用户ID: {}, 会话数量: {}", userId, conversations.size());
            return Result.success("获取会话列表成功", conversations);

        } catch (Exception e) {
            log.error("获取会话列表失败 - 用户ID: {}", userId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取会话列表失败: " + e.getMessage());
        }
    }

    @Override
    public Result<IPage<InstantMessage>> getMessageHistory(Long conversationId, Integer pageNum, Integer pageSize) {
        try {
            if (conversationId == null) {
                return Result.error(ResultCode.PARAM_ERROR, "会话ID不能为空");
            }
            
            pageNum = pageNum != null && pageNum > 0 ? pageNum : 1;
            pageSize = pageSize != null && pageSize > 0 ? pageSize : 10;
            
            Page<InstantMessage> page = new Page<>(pageNum, pageSize);
            IPage<InstantMessage> resultPage = instantMessageMapper.getMessageHistory(conversationId, page);
            
            log.debug("获取会话消息历史成功 - 会话ID: {}, 消息数量: {}", conversationId, resultPage.getTotal());
            return Result.success("获取消息历史成功", resultPage);

        } catch (Exception e) {
            log.error("获取会话消息历史失败 - 会话ID: {}", conversationId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取消息历史失败: " + e.getMessage());
        }
    }

    @Override
    public Result<IPage<InstantMessage>> getPrivateMessages(Long senderId, Long receiverId, Integer pageNum, Integer pageSize) {
        try {
            if (senderId == null || receiverId == null) {
                return Result.error(ResultCode.PARAM_ERROR, "用户ID不能为空");
            }
            
            pageNum = pageNum != null && pageNum > 0 ? pageNum : 1;
            pageSize = pageSize != null && pageSize > 0 ? pageSize : 10;
            
            Page<InstantMessage> page = new Page<>(pageNum, pageSize);
            IPage<InstantMessage> resultPage = instantMessageMapper.getPrivateMessages(senderId, receiverId, page);
            
            log.debug("获取私信历史成功 - 发送者: {}, 接收者: {}, 消息数量: {}", 
                    senderId, receiverId, resultPage.getTotal());
            return Result.success("获取私信历史成功", resultPage);

        } catch (Exception e) {
            log.error("获取私信历史失败 - 发送者: {}, 接收者: {}", senderId, receiverId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取私信历史失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Integer> getUnreadCount(Long userId) {
        try {
            if (userId == null) {
                return Result.error(ResultCode.PARAM_ERROR, "用户ID不能为空");
            }

            Integer unreadCount = instantMessageMapper.getUnreadCount(userId);
            
            log.debug("获取未读消息数量成功 - 用户ID: {}, 未读数量: {}", userId, unreadCount);
            return Result.success("获取未读消息数量成功", unreadCount);

        } catch (Exception e) {
            log.error("获取未读消息数量失败 - 用户ID: {}", userId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取未读消息数量失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> markAsRead(List<Long> messageIds, Long userId) {
        try {
            if (messageIds == null || messageIds.isEmpty() || userId == null) {
                return Result.error(ResultCode.PARAM_ERROR, "参数不能为空");
            }

            int result = instantMessageMapper.batchMarkRead(messageIds, userId);
            
            if (result > 0) {
                log.info("标记消息已读成功 - 用户ID: {}, 消息数量: {}", userId, result);
                return Result.success("标记已读成功", true);
            } else {
                return Result.error(ResultCode.PARAM_ERROR, "标记已读失败");
            }

        } catch (Exception e) {
            log.error("标记消息已读失败 - 用户ID: {}, 消息数量: {}", userId, messageIds.size(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "标记已读失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> deleteMessage(Long messageId, Long userId) {
        try {
            if (messageId == null || userId == null) {
                return Result.error(ResultCode.PARAM_ERROR, "参数不能为空");
            }

            // 验证消息所有权
            LambdaQueryWrapper<InstantMessage> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(InstantMessage::getId, messageId)
                    .and(wrapper -> wrapper.eq(InstantMessage::getSenderId, userId)
                            .or().eq(InstantMessage::getReceiverId, userId));

            InstantMessage message = instantMessageMapper.selectOne(queryWrapper);
            if (message == null) {
                return Result.error(ResultCode.PARAM_ERROR, "消息不存在或无权访问");
            }

            // 软删除
            int result = instantMessageMapper.deleteById(messageId);
            
            if (result > 0) {
                log.info("删除消息成功 - 消息ID: {}, 用户ID: {}", messageId, userId);
                return Result.success("删除消息成功", true);
            } else {
                return Result.error(ResultCode.SYSTEM_ERROR, "删除消息失败");
            }

        } catch (Exception e) {
            log.error("删除消息失败 - 消息ID: {}, 用户ID: {}", messageId, userId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除消息失败: " + e.getMessage());
        }
    }
}