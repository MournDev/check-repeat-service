package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.InstantMessage;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 即时通讯服务接口
 */
public interface InstantMessageService {

    /**
     * 发送即时消息
     * @param message 消息对象
     * @return 发送结果
     */
    Result<Boolean> sendMessage(InstantMessage message);

    /**
     * 获取用户的会话列表
     * @param userId 用户ID
     * @return 会话列表
     */
    Result<List<InstantMessage>> getConversations(Long userId);

    /**
     * 获取会话消息历史
     * @param conversationId 会话ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 消息分页列表
     */
    Result<IPage<InstantMessage>> getMessageHistory(Long conversationId, Integer pageNum, Integer pageSize);

    /**
     * 获取用户之间的私信历史
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 消息分页列表
     */
    Result<IPage<InstantMessage>> getPrivateMessages(Long senderId, Long receiverId, Integer pageNum, Integer pageSize);

    /**
     * 获取未读消息数量
     * @param userId 用户ID
     * @return 未读数量
     */
    Result<Integer> getUnreadCount(Long userId);

    /**
     * 标记消息为已读
     * @param messageIds 消息ID列表
     * @param userId 用户ID
     * @return 操作结果
     */
    Result<Boolean> markAsRead(List<Long> messageIds, Long userId);

    /**
     * 删除消息
     * @param messageId 消息ID
     * @param userId 用户ID
     * @return 操作结果
     */
    Result<Boolean> deleteMessage(Long messageId, Long userId);
}