package com.abin.checkrepeatservice.user.mapper;

import com.abin.checkrepeatservice.pojo.entity.InstantMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 即时通讯消息Mapper接口
 */
@Mapper
public interface InstantMessageMapper extends BaseMapper<InstantMessage> {

    /**
     * 获取用户的会话列表
     * @param userId 用户ID
     * @return 会话列表
     */
    List<InstantMessage> getConversations(@Param("userId") Long userId);

    /**
     * 获取会话消息历史
     * @param conversationId 会话ID
     * @param page 分页参数
     * @return 消息分页列表
     */
    IPage<InstantMessage> getMessageHistory(@Param("conversationId") Long conversationId, Page<InstantMessage> page);

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
     * 获取用户之间的私信历史
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @param page 分页参数
     * @return 消息分页列表
     */
    IPage<InstantMessage> getPrivateMessages(
            @Param("senderId") Long senderId,
            @Param("receiverId") Long receiverId,
            Page<InstantMessage> page);
}