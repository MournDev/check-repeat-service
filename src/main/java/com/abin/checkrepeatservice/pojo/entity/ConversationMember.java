package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 会话成员实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_members")
public class ConversationMember extends BaseEntity {

    /**
     * 会话ID（关联conversations.id）
     */
    @TableField("conversation_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long conversationId;

    /**
     * 用户ID（关联sys_user.id）
     */
    @TableField("user_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 成员角色（OWNER-创建者, ADMIN-管理员, MEMBER-普通成员）
     */
    @TableField("role")
    private String role;

    /**
     * 加入时间
     */
    @TableField("joined_at")
    private LocalDateTime joinedAt;

    /**
     * 离开时间
     */
    @TableField("left_at")
    private LocalDateTime leftAt;

    /**
     * 是否已离开（0-未离开, 1-已离开）
     */
    @TableField("is_left")
    private Integer isLeft;

    /**
     * 未读消息数量
     */
    @TableField("unread_count")
    private Integer unreadCount;

    // 冗余字段 - 便于查询显示
    /**
     * 用户姓名
     */
    @TableField(exist = false)
    private String userName;

    /**
     * 用户头像
     */
    @TableField(exist = false)
    private String userAvatar;

    /**
     * 用户类型
     */
    @TableField(exist = false)
    private String userType;
}