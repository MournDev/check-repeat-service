package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统消息实体类
 * 用于存储系统通知、公告、业务提醒等消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("system_message")
public class SystemMessage extends BaseEntity {

    /**
     * 标题
     */
    @TableField("title")
    private String title;

    /**
     * 内容
     */
    @TableField("content")
    private String content;

    /**
     * 消息类型（SYSTEM-系统通知, ANNOUNCEMENT-公告, BUSINESS-业务提醒）
     */
    @TableField("message_type")
    private String messageType;

    /**
     * 发送者ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("sender_id")
    private Long senderId;

    /**
     * 接收者ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("receiver_id")
    private Long receiverId;

    /**
     * 关联ID（如论文ID、任务ID等）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("related_id")
    private Long relatedId;

    /**
     * 关联类型（paper-论文, task-任务, notice-通知等）
     */
    @TableField("related_type")
    private String relatedType;

    /**
     * 内容类型（TEXT-文本, FILE-文件, LINK-链接）
     */
    @TableField(value = "content_type", exist = false)
    private String contentType;

    /**
     * 是否已读：0-未读，1-已读
     */
    @TableField("is_read")
    private Integer isRead = 0;

    /**
     * 阅读时间
     */
    @TableField("read_time")
    private LocalDateTime readTime;

    /**
     * 过期时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 优先级：1-普通，2-重要，3-紧急
     */
    @TableField("priority")
    private Integer priority = 1;

    /**
     * 消息状态（ACTIVE-激活, EXPIRED-过期, ARCHIVED-归档）
     */
    @TableField(value = "status", exist = false)
    private String status = "ACTIVE";
}

