package com.abin.checkrepeatservice.notification.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import com.abin.checkrepeatservice.user.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息通知服务
 * 专门处理师生互动、系统公告、状态变更提醒等通知功能
 */
@Slf4j
@Service
public class NotificationService {

    @Resource
    private MessageService messageService;

    /**
     * 发送论文状态变更通知给学生
     * @param studentId 学生ID
     * @param paperId 论文ID
     * @param paperTitle 论文标题
     * @param newStatus 新状态
     * @param remarks 备注信息
     */
    public Result<Boolean> sendPaperStatusChangeNotification(Long studentId, Long paperId, 
                                                           String paperTitle, String newStatus, String remarks) {
        try {
            String title = "论文状态变更通知";
            StringBuilder content = new StringBuilder();
            content.append("您的论文《").append(paperTitle).append("》状态已更新为：").append(newStatus);
            if (remarks != null && !remarks.isEmpty()) {
                content.append("\n备注：").append(remarks);
            }
            content.append("\n请及时查看并处理相关事宜。");

            Map<String, Object> params = new HashMap<>();
            params.put("studentId", studentId);
            params.put("paperId", paperId);
            params.put("paperTitle", paperTitle);
            params.put("newStatus", newStatus);
            params.put("remarks", remarks != null ? remarks : "");
            params.put("changeTime", LocalDateTime.now().toString());

            Result<Boolean> result = messageService.sendMessageByTemplate(
                "PAPER_STATUS_CHANGE", 
                studentId, 
                paperId, 
                "paper", 
                params
            );

            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("论文状态变更通知发送成功: studentId={}, paperId={}, status={}", 
                        studentId, paperId, newStatus);
            }
            return result;
        } catch (Exception e) {
            log.error("发送论文状态变更通知失败: studentId={}, paperId={}", studentId, paperId, e);
            return Result.error(com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR, 
                              "发送通知失败: " + e.getMessage());
        }
    }

    /**
     * 发送论文审核结果通知给学生
     * @param studentId 学生ID
     * @param paperId 论文ID
     * @param paperTitle 论文标题
     * @param reviewResult 审核结果（通过/不通过）
     * @param reviewComments 审核意见
     */
    public Result<Boolean> sendPaperReviewResultNotification(Long studentId, Long paperId,
                                                           String paperTitle, String reviewResult, String reviewComments) {
        try {
            String title = "论文审核结果通知";
            StringBuilder content = new StringBuilder();
            content.append("您的论文《").append(paperTitle).append("》审核结果：").append(reviewResult);
            if (reviewComments != null && !reviewComments.isEmpty()) {
                content.append("\n审核意见：").append(reviewComments);
            }
            content.append("\n请登录系统查看详情。");

            Map<String, Object> params = new HashMap<>();
            params.put("studentId", studentId);
            params.put("paperId", paperId);
            params.put("paperTitle", paperTitle);
            params.put("reviewResult", reviewResult);
            params.put("reviewComments", reviewComments != null ? reviewComments : "");
            params.put("reviewTime", LocalDateTime.now().toString());

            Result<Boolean> result = messageService.sendMessageByTemplate(
                "PAPER_REVIEW_RESULT", 
                studentId, 
                paperId, 
                "paper_review", 
                params
            );

            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("论文审核结果通知发送成功: studentId={}, paperId={}, result={}", 
                        studentId, paperId, reviewResult);
            }
            return result;
        } catch (Exception e) {
            log.error("发送论文审核结果通知失败: studentId={}, paperId={}", studentId, paperId, e);
            return Result.error(com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR, 
                              "发送通知失败: " + e.getMessage());
        }
    }

    /**
     * 发送新论文提交通知给指导教师
     * @param teacherId 教师ID
     * @param studentId 学生ID
     * @param studentName 学生姓名
     * @param paperId 论文ID
     * @param paperTitle 论文标题
     */
    public Result<Boolean> sendNewPaperSubmissionNotification(Long teacherId, Long studentId,
                                                            String studentName, Long paperId, String paperTitle) {
        try {
            String title = "新论文提交通知";
            String content = String.format("您的指导学生 %s 提交了新论文《%s》，请及时审核。", 
                                         studentName, paperTitle);

            Map<String, Object> params = new HashMap<>();
            params.put("teacherId", teacherId);
            params.put("studentId", studentId);
            params.put("studentName", studentName);
            params.put("paperId", paperId);
            params.put("paperTitle", paperTitle);
            params.put("submissionTime", LocalDateTime.now().toString());

            Result<Boolean> result = messageService.sendMessageByTemplate(
                "NEW_PAPER_SUBMISSION", 
                teacherId, 
                paperId, 
                "paper_submission", 
                params
            );

            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("新论文提交通知发送成功: teacherId={}, studentId={}, paperId={}", 
                        teacherId, studentId, paperId);
            }
            return result;
        } catch (Exception e) {
            log.error("发送新论文提交通知失败: teacherId={}, studentId={}", teacherId, studentId, e);
            return Result.error(com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR, 
                              "发送通知失败: " + e.getMessage());
        }
    }

    /**
     * 发送系统公告给指定用户群体
     * @param targetType 目标类型（ALL/STUDENT/TEACHER/ADMIN）
     * @param title 公告标题
     * @param content 公告内容
     * @param priority 优先级
     */
    public Result<Boolean> sendSystemAnnouncement(String targetType, String title, 
                                                 String content, String priority) {
        try {
            // 这里需要根据targetType查询对应的用户列表
            // 简化实现，只记录系统公告的发送
            
            SystemMessage announcement = new SystemMessage();
            announcement.setTitle(title);
            announcement.setContent(content);
            announcement.setMessageType("ANNOUNCEMENT");
            announcement.setSenderId(0L); // 系统发送
            announcement.setPriority(parsePriority(priority)); // 解析优先级字符串
            announcement.setExpireTime(LocalDateTime.now().plusDays(30)); // 30天过期
            announcement.setCreateTime(LocalDateTime.now());
            announcement.setUpdateTime(LocalDateTime.now());

            // 这里应该批量发送给目标用户群体
            // 简化处理，只记录一条系统公告
            Result<Boolean> result = messageService.sendMessage(announcement);

            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("系统公告发送成功: targetType={}, title={}", targetType, title);
            }
            return result;
        } catch (Exception e) {
            log.error("发送系统公告失败: targetType={}, title={}", targetType, title, e);
            return Result.error(com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR, 
                              "发送公告失败: " + e.getMessage());
        }
    }

    /**
     * 发送查重结果通知给学生
     * @param studentId 学生ID
     * @param paperId 论文ID
     * @param paperTitle 论文标题
     * @param similarityRate 相似度
     * @param riskLevel 风险等级
     */
    public Result<Boolean> sendSimilarityCheckResultNotification(Long studentId, Long paperId,
                                                               String paperTitle, Double similarityRate, String riskLevel) {
        try {
            String title = "论文查重结果通知";
            String content = String.format("您的论文《%s》查重已完成，相似度为 %.2f%% (%s风险)，请及时查看详细报告。", 
                                         paperTitle, similarityRate, riskLevel);

            Map<String, Object> params = new HashMap<>();
            params.put("studentId", studentId);
            params.put("paperId", paperId);
            params.put("paperTitle", paperTitle);
            params.put("similarityRate", String.format("%.2f", similarityRate));
            params.put("riskLevel", riskLevel);
            params.put("checkTime", LocalDateTime.now().toString());

            Result<Boolean> result = messageService.sendMessageByTemplate(
                "SIMILARITY_CHECK_RESULT", 
                studentId, 
                paperId, 
                "similarity_check", 
                params
            );

            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("查重结果通知发送成功: studentId={}, paperId={}, similarity={}", 
                        studentId, paperId, similarityRate);
            }
            return result;
        } catch (Exception e) {
            log.error("发送查重结果通知失败: studentId={}, paperId={}", studentId, paperId, e);
            return Result.error(com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR, 
                              "发送通知失败: " + e.getMessage());
        }
    }

    /**
     * 发送紧急系统通知
     * @param userId 用户ID
     * @param title 通知标题
     * @param content 通知内容
     */
    public Result<Boolean> sendEmergencyNotification(Long userId, String title, String content) {
        try {
            SystemMessage message = new SystemMessage();
            message.setTitle(title);
            message.setContent(content);
            message.setMessageType("EMERGENCY");
            message.setSenderId(0L); // 系统发送
            message.setReceiverId(userId);
            message.setPriority(3); // 3-紧急
            message.setExpireTime(LocalDateTime.now().plusHours(24)); // 24小时过期
            message.setCreateTime(LocalDateTime.now());
            message.setUpdateTime(LocalDateTime.now());

            Result<Boolean> result = messageService.sendMessage(message);

            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("紧急通知发送成功: userId={}, title={}", userId, title);
            }
            return result;
        } catch (Exception e) {
            log.error("发送紧急通知失败: userId={}, title={}", userId, title, e);
            return Result.error(com.abin.checkrepeatservice.common.enums.ResultCode.SYSTEM_ERROR, 
                              "发送紧急通知失败: " + e.getMessage());
        }
    }

    /**
     * 解析优先级字符串为数字
     * @param priorityStr 优先级字符串（LOW/NORMAL/HIGH/CRITICAL）
     * @return 优先级数字（1-普通，2-重要，3-紧急）
     */
    private Integer parsePriority(String priorityStr) {
        if (priorityStr == null) {
            return 1; // 默认普通优先级
        }
        
        switch (priorityStr.toUpperCase()) {
            case "LOW":
                return 1;
            case "NORMAL":
                return 1;
            case "HIGH":
                return 2;
            case "CRITICAL":
                return 3;
            default:
                return 1; // 默认普通优先级
        }
    }
}