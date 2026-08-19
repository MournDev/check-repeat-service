package com.abin.checkrepeatservice.user.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息VO
 */
@Data
public class MessageVO {

    /**
     * 消息ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 发送者ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long senderId;

    /**
     * 接收者ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long receiverId;

    /**
     * 会话ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long conversationId;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 内容类型
     */
    private String contentType;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 附件列表
     */
    private List<String> attachments;

    /**
     * 消息状态
     */
    private String status;

    /**
     * 关联ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long relatedId;

    /**
     * 关联类型
     */
    private String relatedType;

    /**
     * 发送时间
     */
    private LocalDateTime sentTime;

    /**
     * 送达时间
     */
    private LocalDateTime deliveredTime;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;

    /**
     * 发送者姓名
     */
    private String senderName;

    /**
     * 接收者姓名
     */
    private String receiverName;

    /**
     * 发送者头像
     */
    private String senderAvatar;

    /**
     * 接收者头像
     */
    private String receiverAvatar;
}