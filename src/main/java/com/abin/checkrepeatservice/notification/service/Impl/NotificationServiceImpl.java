package com.abin.checkrepeatservice.notification.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.notification.service.NotificationService;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.user.service.MessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    @Resource
    private MessageService messageService;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    /**
     * 发进用户可见文案的时间统一用这个格式。
     * 用 LocalDateTime.toString() 会渲染成 2026-10-03T14:05:23.123456，直接出现在站内信里。
     */
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static String now() {
        return LocalDateTime.now().format(MINUTE_FORMAT);
    }

    @Override
    public Result<Boolean> sendPaperStatusChangeNotification(Long studentId, Long paperId,
                                                             String paperTitle, String newStatus, String remarks) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("studentId", studentId);
            params.put("paperId", paperId);
            params.put("paperTitle", paperTitle);
            params.put("newStatus", newStatus);
            params.put("remarks", remarks != null ? remarks : "");
            params.put("changeTime", now());

            Result<Boolean> result = messageService.sendMessageByTemplate(
                "PAPER_STATUS_CHANGE",
                studentId,
                paperId,
                DictConstants.RelatedType.PAPER,
                params
            );

            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("论文状态变更通知发送成功: studentId={}, paperId={}, status={}",
                        studentId, paperId, newStatus);
            }
            return result;
        } catch (Exception e) {
            log.error("发送论文状态变更通知失败: studentId={}, paperId={}", studentId, paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送通知失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> sendPaperReviewResultNotification(Long studentId, Long paperId,
                                                             String paperTitle, String reviewResult, String reviewComments) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("studentId", studentId);
            params.put("paperId", paperId);
            params.put("paperTitle", paperTitle);
            params.put("reviewResult", reviewResult);
            params.put("reviewComments", reviewComments != null ? reviewComments : "");
            params.put("reviewTime", now());

            Result<Boolean> result = messageService.sendMessageByTemplate(
                "PAPER_REVIEW_RESULT",
                studentId,
                paperId,
                DictConstants.RelatedType.PAPER,
                params
            );

            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("论文审核结果通知发送成功: studentId={}, paperId={}, result={}",
                        studentId, paperId, reviewResult);
            }
            return result;
        } catch (Exception e) {
            log.error("发送论文审核结果通知失败: studentId={}, paperId={}", studentId, paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送通知失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> sendSystemAnnouncement(String targetType, String title,
                                                  String content, String priority) {
        try {
            // 已知缺陷：targetType 没有真正参与发送——这里没有按目标群体查用户列表，
            // 只写了一条无接收人的公告行，所以它仅出现在下面的日志里。
            // 保留参数是因为 NotificationController.sendAnnouncement 从请求里传进来。
            SystemMessage announcement = new SystemMessage();
            announcement.setTitle(title);
            announcement.setContent(content);
            announcement.setMessageType("ANNOUNCEMENT");
            announcement.setSenderId(0L); // 系统发送
            announcement.setPriority(parsePriority(priority)); // 解析优先级字符串
            announcement.setExpireTime(LocalDateTime.now().plusDays(30)); // 30天过期
            announcement.setCreateTime(LocalDateTime.now());
            announcement.setUpdateTime(LocalDateTime.now());

            Result<Boolean> result = messageService.sendMessage(announcement);

            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("系统公告发送成功: targetType={}, title={}", targetType, title);
            }
            return result;
        } catch (Exception e) {
            log.error("发送系统公告失败: targetType={}, title={}", targetType, title, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送公告失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> sendSimilarityCheckResultNotification(Long studentId, Long paperId,
                                                                 String paperTitle, Double similarityRate, String riskLevel) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("studentId", studentId);
            params.put("paperId", paperId);
            params.put("paperTitle", paperTitle);
            params.put("similarityRate", String.format("%.2f", similarityRate));
            params.put("riskLevel", riskLevel);
            params.put("checkTime", now());

            Result<Boolean> result = messageService.sendMessageByTemplate(
                "SIMILARITY_CHECK_RESULT",
                studentId,
                paperId,
                DictConstants.RelatedType.PAPER,
                params
            );

            if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                log.info("查重结果通知发送成功: studentId={}, paperId={}, similarity={}",
                        studentId, paperId, similarityRate);
            }
            return result;
        } catch (Exception e) {
            log.error("发送查重结果通知失败: studentId={}, paperId={}", studentId, paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送通知失败: " + e.getMessage());
        }
    }

    @Override
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
            return Result.error(ResultCode.SYSTEM_ERROR, "发送紧急通知失败: " + e.getMessage());
        }
    }

    @Override
    public void sendTeacherAssignmentNotice(Long paperId, String paperTitle, Long studentId, Long teacherId) {
        try {
            // 查询教师和学生信息
            SysUser teacher = sysUserMapper.selectById(teacherId);
            SysUser student = sysUserMapper.selectById(studentId);

            if (teacher == null || student == null) {
                log.warn("教师或学生信息不存在，无法发送站内信通知 - 老师ID: {}, 学生ID: {}", teacherId, studentId);
                return;
            }

            Map<String, Object> teacherParams = new HashMap<>();
            teacherParams.put("teacherName", teacher.getRealName());
            teacherParams.put("paperTitle", paperTitle != null ? paperTitle : "未知论文");
            teacherParams.put("paperId", paperId.toString());
            teacherParams.put("studentName", student.getRealName());
            teacherParams.put("deadline", LocalDateTime.now().plusDays(3).format(MINUTE_FORMAT));

            Result<Boolean> teacherMessageResult = messageService.sendMessageByTemplate(
                    "TEACHER_ASSIGNMENT_NOTICE",
                    teacherId,
                    paperId,
                    DictConstants.RelatedType.PAPER,
                    teacherParams
            );

            if (teacherMessageResult.isSuccess()) {
                log.info("教师站内信通知发送成功 - 老师: {}, 论文ID: {}", teacher.getRealName(), paperId);
            } else {
                log.warn("教师站内信通知发送失败 - 老师: {}, 错误: {}", teacher.getRealName(), teacherMessageResult.getMessage());
            }

        } catch (Exception e) {
            log.error("发送教师站内信通知异常 - 论文ID: {}, 老师ID: {}", paperId, teacherId, e);
        }
    }

    @Override
    public void sendStudentAssignmentNotice(Long paperId, String paperTitle, Long studentId, Long teacherId) {
        try {
            SysUser student = sysUserMapper.selectById(studentId);
            SysUser teacher = sysUserMapper.selectById(teacherId);

            if (student == null || teacher == null) {
                log.warn("学生或教师信息不存在，无法发送站内信通知 - 学生ID: {}, 老师ID: {}", studentId, teacherId);
                return;
            }

            Map<String, Object> studentParams = new HashMap<>();
            studentParams.put("studentName", student.getRealName());
            studentParams.put("paperTitle", paperTitle != null ? paperTitle : "未知论文");
            studentParams.put("teacherName", teacher.getRealName());
            studentParams.put("assignmentTime", now());

            Result<Boolean> studentMessageResult = messageService.sendMessageByTemplate(
                    "STUDENT_ASSIGNMENT_RESULT",
                    studentId,
                    paperId,
                    DictConstants.RelatedType.PAPER,
                    studentParams
            );

            if (studentMessageResult.isSuccess()) {
                log.info("学生站内信通知发送成功 - 学生: {}, 论文ID: {}", student.getRealName(), paperId);
            } else {
                log.warn("学生站内信通知发送失败 - 学生: {}, 错误: {}", student.getRealName(), studentMessageResult.getMessage());
            }

        } catch (Exception e) {
            log.error("发送学生站内信通知异常 - 论文ID: {}, 学生ID: {}", paperId, studentId, e);
        }
    }

    @Override
    public void sendTimeoutRejectNotice(Long paperId, Long studentId, Long teacherId) {
        try {
            PaperInfo paper = paperInfoMapper.selectById(paperId);
            SysUser student = sysUserMapper.selectById(studentId);
            SysUser teacher = sysUserMapper.selectById(teacherId);

            if (paper == null || student == null || teacher == null) {
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paper.getPaperTitle());
            params.put("teacherName", teacher.getRealName());
            params.put("timeoutTime", now());

            Result<Boolean> result = messageService.sendMessageByTemplate(
                    "TIMEOUT_REJECT_NOTICE",
                    studentId,
                    paperId,
                    DictConstants.RelatedType.PAPER,
                    params
            );

            if (result.isSuccess()) {
                log.info("超时拒绝站内信通知发送成功 - 论文ID: {}, 学生ID: {}", paperId, studentId);
            } else {
                log.warn("超时拒绝站内信通知发送失败 - 论文ID: {}, 错误: {}", paperId, result.getMessage());
            }

        } catch (Exception e) {
            log.error("发送超时拒绝站内信通知异常 - 论文ID: {}, 老师ID: {}", paperId, teacherId, e);
        }
    }

    @Override
    public Result<Boolean> sendSystemNotice(Long userId, String title, String content, String noticeType, Long relatedId) {
        try {
            // 验证用户是否存在
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null) {
                log.warn("用户不存在，无法发送系统通知 - 用户ID: {}", userId);
                return Result.error(ResultCode.PARAM_ERROR, "用户不存在");
            }

            // 构建通知参数
            Map<String, Object> params = new HashMap<>();
            params.put("userName", user.getRealName());
            params.put("title", title);
            params.put("content", content);
            params.put("noticeType", noticeType);
            params.put("sendTime", now());

            // 如果有关联ID，添加到参数中
            if (relatedId != null) {
                params.put("relatedId", relatedId);
            }

            // 发送站内信
            Result<Boolean> result = messageService.sendMessageByTemplate(
                    "SYSTEM_NOTICE", // 使用系统通知模板
                    userId,
                    relatedId,
                    DictConstants.RelatedType.SYSTEM, // 使用系统类型
                    params
            );

            if (result.isSuccess()) {
                log.info("系统通知发送成功 - 用户: {}, 通知类型: {}, 标题: {}",
                        user.getRealName(), noticeType, title);
            } else {
                log.warn("系统通知发送失败 - 用户: {}, 通知类型: {}, 错误: {}",
                        user.getRealName(), noticeType, result.getMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("发送系统通知异常 - 用户ID: {}, 通知类型: {}, 标题: {}",
                    userId, noticeType, title, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "系统通知发送异常: ");
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
