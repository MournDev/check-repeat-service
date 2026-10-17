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
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private ConversationService conversationService;

    /**
     * 获取消息列表（通用接口）
     * GET /api/message/list
     */
    @GetMapping("/list")
    public Result<PageResultVO<SystemMessage>> getMessageList(
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("获取消息列表请求 - 用户ID: {}, 类型: {}, 已读: {}, 页码: {}, 大小: {}", 
                    userId, messageType, isRead, pageNum, pageSize);
            
            Result<PageResultVO<SystemMessage>> result = messageService.getMessageList(
                    userId, messageType, isRead, pageNum, pageSize, sortBy, sortOrder);
            return result;
        } catch (Exception e) {
            log.error("获取消息列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取消息列表失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 发送消息
     */
    @PostMapping("/send")
    public Result<Boolean> sendMessage(@RequestBody SystemMessage message) {
        try {
            Long senderId = UserBusinessInfoUtils.getCurrentUserId();
            message.setSenderId(senderId);
            log.info("发送消息请求 - 发送者ID: {}, 接收者ID: {}, 消息类型: {}", 
                    senderId, message.getReceiverId(), message.getMessageType());

            // 发送消息
            Result<Boolean> result = messageService.sendMessage(message);
            return result;
        } catch (Exception e) {
            log.error("发送消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "消息发送失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取用户消息列表
     */
    @GetMapping("/user-messages")
    public Result<Page<SystemMessage>> getUserMessages(
            @RequestParam(required = false) String messageType,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            Result<Page<SystemMessage>> result = messageService.getUserMessages(userId, messageType, pageNum, pageSize);
            return result;
        } catch (Exception e) {
            log.error("获取用户消息列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取用户消息列表失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取用户未读消息
     */
    @GetMapping("/unread-messages")
    public Result<Page<SystemMessage>> getUnreadMessages(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            Result<Page<SystemMessage>> result = messageService.getUnreadMessages(userId, pageNum, pageSize);
            return result;
        } catch (Exception e) {
            log.error("获取未读消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取未读消息失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取未读消息数
     */
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount() {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            Result<Integer> result = messageService.getUnreadCount(userId);
            return result;
        } catch (Exception e) {
            log.error("获取未读消息数失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取未读消息数失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 标记消息已读
     */
    @PostMapping("/mark-read")
    public Result<Boolean> markAsRead(@RequestParam Long messageId) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            Result<Boolean> result = messageService.markAsRead(messageId, userId);
            return result;
        } catch (Exception e) {
            log.error("标记消息已读失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "标记消息已读失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/delete/{messageId}")
    public Result<Boolean> deleteMessage(@PathVariable Long messageId) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            Result<Boolean> result = messageService.deleteMessage(messageId, userId);
            return result;
        } catch (Exception e) {
            log.error("删除消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除消息失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 批量删除消息
     */
    @DeleteMapping("/batch-delete")
    public Result<Boolean> deleteAllMessages(@RequestBody List<Long> messageIds) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            Result<Boolean> result = messageService.deleteAllMessages(messageIds, userId);
            return result;
        } catch (Exception e) {
            log.error("批量删除消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量删除消息失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取消息详情
     */
    @GetMapping("/detail/{messageId}")
    public Result<SystemMessage> getMessageDetail(@PathVariable Long messageId) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            Result<SystemMessage> result = messageService.getMessageDetail(messageId, userId);
            return result;
        } catch (Exception e) {
            log.error("获取消息详情失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取消息详情失败: " + "系统异常，请稍后重试");
        }
    }

    // ========================== 会话管理接口 ==========================

    /**
     * 获取用户会话列表
     */
    @GetMapping("/user-conversations")
    public Result<IPage<Conversation>> getUserConversations(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            IPage<Conversation> conversations = conversationService.getUserConversations(userId, pageNum, pageSize);
            return Result.success("获取用户会话列表成功", conversations);
        } catch (Exception e) {
            log.error("获取用户会话列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取用户会话列表失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 创建会话
     */
    @PostMapping("/conversation/create")
    public Result<Conversation> createConversation(
            @RequestParam String name,
            @RequestParam String type,
            @RequestBody List<Long> memberIds) {
        try {
            Long creatorId = UserBusinessInfoUtils.getCurrentUserId();
            Conversation conversation = conversationService.createConversation(name, type, creatorId, memberIds);
            return Result.success("创建会话成功", conversation);
        } catch (Exception e) {
            log.error("创建会话失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "创建会话失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 更新会话信息
     */
    @PutMapping("/conversation/{conversationId}")
    public Result<Boolean> updateConversation(
            @PathVariable Long conversationId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String avatar) {
        try {
            boolean result = conversationService.updateConversation(conversationId, name, avatar);
            return Result.success("更新会话信息成功", result);
        } catch (Exception e) {
            log.error("更新会话信息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "更新会话信息失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 退出会话
     */
    @PostMapping("/conversation/{conversationId}/leave")
    public Result<Boolean> leaveConversation(@PathVariable Long conversationId) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            boolean result = conversationService.leaveConversation(conversationId, userId);
            return Result.success("退出会话成功", result);
        } catch (Exception e) {
            log.error("退出会话失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "退出会话失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取会话成员
     */
    @GetMapping("/conversation/{conversationId}/members")
    public Result<IPage<ConversationMember>> getConversationMembers(
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            IPage<ConversationMember> members = conversationService.getConversationMembers(conversationId, pageNum, pageSize);
            return Result.success("获取会话成员成功", members);
        } catch (Exception e) {
            log.error("获取会话成员失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取会话成员失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 搜索会话
     */
    @GetMapping("/conversation/search")
    public Result<IPage<Conversation>> searchConversations(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            Long userId = UserBusinessInfoUtils.getCurrentUserId();
            IPage<Conversation> conversations = conversationService.searchConversations(userId, keyword, pageNum, pageSize);
            return Result.success("搜索会话成功", conversations);
        } catch (Exception e) {
            log.error("搜索会话失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "搜索会话失败: " + "系统异常，请稍后重试");
        }
    }
}