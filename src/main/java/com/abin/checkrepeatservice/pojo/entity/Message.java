package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户间消息实体类
 * 用于存储用户间的私信、群聊等实时通信消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("messages")
public class Message extends BaseEntity {

    /**
     * 发送者ID（关联sys_user.id）
     */
    @TableField("sender_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long senderId;

    /**
     * 接收者ID（关联sys_user.id）
     */
    @TableField("receiver_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long receiverId;

    /**
     * 会话ID（关联conversation.id）
     */
    @TableField("conversation_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long conversationId;

    /**
     * 消息类型（PRIVATE-私信, NOTIFICATION-通知, GROUP-群聊, BROADCAST-广播）
     */
    @TableField("message_type")
    private String messageType;

    /**
     * 内容类型（TEXT-文本, FILE-文件, IMAGE-图片, VOICE-语音）
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 消息标题
     */
    @TableField("title")
    private String title;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 附件列表（JSON格式存储）
     */
    @TableField("attachments")
    private String attachments;

    /**
     * 消息状态（SENT-已发送, DELIVERED-已送达, READ-已读）
     */
    @TableField("status")
    private String status;

    /**
     * 关联ID（如论文ID、任务ID等）
     */
    @TableField("related_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long relatedId;

    /**
     * 关联类型（paper-论文, task-任务, notice-通知等）
     */
    @TableField("related_type")
    private String relatedType;

    /**
     * 发送时间
     */
    @TableField("sent_time")
    private LocalDateTime sentTime;

    /**
     * 送达时间
     */
    @TableField("delivered_time")
    private LocalDateTime deliveredTime;

    /**
     * 阅读时间
     */
    @TableField("read_time")
    private LocalDateTime readTime;

    // 冗余字段 - 便于查询显示
    /**
     * 发送者姓名
     */
    @TableField(exist = false)
    private String senderName;

    /**
     * 接收者姓名
     */
    @TableField(exist = false)
    private String receiverName;

    /**
     * 发送者头像
     */
    @TableField(exist = false)
    private String senderAvatar;

    /**
     * 接收者头像
     */
    @TableField(exist = false)
    private String receiverAvatar;
}