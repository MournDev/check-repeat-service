package com.abin.checkrepeatservice.user.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户消息通知控制器
 * 提供用户消息相关的接口
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@PreAuthorize("hasAnyAuthority('TEACHER','STUDENT','ADMIN')")
@Tag(name = "用户消息接口", description = "用户消息通知相关接口")
public class UserController {

    @Resource
    private MessageService messageService;

    /**
     * 7. 消息通知接口
     * GET /api/user/messages
     */
    @GetMapping("/messages")
    @Operation(summary = "获取用户消息通知", description = "获取用户的消息通知列表")
    public Result<Object> getUserMessages(
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        
        log.info("用户{}请求获取消息列表: pageNum={}, pageSize={}", userId, pageNum, pageSize);
        
        // 验证权限 - 只能查看自己的消息
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        if (!currentUserId.equals(userId)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限访问其他用户的消息");
        }
        
        // 调用消息服务获取消息列表
        Result<PageResultVO<SystemMessage>> messageResult = messageService.getMessageList(
            userId, null, null, pageNum, pageSize, "createTime", "desc");
        
        if (!messageResult.isSuccess()) {
            return Result.error(messageResult.getCode(), messageResult.getMessage());
        }
        
        // 转换为符合需求的数据结构
        PageResultVO<SystemMessage> pageResult = messageResult.getData();
        Map<String, Object> responseData = new HashMap<>();
        
        // 转换消息记录格式
        java.util.List<Map<String, Object>> records = new java.util.ArrayList<>();
        for (SystemMessage message : pageResult.getList()) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", message.getId() == null ? null : String.valueOf(message.getId()));
            record.put("title", message.getTitle());
            record.put("content", message.getContent());
            record.put("createTime", message.getCreateTime());
            record.put("status", message.getIsRead() == 1 ? "READ" : "UNREAD");
            records.add(record);
        }
        
        responseData.put("records", records);
        responseData.put("total", pageResult.getTotalCount());
        
        return Result.success("获取消息列表成功", responseData);
    }

    /**
     * 8. 消息标记已读接口
     * PUT /api/user/messages/{messageId}/read
     */
    @PutMapping("/messages/{messageId}/read")
    @Operation(summary = "标记消息为已读", description = "将指定消息标记为已读状态")
    public Result<String> markMessageAsRead(
            @Parameter(description = "消息ID") @PathVariable Long messageId) {
        
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("用户{}标记消息{}为已读", userId, messageId);
        
        // 调用消息服务标记已读
        Result<Boolean> result = messageService.markAsRead(messageId, userId);
        
        if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
            return Result.success("消息标记已读成功");
        } else {
            return Result.error(result.getCode(), result.getMessage());
        }
    }
}