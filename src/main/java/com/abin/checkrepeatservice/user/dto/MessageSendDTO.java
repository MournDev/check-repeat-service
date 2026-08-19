package com.abin.checkrepeatservice.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 消息发送DTO
 */
@Data
public class MessageSendDTO {

    /**
     * 接收者ID（私信时必填）
     */
    private Long receiverId;

    /**
     * 消息类型（PRIVATE/NOTIFICATION/GROUP/BROADCAST）
     */
    @NotBlank(message = "消息类型不能为空")
    private String messageType;

    /**
     * 内容类型（TEXT/FILE/IMAGE/VOICE）
     */
    @NotBlank(message = "内容类型不能为空")
    private String contentType;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /**
     * 附件列表
     */
    private List<String> attachments;

    /**
     * 关联ID（如论文ID）
     */
    private Long relatedId;

    /**
     * 关联类型（paper/student/teacher等）
     */
    private String relatedType;

    /**
     * 接收者类型（用于广播消息）
     */
    private String receiverType;

    /**
     * 接收者ID列表（用于群发消息）
     */
    private List<Long> receiverIds;
}