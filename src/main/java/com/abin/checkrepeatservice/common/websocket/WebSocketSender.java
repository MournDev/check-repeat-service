package com.abin.checkrepeatservice.common.websocket;

import com.abin.checkrepeatservice.common.websocket.manager.WebSocketConnectionManager;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket消息发送器
 */
@Component
@Slf4j
public class WebSocketSender {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private WebSocketConnectionManager connectionManager;

    /**
     * 发送点对点消息
     * @param userId 接收用户ID
     * @param destination 目标地址
     * @param message 消息内容
     */
    public void sendToUser(Long userId, String destination, Object message) {
        try {
            // 检查用户是否在线
            if (!connectionManager.isUserOnline(userId)) {
                log.debug("用户不在线，消息暂存 - 用户ID: {}", userId);
                // 这里可以实现离线消息存储逻辑
                return;
            }
            
            messagingTemplate.convertAndSendToUser(userId.toString(), destination, message);
            log.debug("发送点对点消息成功 - 用户ID: {}, 目标: {}, 消息: {}", 
                     userId, destination, JSON.toJSONString(message));
        } catch (Exception e) {
            log.error("发送点对点消息失败 - 用户ID: {}", userId, e);
        }
    }

    /**
     * 发送广播消息
     * @param destination 目标地址
     * @param message 消息内容
     */
    public void sendToAll(String destination, Object message) {
        try {
            messagingTemplate.convertAndSend(destination, message);
            log.debug("发送广播消息成功 - 目标: {}, 消息: {}", 
                     destination, JSON.toJSONString(message));
        } catch (Exception e) {
            log.error("发送广播消息失败 - 目标: {}", destination, e);
        }
    }

    /**
     * 发送到指定话题
     * @param topic 话题名称
     * @param message 消息内容
     */
    public void sendToTopic(String topic, Object message) {
        try {
            String destination = "/topic/" + topic;
            messagingTemplate.convertAndSend(destination, message);
            log.debug("发送话题消息成功 - 话题: {}, 消息: {}", 
                     topic, JSON.toJSONString(message));
        } catch (Exception e) {
            log.error("发送话题消息失败 - 话题: {}", topic, e);
        }
    }
    
    /**
     * 获取在线用户数量
     * @return 在线用户数
     */
    public int getOnlineUserCount() {
        return connectionManager.getOnlineUserCount();
    }
    
    /**
     * 检查用户是否在线
     * @param userId 用户ID
     * @return 是否在线
     */
    public boolean isUserOnline(Long userId) {
        return connectionManager.isUserOnline(userId);
    }
}