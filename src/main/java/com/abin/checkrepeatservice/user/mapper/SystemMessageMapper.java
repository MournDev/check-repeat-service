package com.abin.checkrepeatservice.user.mapper;

import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SystemMessageMapper extends BaseMapper<SystemMessage> {

    /**
     * 获取用户未读消息数量
     */
    Integer selectUnreadCount(@Param("userId") Long userId);

    /**
     * 标记消息为已读
     */
    int markAsRead(@Param("messageId") Long messageId, @Param("userId") Long userId);

    /**
     * 批量标记为已读
     */
    int batchMarkAsRead(@Param("messageIds") List<Long> messageIds, @Param("userId") Long userId);

    /**
     * 获取过期的消息
     */
    List<SystemMessage> selectExpiredMessages();

    /**
     * 删除过期消息
     */
    int deleteExpiredMessages();
}
