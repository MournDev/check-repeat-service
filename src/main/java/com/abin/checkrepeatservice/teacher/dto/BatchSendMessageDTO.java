package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量发送消息请求DTO
 */
@Data
public class BatchSendMessageDTO {
    /**
     * 接收者ID数组
     */
    private List<Long> receiverIds;
    
    /**
     * 接收者类型
     */
    private String receiverType;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 消息标题
     */
    private String title;
}