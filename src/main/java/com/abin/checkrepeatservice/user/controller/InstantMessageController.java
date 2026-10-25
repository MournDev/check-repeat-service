package com.abin.checkrepeatservice.user.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.InstantMessage;
import com.abin.checkrepeatservice.user.service.InstantMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "即时通讯接口", description = "即时消息发送、会话与消息历史查询相关接口")
public class InstantMessageController {

    @Autowired
    private InstantMessageService instantMessageService;

    /**
     * 发送即时消息
     */
    @Operation(summary = "发送即时消息", description = "发送一条即时消息，发送者取自当前登录用户")
    @PostMapping("/send")
    public Result<Boolean> sendMessage(@Parameter(description = "即时消息内容", required = true) @RequestBody InstantMessage message) {
        Long senderId = UserBusinessInfoUtils.getCurrentUserId();
        message.setSenderId(senderId);
        
        log.info("发送即时消息请求 - 发送者ID: {}, 接收者ID: {}, 消息类型: {}", 
                senderId, message.getReceiverId(), message.getMessageType());

        Result<Boolean> result = instantMessageService.sendMessage(message);
        return result;
    }

    /**
     * 获取会话列表
     */
    @Operation(summary = "获取会话列表", description = "获取当前用户的即时消息会话列表")
    @GetMapping("/conversations")
    public Result<List<InstantMessage>> getConversations() {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        log.debug("获取会话列表请求 - 用户ID: {}", userId);
        
        return instantMessageService.getConversations(userId);
    }

    /**
     * 获取会话消息历史
     */
    @Operation(summary = "获取会话消息历史", description = "分页获取指定会话的消息历史")
    @GetMapping("/history/{conversationId}")
    public Result<?> getMessageHistory(
            @Parameter(description = "会话ID", required = true) @PathVariable Long conversationId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        log.debug("获取会话消息历史请求 - 会话ID: {}, 页码: {}, 每页大小: {}", 
                conversationId, pageNum, pageSize);
        
        return instantMessageService.getMessageHistory(conversationId, pageNum, pageSize);
    }

    /**
     * 获取用户间私信历史
     */
    @Operation(summary = "获取私信历史", description = "分页获取当前用户与指定用户之间的私信历史")
    @GetMapping("/private/{otherUserId}")
    public Result<?> getPrivateMessages(
            @Parameter(description = "对方用户ID", required = true) @PathVariable Long otherUserId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        log.debug("获取私信历史请求 - 当前用户: {}, 对方用户: {}, 页码: {}, 每页大小: {}", 
                currentUserId, otherUserId, pageNum, pageSize);
        
        return instantMessageService.getPrivateMessages(currentUserId, otherUserId, pageNum, pageSize);
    }

    /**
     * 获取未读消息数量
     */
    @Operation(summary = "获取未读消息数量", description = "获取当前用户的未读即时消息数量")
    @GetMapping("/unread/count")
    public Result<Integer> getUnreadCount() {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        log.debug("获取未读消息数量请求 - 用户ID: {}", userId);
        
        return instantMessageService.getUnreadCount(userId);
    }

    /**
     * 标记消息为已读
     */
    @Operation(summary = "标记消息为已读", description = "批量将指定消息标记为已读")
    @PutMapping("/read")
    public Result<Boolean> markAsRead(@Parameter(description = "消息ID列表", required = true) @RequestBody List<Long> messageIds) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("标记消息已读请求 - 用户ID: {}, 消息数量: {}", userId, messageIds.size());
        
        return instantMessageService.markAsRead(messageIds, userId);
    }

    /**
     * 删除消息
     */
    @Operation(summary = "删除消息", description = "删除指定的即时消息")
    @DeleteMapping("/{messageId}")
    public Result<Boolean> deleteMessage(@Parameter(description = "消息ID", required = true) @PathVariable Long messageId) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("删除消息请求 - 消息ID: {}, 用户ID: {}", messageId, userId);
        
        return instantMessageService.deleteMessage(messageId, userId);
    }
}