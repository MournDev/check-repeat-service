package com.abin.checkrepeatservice.common.websocket.handler;

import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.common.websocket.WebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebSocket消息拦截器
 * 用于验证用户身份和处理消息
 */
@Component
@Slf4j
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 处理连接请求
            List<String> authorization = accessor.getNativeHeader("Authorization");
            if (authorization != null && !authorization.isEmpty()) {
                // 整头交给 JwtUtils，由它统一剥离 "Bearer " 前缀
                // （这里原先自己 replace("Bearer ", "")，会连令牌中间的同样字串一起替换掉）
                String token = authorization.get(0);
                try {
                    // 验证JWT token
                    Long userId = jwtUtils.getUserIdFromToken(token);
                    if (userId != null) {
                        // 将用户ID设置到会话属性中
                        accessor.setUser(() -> userId.toString());
                        log.info("WebSocket连接认证成功 - 用户ID: {}", userId);
                    } else {
                        log.warn("WebSocket连接认证失败 - 无效的用户ID");
                        return null; // 拒绝连接
                    }
                } catch (Exception e) {
                    log.error("WebSocket连接认证失败 - Token验证异常", e);
                    return null; // 拒绝连接
                }
            } else {
                log.warn("WebSocket连接认证失败 - 缺少Authorization头");
                return null; // 拒绝连接
            }
        }
        
        return message;
    }
}