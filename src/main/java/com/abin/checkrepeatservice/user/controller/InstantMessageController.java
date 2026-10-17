package com.abin.checkrepeatservice.user.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.InstantMessage;
import com.abin.checkrepeatservice.user.service.InstantMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

/**
 * 即时通讯控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
public class InstantMessageController {

    @Autowired
    private InstantMessageService instantMessageService;

    /**
     * 发送即时消息
     */
    @PostMapping("/send")
    public Result<Boolean> sendMessage(@RequestBody InstantMessage message) {
        try {
            Long senderId = UserBusinessInfoUtils.getCurrentUserId();
            message.setSenderId(senderId);
            
            log.info("发送即时消息请求 - 发送者ID: {}, 接收者ID: {}, 消息类型: {}", 
                    senderId, message.getReceiverId(), message.getMessageType());

            Result<Boolean> result = instantMessageService.sendMessage(message);
            return result;
            
        } catch (Exception e) {
            log.error("发送即时消息失败", e);
            return Result.error(500, "消息发送失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public Result<List<InstantMessage>> getConversations() {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            log.debug("获取会话列表请求 - 用户ID: {}", userId);
            
            return instantMessageService.getConversations(userId);
            
        } catch (Exception e) {
            log.error("获取会话列表失败", e);
            return Result.error(500, "获取会话列表失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取会话消息历史
     */
    @GetMapping("/history/{conversationId}")
    public Result<?> getMessageHistory(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            log.debug("获取会话消息历史请求 - 会话ID: {}, 页码: {}, 每页大小: {}", 
                    conversationId, pageNum, pageSize);
            
            return instantMessageService.getMessageHistory(conversationId, pageNum, pageSize);
            
        } catch (Exception e) {
            log.error("获取会话消息历史失败", e);
            return Result.error(500, "获取消息历史失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取用户间私信历史
     */
    @GetMapping("/private/{otherUserId}")
    public Result<?> getPrivateMessages(
            @PathVariable Long otherUserId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
            log.debug("获取私信历史请求 - 当前用户: {}, 对方用户: {}, 页码: {}, 每页大小: {}", 
                    currentUserId, otherUserId, pageNum, pageSize);
            
            return instantMessageService.getPrivateMessages(currentUserId, otherUserId, pageNum, pageSize);
            
        } catch (Exception e) {
            log.error("获取私信历史失败", e);
            return Result.error(500, "获取私信历史失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取未读消息数量
     */
    @GetMapping("/unread/count")
    public Result<Integer> getUnreadCount() {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            log.debug("获取未读消息数量请求 - 用户ID: {}", userId);
            
            return instantMessageService.getUnreadCount(userId);
            
        } catch (Exception e) {
            log.error("获取未读消息数量失败", e);
            return Result.error(500, "获取未读消息数量失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 标记消息为已读
     */
    @PutMapping("/read")
    public Result<Boolean> markAsRead(@RequestBody List<Long> messageIds) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("标记消息已读请求 - 用户ID: {}, 消息数量: {}", userId, messageIds.size());
            
            return instantMessageService.markAsRead(messageIds, userId);
            
        } catch (Exception e) {
            log.error("标记消息已读失败", e);
            return Result.error(500, "标记已读失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/{messageId}")
    public Result<Boolean> deleteMessage(@PathVariable Long messageId) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("删除消息请求 - 消息ID: {}, 用户ID: {}", messageId, userId);
            
            return instantMessageService.deleteMessage(messageId, userId);
            
        } catch (Exception e) {
            log.error("删除消息失败", e);
            return Result.error(500, "删除消息失败: " + "系统异常，请稍后重试");
        }
    }
}