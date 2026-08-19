package com.abin.checkrepeatservice.common.websocket.listener;

import com.abin.checkrepeatservice.common.websocket.manager.WebSocketConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * WebSocket连接事件监听器
 */
@Component
@Slf4j
public class WebSocketEventListener {

    @Autowired
    private WebSocketConnectionManager connectionManager;

    /**
     * 监听WebSocket连接事件
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        
        String user = headerAccessor.getUser().getName();
        String sessionId = headerAccessor.getSessionId();
        
        try {
            Long userId = Long.valueOf(user);
            connectionManager.userConnect(userId, sessionId);
            
            log.info("WebSocket连接建立成功 - 用户ID: {}, 会话ID: {}", userId, sessionId);
        } catch (NumberFormatException e) {
            log.error("WebSocket连接用户ID格式错误: {}", user, e);
        }
    }

    /**
     * 监听WebSocket断开事件
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        
        String user = headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : null;
        String sessionId = headerAccessor.getSessionId();
        
        if (user != null) {
            try {
                Long userId = Long.valueOf(user);
                connectionManager.userDisconnect(userId);
                
                log.info("WebSocket连接断开 - 用户ID: {}, 会话ID: {}", userId, sessionId);
            } catch (NumberFormatException e) {
                log.error("WebSocket断开用户ID格式错误: {}", user, e);
            }
        } else {
            log.warn("WebSocket连接断开 - 无法获取用户信息, 会话ID: {}", sessionId);
        }
    }
}