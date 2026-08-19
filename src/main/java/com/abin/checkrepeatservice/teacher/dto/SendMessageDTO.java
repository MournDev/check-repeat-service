package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

import java.util.List;

/**
 * 发送消息请求DTO
 */
@Data
public class SendMessageDTO {
    /**
     * 接收者ID（学生ID）
     */
    private Long receiverId;
    
    /**
     * 接收者类型
     */
    private String receiverType;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 消息标题（可选）
     */
    private String title;
}