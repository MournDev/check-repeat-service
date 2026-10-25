package com.abin.checkrepeatservice.user.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.Conversation;
import com.abin.checkrepeatservice.pojo.entity.ConversationMember;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import com.abin.checkrepeatservice.user.dto.MessageSendDTO;
import com.abin.checkrepeatservice.user.service.ConversationService;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.abin.checkrepeatservice.user.vo.MessageVO;
import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息通信控制器
 */
@RestController
@RequestMapping("/api/message")
@Slf4j
@Tag(name = "消息通信接口", description = "消息列表、发送、已读、删除及会话管理相关接口")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private ConversationService conversationService;

    /**
     * 获取消息列表（通用接口）
     * GET /api/message/list
     */
    @Operation(summary = "获取消息列表", description = "分页获取当前用户的消息列表，支持按类型、已读状态与字段排序筛选")
    @GetMapping("/list")
    public Result<PageResultVO<SystemMessage>> getMessageList(
            @Parameter(description = "消息类型") @RequestParam(required = false) String messageType,
            @Parameter(description = "是否已读") @RequestParam(required = false) Integer isRead,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "排序字段") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方式") @RequestParam(required = false) String sortOrder) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("获取消息列表请求 - 用户ID: {}, 类型: {}, 已读: {}, 页码: {}, 大小: {}", 
                userId, messageType, isRead, pageNum, pageSize);
        
        Result<PageResultVO<SystemMessage>> result = messageService.getMessageList(
                userId, messageType, isRead, pageNum, pageSize, sortBy, sortOrder);
        return result;
    }

    /**
     * 发送消息
     */
    @Operation(summary = "发送消息", description = "发送一条系统消息，发送者取自当前登录用户")
    @PostMapping("/send")
    public Result<Boolean> sendMessage(@Parameter(description = "消息内容", required = true) @RequestBody SystemMessage message) {
        Long senderId = UserBusinessInfoUtils.getCurrentUserId();
        message.setSenderId(senderId);
        log.info("发送消息请求 - 发送者ID: {}, 接收者ID: {}, 消息类型: {}", 
                senderId, message.getReceiverId(), message.getMessageType());

        // 发送消息
        Result<Boolean> result = messageService.sendMessage(message);
        return result;
    }

    /**
     * 获取用户消息列表
     */
    @Operation(summary = "获取用户消息列表", description = "分页获取当前用户的消息列表")
    @GetMapping("/user-messages")
    public Result<Page<SystemMessage>> getUserMessages(
            @Parameter(description = "消息类型") @RequestParam(required = false) String messageType,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        Result<Page<SystemMessage>> result = messageService.getUserMessages(userId, messageType, pageNum, pageSize);
        return result;
    }

    /**
     * 获取用户未读消息
     */
    @Operation(summary = "获取未读消息", description = "分页获取当前用户的未读消息")
    @GetMapping("/unread-messages")
    public Result<Page<SystemMessage>> getUnreadMessages(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        Result<Page<SystemMessage>> result = messageService.getUnreadMessages(userId, pageNum, pageSize);
        return result;
    }

    /**
     * 获取未读消息数
     */
    @Operation(summary = "获取未读消息数", description = "获取当前用户的未读消息数量")
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount() {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        Result<Integer> result = messageService.getUnreadCount(userId);
        return result;
    }

    /**
     * 标记消息已读
     */
    @Operation(summary = "标记消息已读", description = "将指定消息标记为已读")
    @PostMapping("/mark-read")
    public Result<Boolean> markAsRead(@Parameter(description = "消息ID", required = true) @RequestParam Long messageId) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        Result<Boolean> result = messageService.markAsRead(messageId, userId);
        return result;
    }

    /**
     * 删除消息
     */
    @Operation(summary = "删除消息", description = "删除指定消息")
    @DeleteMapping("/delete/{messageId}")
    public Result<Boolean> deleteMessage(@Parameter(description = "消息ID", required = true) @PathVariable Long messageId) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        Result<Boolean> result = messageService.deleteMessage(messageId, userId);
        return result;
    }

    /**
     * 批量删除消息
     */
    @Operation(summary = "批量删除消息", description = "根据消息ID列表批量删除当前用户的消息")
    @DeleteMapping("/batch-delete")
    public Result<Boolean> deleteAllMessages(@Parameter(description = "消息ID列表", required = true) @RequestBody List<Long> messageIds) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        Result<Boolean> result = messageService.deleteAllMessages(messageIds, userId);
        return result;
    }

    /**
     * 获取消息详情
     */
    @Operation(summary = "获取消息详情", description = "获取指定消息的详细信息")
    @GetMapping("/detail/{messageId}")
    public Result<SystemMessage> getMessageDetail(@Parameter(description = "消息ID", required = true) @PathVariable Long messageId) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        Result<SystemMessage> result = messageService.getMessageDetail(messageId, userId);
        return result;
    }

    // ========================== 会话管理接口 ==========================

    /**
     * 获取用户会话列表
     */
    @Operation(summary = "获取用户会话列表", description = "分页获取当前用户参与的会话列表")
    @GetMapping("/user-conversations")
    public Result<IPage<Conversation>> getUserConversations(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        IPage<Conversation> conversations = conversationService.getUserConversations(userId, pageNum, pageSize);
        return Result.success("获取用户会话列表成功", conversations);
    }

    /**
     * 创建会话
     */
    @Operation(summary = "创建会话", description = "创建一个新会话并指定成员")
    @PostMapping("/conversation/create")
    public Result<Conversation> createConversation(
            @Parameter(description = "会话名称", required = true) @RequestParam String name,
            @Parameter(description = "会话类型", required = true) @RequestParam String type,
            @Parameter(description = "成员ID列表", required = true) @RequestBody List<Long> memberIds) {
        Long creatorId = UserBusinessInfoUtils.getCurrentUserId();
        Conversation conversation = conversationService.createConversation(name, type, creatorId, memberIds);
        return Result.success("创建会话成功", conversation);
    }

    /**
     * 更新会话信息
     */
    @Operation(summary = "更新会话信息", description = "更新会话的名称或头像")
    @PutMapping("/conversation/{conversationId}")
    public Result<Boolean> updateConversation(
            @Parameter(description = "会话ID", required = true) @PathVariable Long conversationId,
            @Parameter(description = "会话名称") @RequestParam(required = false) String name,
            @Parameter(description = "会话头像") @RequestParam(required = false) String avatar) {
        boolean result = conversationService.updateConversation(conversationId, name, avatar);
        return Result.success("更新会话信息成功", result);
    }

    /**
     * 退出会话
     */
    @Operation(summary = "退出会话", description = "当前用户退出指定会话")
    @PostMapping("/conversation/{conversationId}/leave")
    public Result<Boolean> leaveConversation(@Parameter(description = "会话ID", required = true) @PathVariable Long conversationId) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        boolean result = conversationService.leaveConversation(conversationId, userId);
        return Result.success("退出会话成功", result);
    }

    /**
     * 获取会话成员
     */
    @Operation(summary = "获取会话成员", description = "分页获取指定会话的成员列表")
    @GetMapping("/conversation/{conversationId}/members")
    public Result<IPage<ConversationMember>> getConversationMembers(
            @Parameter(description = "会话ID", required = true) @PathVariable Long conversationId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer pageSize) {
        IPage<ConversationMember> members = conversationService.getConversationMembers(conversationId, pageNum, pageSize);
        return Result.success("获取会话成员成功", members);
    }

    /**
     * 搜索会话
     */
    @Operation(summary = "搜索会话", description = "按关键字搜索当前用户的会话")
    @GetMapping("/conversation/search")
    public Result<IPage<Conversation>> searchConversations(
            @Parameter(description = "搜索关键字", required = true) @RequestParam String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        IPage<Conversation> conversations = conversationService.searchConversations(userId, keyword, pageNum, pageSize);
        return Result.success("搜索会话成功", conversations);
    }
}