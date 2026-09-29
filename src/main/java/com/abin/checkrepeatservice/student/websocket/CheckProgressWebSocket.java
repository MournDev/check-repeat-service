package com.abin.checkrepeatservice.student.websocket;

import com.abin.checkrepeatservice.student.event.CheckProgressEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 查重进度推送 - 使用 STOMP 协议
 * 
 * 前端连接：ws://localhost:8080/ws
 * 订阅地址：/topic/check-progress/{taskId}
 */
@Component
@Slf4j
public class CheckProgressWebSocket {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 注入消息模板（由 Spring WebSocket 自动配置）
     */
    private SimpMessagingTemplate messagingTemplate;

    public CheckProgressWebSocket(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 推送进度到指定任务的所有订阅者
     * 
     * @param taskId 任务 ID
     * @param progressEvent 进度事件
     */
    public void sendProgress(Long taskId, CheckProgressEvent progressEvent) {
        try {
            String destination = "/topic/check-progress/" + taskId;
            messagingTemplate.convertAndSend(destination, progressEvent);
            log.debug("STOMP 消息推送成功 - 任务 ID: {}, 目的地：{}, 进度：{}%", 
                     taskId, destination, progressEvent.getPercent());
        } catch (Exception e) {
            log.error("STOMP 消息推送失败 - 任务 ID: {}", taskId, e);
        }
    }
}
