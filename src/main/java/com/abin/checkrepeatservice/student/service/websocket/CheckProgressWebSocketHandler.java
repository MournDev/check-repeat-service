package com.abin.checkrepeatservice.student.service.websocket;

import com.abin.checkrepeatservice.student.event.CheckProgressEvent;
import com.abin.checkrepeatservice.student.websocket.CheckProgressWebSocket;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 查重进度 WebSocket 推送服务
 */
@Service
@Slf4j
public class CheckProgressWebSocketHandler {

    @Resource
    private CheckProgressWebSocket checkProgressWebSocket;

    /**
     * 推送进度到指定任务
     */
    public void sendProgress(CheckProgressEvent progressEvent) {
        checkProgressWebSocket.sendProgress(progressEvent.getTaskId(), progressEvent);
    }

    /**
     * 推送自定义进度消息
     */
    public void sendProgress(Long taskId, String stage, Integer percent, 
                            String message, Integer estimatedSeconds) {
        CheckProgressEvent event = CheckProgressEvent.builder()
            .source(this)
            .taskId(taskId)
            .paperId(null) // 可以根据需要传递
            .stage(stage)
            .percent(percent)
            .message(message)
            .estimatedRemainingSeconds(estimatedSeconds)
            .build();
        sendProgress(event);
    }

    /**
     * 广播系统通知
     */
    public void broadcast(String message) {
        checkProgressWebSocket.broadcast(message);
    }
}
