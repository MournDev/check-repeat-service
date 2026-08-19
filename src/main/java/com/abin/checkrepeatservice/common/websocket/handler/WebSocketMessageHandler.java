package com.abin.checkrepeatservice.common.websocket.handler;

import com.abin.checkrepeatservice.common.websocket.WebSocketMessage;
import com.abin.checkrepeatservice.common.websocket.WebSocketSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket消息处理器
 * 处理客户端发送的消息
 */
@Controller
@Slf4j
public class WebSocketMessageHandler {

    @Autowired
    private WebSocketSender webSocketSender;

    /**
     * 处理用户发送的消息
     * @param message 消息内容
     * @param principal 用户身份信息
     * @return 处理后的消息
     */
    @MessageMapping("/message/send")
    @SendToUser("/queue/messages")
    public WebSocketMessage handleMessage(@Payload WebSocketMessage message, Principal principal) {
        try {
            Long senderId = Long.valueOf(principal.getName());
            message.setSenderId(senderId);
            message.setTimestamp(System.currentTimeMillis());
            
            log.debug("收到WebSocket消息 - 发送者: {}, 消息类型: {}, 内容: {}", 
                     senderId, message.getType(), message.getContent());
            
            // 根据消息类型进行不同的处理
            switch (message.getType()) {
                case "CHAT":
                    // 处理聊天消息
                    handleChatMessage(message);
                    break;
                case "TYPING":
                    // 处理输入状态消息
                    handleTypingMessage(message);
                    break;
                case "READ":
                    // 处理已读状态消息
                    handleReadMessage(message);
                    break;
                default:
                    log.warn("未知的消息类型: {}", message.getType());
            }
            
            return message;
        } catch (Exception e) {
            log.error("处理WebSocket消息失败", e);
            WebSocketMessage errorMsg = new WebSocketMessage();
            errorMsg.setType("ERROR");
            errorMsg.setContent("消息处理失败: " + e.getMessage());
            return errorMsg;
        }
    }

    /**
     * 处理聊天消息
     */
    private void handleChatMessage(WebSocketMessage message) {
        // 这里可以集成到消息服务中
        if (message.getReceiverId() != null) {
            // 发送给指定用户
            webSocketSender.sendToUser(message.getReceiverId(), "/queue/messages", message);
        }
        
        log.debug("聊天消息处理完成 - 发送者: {}, 接收者: {}", 
                 message.getSenderId(), message.getReceiverId());
    }

    /**
     * 处理输入状态消息
     */
    private void handleTypingMessage(WebSocketMessage message) {
        // 通知对方用户正在输入
        if (message.getReceiverId() != null) {
            webSocketSender.sendToUser(message.getReceiverId(), "/queue/typing", message);
        }
    }

    /**
     * 处理已读状态消息
     */
    private void handleReadMessage(WebSocketMessage message) {
        // 更新消息已读状态
        log.debug("处理已读状态消息 - 会话ID: {}", message.getConversationId());
    }
}