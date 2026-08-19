package com.abin.checkrepeatservice.user.mapper;

import com.abin.checkrepeatservice.pojo.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话Mapper接口
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /**
     * 获取用户的会话列表
     * @param userId 用户ID
     * @param page 分页参数
     * @return 会话分页列表
     */
    IPage<Conversation> getUserConversations(@Param("userId") Long userId, Page<Conversation> page);

    /**
     * 获取用户参与的所有会话ID
     * @param userId 用户ID
     * @return 会话ID列表
     */
    List<Long> getUserConversationIds(@Param("userId") Long userId);

    /**
     * 更新会话的最后活跃时间和最后消息
     * @param conversationId 会话ID
     * @param lastMessageId 最后消息ID
     * @return 影响行数
     */
    int updateLastActive(@Param("conversationId") Long conversationId, @Param("lastMessageId") Long lastMessageId);

    /**
     * 搜索会话
     * @param userId 用户ID
     * @param keyword 搜索关键词
     * @param page 分页参数
     * @return 会话分页列表
     */
    IPage<Conversation> searchConversations(@Param("userId") Long userId, @Param("keyword") String keyword, Page<Conversation> page);
}