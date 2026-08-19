package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.pojo.entity.Conversation;
import com.abin.checkrepeatservice.pojo.entity.ConversationMember;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 会话服务接口
 */
public interface ConversationService {

    /**
     * 获取用户会话列表
     */
    IPage<Conversation> getUserConversations(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 创建会话
     */
    Conversation createConversation(String name, String type, Long creatorId, List<Long> memberIds);

    /**
     * 更新会话信息
     */
    boolean updateConversation(Long conversationId, String name, String avatar);

    /**
     * 退出会话
     */
    boolean leaveConversation(Long conversationId, Long userId);

    /**
     * 获取会话成员
     */
    IPage<ConversationMember> getConversationMembers(Long conversationId, Integer pageNum, Integer pageSize);

    /**
     * 搜索会话
     */
    IPage<Conversation> searchConversations(Long userId, String keyword, Integer pageNum, Integer pageSize);
}