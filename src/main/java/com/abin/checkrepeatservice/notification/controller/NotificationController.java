package com.abin.checkrepeatservice.notification.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.notification.service.NotificationService;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 消息通知控制器
 * 提供统一的消息管理、通知发送、公告发布等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/notification")
@Tag(name = "消息通知接口", description = "消息管理、通知发送、公告发布等相关接口")
public class NotificationController {

    @Resource
    private MessageService messageService;
    
    @Resource
    private NotificationService notificationService;
    
    @Resource
    private JwtUtils jwtUtils;

    /**
     * 获取用户消息列表
     */
    @GetMapping("/messages")
    @Operation(summary = "获取消息列表", description = "获取当前用户的各类消息列表")
    public Result<PageResultVO<SystemMessage>> getUserMessages(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "消息类型") @RequestParam(required = false) String messageType,
            @Parameter(description = "是否已读") @RequestParam(required = false) Integer isRead,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "排序字段") @RequestParam(defaultValue = "createTime") String sortBy,
            @Parameter(description = "排序方式") @RequestParam(defaultValue = "desc") String sortOrder) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            log.info("用户请求获取消息列表: userId={}, type={}, read={}", userId, messageType, isRead);
            
            return messageService.getMessageList(userId, messageType, isRead, pageNum, pageSize, sortBy, sortOrder);
        } catch (Exception e) {
            log.error("获取消息列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取消息列表失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取未读消息数量
     */
    @GetMapping("/unread-count")
    @Operation(summary = "获取未读消息数", description = "获取当前用户未读消息数量")
    public Result<Integer> getUnreadCount(@RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            log.debug("用户请求获取未读消息数: userId={}", userId);
            
            return messageService.getUnreadCount(userId);
        } catch (Exception e) {
            log.error("获取未读消息数失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取未读消息数失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 标记消息已读
     */
    @PostMapping("/mark-read/{messageId}")
    @Operation(summary = "标记消息已读", description = "将指定消息标记为已读状态")
    public Result<Boolean> markAsRead(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "消息ID") @PathVariable Long messageId) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            log.info("用户标记消息已读: userId={}, messageId={}", userId, messageId);
            
            return messageService.markAsRead(messageId, userId);
        } catch (Exception e) {
            log.error("标记消息已读失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "标记消息已读失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 批量标记消息已读
     */
    @PostMapping("/batch-mark-read")
    @Operation(summary = "批量标记已读", description = "批量将多个消息标记为已读状态")
    public Result<Boolean> batchMarkAsRead(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "消息ID列表") @RequestBody List<Long> messageIds) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            log.info("用户批量标记消息已读: userId={}, messageCount={}", userId, messageIds.size());
            
            return messageService.batchMarkAsRead(messageIds, userId);
        } catch (Exception e) {
            log.error("批量标记消息已读失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量标记消息已读失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/{messageId}")
    @Operation(summary = "删除消息", description = "删除指定的消息")
    public Result<Boolean> deleteMessage(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "消息ID") @PathVariable Long messageId) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            log.info("用户删除消息: userId={}, messageId={}", userId, messageId);
            
            return messageService.deleteMessage(messageId, userId);
        } catch (Exception e) {
            log.error("删除消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除消息失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取消息详情
     */
    @GetMapping("/{messageId}")
    @Operation(summary = "获取消息详情", description = "获取指定消息的详细信息")
    public Result<SystemMessage> getMessageDetail(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "消息ID") @PathVariable Long messageId) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            log.debug("用户获取消息详情: userId={}, messageId={}", userId, messageId);
            
            return messageService.getMessageDetail(messageId, userId);
        } catch (Exception e) {
            log.error("获取消息详情失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取消息详情失败: " + "系统异常，请稍后重试");
        }
    }

    // ==================== 通知发送接口 ====================

    /**
     * 发送论文状态变更通知（教师端）
     */
    @PostMapping("/paper-status-change")
    @PreAuthorize("hasAuthority('TEACHER')")
    @Operation(summary = "发送论文状态变更通知", description = "教师发送论文状态变更通知给学生")
    @OperationLog(type = "notification_paper_status", description = "发送论文状态变更通知")
    public Result<Boolean> sendPaperStatusChange(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "学生ID") @RequestParam Long studentId,
            @Parameter(description = "论文ID") @RequestParam Long paperId,
            @Parameter(description = "论文标题") @RequestParam String paperTitle,
            @Parameter(description = "新状态") @RequestParam String newStatus,
            @Parameter(description = "备注") @RequestParam(required = false) String remarks) {
        try {
            Long teacherId = jwtUtils.getUserIdFromToken(token);
            log.info("教师发送论文状态变更通知: teacherId={}, studentId={}, paperId={}", 
                    teacherId, studentId, paperId);
            
            return notificationService.sendPaperStatusChangeNotification(
                studentId, paperId, paperTitle, newStatus, remarks);
        } catch (Exception e) {
            log.error("发送论文状态变更通知失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送通知失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 发送论文审核结果通知（教师端）
     */
    @PostMapping("/paper-review-result")
    @PreAuthorize("hasAuthority('TEACHER')")
    @Operation(summary = "发送论文审核结果通知", description = "教师发送论文审核结果通知给学生")
    @OperationLog(type = "notification_paper_review", description = "发送论文审核结果通知")
    public Result<Boolean> sendPaperReviewResult(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "学生ID") @RequestParam Long studentId,
            @Parameter(description = "论文ID") @RequestParam Long paperId,
            @Parameter(description = "论文标题") @RequestParam String paperTitle,
            @Parameter(description = "审核结果") @RequestParam String reviewResult,
            @Parameter(description = "审核意见") @RequestParam(required = false) String reviewComments) {
        try {
            Long teacherId = jwtUtils.getUserIdFromToken(token);
            log.info("教师发送论文审核结果通知: teacherId={}, studentId={}, paperId={}", 
                    teacherId, studentId, paperId);
            
            return notificationService.sendPaperReviewResultNotification(
                studentId, paperId, paperTitle, reviewResult, reviewComments);
        } catch (Exception e) {
            log.error("发送论文审核结果通知失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送通知失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 发送系统公告（管理员端）
     */
    @PostMapping("/announcement")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "发布公告", description = "管理员发布系统公告")
    public Result<Boolean> sendAnnouncement(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "目标类型") @RequestParam String targetType,
            @Parameter(description = "公告标题") @RequestParam String title,
            @Parameter(description = "公告内容") @RequestParam String content,
            @Parameter(description = "优先级") @RequestParam(required = false, defaultValue = "NORMAL") String priority) {
        try {
            Long adminId = jwtUtils.getUserIdFromToken(token);
            log.info("管理员发布公告: adminId={}, targetType={}, title={}", adminId, targetType, title);
            
            return notificationService.sendSystemAnnouncement(targetType, title, content, priority);
        } catch (Exception e) {
            log.error("发布公告失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发布公告失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 发送紧急通知（管理员端）
     */
    @PostMapping("/emergency")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "发送紧急通知", description = "管理员发送紧急系统通知")
    public Result<Boolean> sendEmergencyNotification(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "用户ID") @RequestParam Long userId,
            @Parameter(description = "通知标题") @RequestParam String title,
            @Parameter(description = "通知内容") @RequestParam String content) {
        try {
            Long adminId = jwtUtils.getUserIdFromToken(token);
            log.info("管理员发送紧急通知: adminId={}, userId={}, title={}", adminId, userId, title);
            
            return notificationService.sendEmergencyNotification(userId, title, content);
        } catch (Exception e) {
            log.error("发送紧急通知失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送紧急通知失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取通知统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取通知统计", description = "获取用户的消息统计信息")
    public Result<Map<String, Object>> getNotificationStatistics(@RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            log.debug("用户获取通知统计: userId={}", userId);
            
            // 获取各种类型消息的数量统计
            Map<String, Object> statistics = new java.util.HashMap<>();
            
            // 未读消息数
            Result<Integer> unreadResult = messageService.getUnreadCount(userId);
            statistics.put("unreadCount", unreadResult.isSuccess() ? unreadResult.getData() : 0);
            
            // 各类型消息统计
            Result<PageResultVO<SystemMessage>> systemMsgs = messageService.getMessageList(
                userId, "SYSTEM", null, 1, 1, "createTime", "desc");
            statistics.put("systemMessageCount", systemMsgs.isSuccess() ? systemMsgs.getData().getTotalCount() : 0);
            
            Result<PageResultVO<SystemMessage>> privateMsgs = messageService.getMessageList(
                userId, "PRIVATE", null, 1, 1, "createTime", "desc");
            statistics.put("privateMessageCount", privateMsgs.isSuccess() ? privateMsgs.getData().getTotalCount() : 0);
            
            Result<PageResultVO<SystemMessage>> announcementMsgs = messageService.getMessageList(
                userId, "ANNOUNCEMENT", null, 1, 1, "createTime", "desc");
            statistics.put("announcementCount", announcementMsgs.isSuccess() ? announcementMsgs.getData().getTotalCount() : 0);
            
            log.debug("获取通知统计成功: userId={}", userId);
            return Result.success("获取通知统计成功", statistics);
        } catch (Exception e) {
            log.error("获取通知统计失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取通知统计失败: " + "系统异常，请稍后重试");
        }
    }
}