package com.abin.checkrepeatservice.user.mapper;

import com.abin.checkrepeatservice.pojo.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 消息Mapper接口
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 获取用户的会话列表
     * @param userId 用户ID
     * @param role 用户角色
     * @return 会话列表
     */
    List<Message> getConversations(@Param("userId") Long userId, @Param("role") String role);

    /**
     * 获取会话消息历史
     * @param conversationId 会话ID
     * @param page 分页参数
     * @return 消息分页列表
     */
    IPage<Message> getMessageHistory(@Param("conversationId") Long conversationId, Page<Message> page);

    /**
     * 获取未读消息数量
     * @param userId 用户ID
     * @return 未读消息数量
     */
    Integer getUnreadCount(@Param("userId") Long userId);

    /**
     * 批量标记消息为已读
     * @param messageIds 消息ID列表
     * @param userId 用户ID
     * @return 影响行数
     */
    int batchMarkRead(@Param("messageIds") List<Long> messageIds, @Param("userId") Long userId);

    /**
     * 获取用户相关的最新消息
     * @param userId 用户ID
     * @param limit 限制数量
     * @return 最新消息列表
     */
    List<Message> getLatestMessages(@Param("userId") Long userId, @Param("limit") Integer limit);

    /**
     * 获取两个用户之间的私信会话ID
     * @param userId1 用户1ID
     * @param userId2 用户2ID
     * @return 会话ID
     */
    Long getPrivateConversationId(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}