package com.abin.checkrepeatservice.user.mapper;

import com.abin.checkrepeatservice.pojo.entity.ConversationMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话成员Mapper接口
 */
@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {

    /**
     * 获取会话的所有成员
     * @param conversationId 会话ID
     * @return 成员列表
     */
    List<ConversationMember> getMembersByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 获取用户加入的所有会话ID
     * @param userId 用户ID
     * @return 会话ID列表
     */
    List<Long> getConversationIdsByUserId(@Param("userId") Long userId);

    /**
     * 获取会话中除指定用户外的其他成员
     * @param conversationId 会话ID
     * @param excludeUserId 排除的用户ID
     * @return 成员列表
     */
    List<ConversationMember> getOtherMembers(@Param("conversationId") Long conversationId, @Param("excludeUserId") Long excludeUserId);

    /**
     * 批量更新成员未读消息数量
     * @param conversationId 会话ID
     * @param userId 排除的用户ID（消息发送者）
     * @param increment 增量
     * @return 影响行数
     */
    int batchUpdateUnreadCount(@Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("increment") Integer increment);

    /**
     * 重置指定用户的未读消息数量
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 影响行数
     */
    int resetUnreadCount(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    /**
     * 获取会话成员分页列表
     * @param conversationId 会话ID
     * @param page 分页参数
     * @return 成员分页列表
     */
    IPage<ConversationMember> getMembersPage(@Param("conversationId") Long conversationId, Page<ConversationMember> page);

    /**
     * 检查用户是否为会话成员
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 是否为成员
     */
    boolean isMember(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}