package com.abin.checkrepeatservice.common.websocket.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket连接状态管理器
 */
@Component
@Slf4j
public class WebSocketConnectionManager {

    // 存储用户连接状态 <userId, sessionId>
    private final ConcurrentHashMap<Long, String> userConnections = new ConcurrentHashMap<>();
    
    // 在线用户计数器
    private final AtomicInteger onlineUserCount = new AtomicInteger(0);

    /**
     * 用户连接
     * @param userId 用户ID
     * @param sessionId 会话ID
     */
    public void userConnect(Long userId, String sessionId) {
        userConnections.put(userId, sessionId);
        onlineUserCount.incrementAndGet();
        log.info("用户连接 - 用户ID: {}, 会话ID: {}, 当前在线用户数: {}", 
                userId, sessionId, onlineUserCount.get());
    }

    /**
     * 用户断开连接
     * @param userId 用户ID
     */
    public void userDisconnect(Long userId) {
        if (userConnections.remove(userId) != null) {
            onlineUserCount.decrementAndGet();
            log.info("用户断开连接 - 用户ID: {}, 当前在线用户数: {}", 
                    userId, onlineUserCount.get());
        }
    }

    /**
     * 检查用户是否在线
     * @param userId 用户ID
     * @return 是否在线
     */
    public boolean isUserOnline(Long userId) {
        return userConnections.containsKey(userId);
    }

    /**
     * 获取用户会话ID
     * @param userId 用户ID
     * @return 会话ID
     */
    public String getUserSessionId(Long userId) {
        return userConnections.get(userId);
    }

    /**
     * 获取在线用户数量
     * @return 在线用户数
     */
    public int getOnlineUserCount() {
        return onlineUserCount.get();
    }

    /**
     * 获取所有在线用户ID
     * @return 在线用户ID列表
     */
    public java.util.Set<Long> getOnlineUserIds() {
        return userConnections.keySet();
    }

    /**
     * 清理所有连接（系统关闭时调用）
     */
    public void clearAllConnections() {
        int count = userConnections.size();
        userConnections.clear();
        onlineUserCount.set(0);
        log.info("清理所有WebSocket连接 - 清理数量: {}", count);
    }
}