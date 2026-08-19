package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会话实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversations")
public class Conversation extends BaseEntity {

    /**
     * 会话名称
     */
    @TableField("name")
    private String name;

    /**
     * 会话类型（PRIVATE-私聊, GROUP-群聊）
     */
    @TableField("type")
    private String type;

    /**
     * 会话头像
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 最后一条消息ID
     */
    @TableField("last_message_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long lastMessageId;

    /**
     * 最后活跃时间
     */
    @TableField("last_active_time")
    private LocalDateTime lastActiveTime;

    /**
     * 会话创建者ID
     */
    @TableField("creator_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long creatorId;

    /**
     * 最后一条消息时间
     */
    @TableField("last_message_time")
    private LocalDateTime lastMessageTime;

    /**
     * 会话描述
     */
    @TableField("description")
    private String description;

    // 冗余字段 - 便于查询显示
    /**
     * 最后一条消息内容
     */
    @TableField(exist = false)
    private String lastMessageContent;

    /**
     * 未读消息数量
     */
    @TableField(exist = false)
    private Integer unreadCount;

    /**
     * 会话参与者列表（JSON格式）
     */
    @TableField(exist = false)
    private String participants;
}