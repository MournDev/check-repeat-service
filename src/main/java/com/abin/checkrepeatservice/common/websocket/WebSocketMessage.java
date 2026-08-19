package com.abin.checkrepeatservice.common.websocket;

import lombok.Data;

/**
 * WebSocket消息实体
 */
@Data
public class WebSocketMessage {

    /**
     * 消息类型
     */
    private String type;

    /**
     * 消息内容
     */
    private Object content;

    /**
     * 发送者ID
     */
    private Long senderId;

    /**
     * 接收者ID
     */
    private Long receiverId;

    /**
     * 会话ID
     */
    private Long conversationId;

    /**
     * 时间戳
     */
    private Long timestamp;

    public WebSocketMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public WebSocketMessage(String type, Object content) {
        this();
        this.type = type;
        this.content = content;
    }

    public WebSocketMessage(String type, Object content, Long senderId, Long receiverId) {
        this(type, content);
        this.senderId = senderId;
        this.receiverId = receiverId;
    }
}