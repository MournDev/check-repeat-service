package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
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
public class InternalMessageNotificationService {

    @Resource
    private MessageService messageService;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    /**
     * 发送教师分配通知 - 站内信
     */
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
            teacherParams.put("deadline", LocalDateTime.now().plusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

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

    /**
     * 发送学生分配结果通知 - 站内信
     */
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
            studentParams.put("assignmentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

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

    /**
     * 发送超时自动拒绝通知 - 站内信
     *
     * 由 TeacherAssignmentServiceImpl.doRejectAssignment(byTimeout=true)（即 AssignmentTimeoutTask
     * 每天凌晨的分配超时处理）调用。超时场景老师并没有操作过，不能复用「指导老师已拒绝接收」那条文案。
     *
     * 注：本类原先还有 sendTeacherConfirmNotice / sendTeacherRejectNotice 两个方法，与
     * TeacherAssignmentServiceImpl.sendConfirmationNoticeToStudent 完全重复且全项目无调用点
     * （类名容易被误认为是站内信的正统入口，调错会让同一事件发两条通知），已删除。
     */
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
            params.put("timeoutTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

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

        /**
         * 发送系统通知 - 通用方法
         * @param userId 接收用户ID
         * @param title 通知标题
         * @param content 通知内容
         * @param noticeType 通知类型（用于模板匹配）
         * @param relatedId 关联ID（如论文ID等）
         * @return 发送结果
         */
        public Result<Boolean> sendSystemNotice(Long userId, String title, String content, String noticeType, String relatedId) {
            try {
                // 验证用户是否存在
                SysUser user = sysUserMapper.selectById(userId);
                if (user == null) {
                    log.warn("用户不存在，无法发送系统通知 - 用户ID: {}", userId);
                    return Result.error(ResultCode.PARAM_ERROR,"用户不存在");
                }

                // 构建通知参数
                Map<String, Object> params = new HashMap<>();
                params.put("userName", user.getRealName());
                params.put("title", title);
                params.put("content", content);
                params.put("noticeType", noticeType);
                params.put("sendTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

                // 如果有关联ID，添加到参数中
                if (relatedId != null) {
                    params.put("relatedId", relatedId);
                }

                // 发送站内信
                Result<Boolean> result = messageService.sendMessageByTemplate(
                        "SYSTEM_NOTICE", // 使用系统通知模板
                        userId,
                        relatedId != null ? Long.valueOf(relatedId) : null,
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
                return Result.error(ResultCode.SYSTEM_ERROR,"系统通知发送异常: ");
            }
        }

        /**
         * 发送论文相关系统通知 - 便捷方法
         * @param paperId 论文ID
         * @param studentId 学生ID
         * @param title 通知标题
         * @param content 通知内容
         * @param noticeType 通知类型
         * @return 发送结果
         */
        public Result<Boolean> sendPaperSystemNotice(Long paperId, Long studentId, String title,
                                                     String content, String noticeType) {
            try {
                // 验证论文信息
                PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
                if (paperInfo == null) {
                    log.warn("论文不存在，无法发送系统通知 - 论文ID: {}", paperId);
                    return Result.error(ResultCode.SYSTEM_ERROR,"论文不存在");
                }

                // 构建更丰富的参数
                Map<String, Object> params = new HashMap<>();
                params.put("paperTitle", paperInfo.getPaperTitle());
                params.put("paperId", paperId.toString());
                params.put("title", title);
                params.put("content", content);
                params.put("noticeType", noticeType);
                params.put("sendTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

                // 获取学生信息
                SysUser student = sysUserMapper.selectById(studentId);
                if (student != null) {
                    params.put("studentName", student.getRealName());
                }

                // 发送站内信
                Result<Boolean> result = messageService.sendMessageByTemplate(
                        "PAPER_SYSTEM_NOTICE", // 使用论文系统通知模板
                        studentId,
                        paperId,
                        DictConstants.RelatedType.PAPER,
                        params
                );

                if (result.isSuccess()) {
                    log.info("论文系统通知发送成功 - 论文ID: {}, 学生ID: {}, 通知类型: {}",
                            paperId, studentId, noticeType);
                } else {
                    log.warn("论文系统通知发送失败 - 论文ID: {}, 学生ID: {}, 错误: {}",
                            paperId, studentId, result.getMessage());
                }

                return result;

            } catch (Exception e) {
                log.error("发送论文系统通知异常 - 论文ID: {}, 学生ID: {}, 通知类型: {}",
                        paperId, studentId, noticeType, e);
                return Result.error(ResultCode.SYSTEM_ERROR,"论文系统通知发送异常: ");
            }
        }

        /**
         * 批量发送系统通知
         * @param userIds 用户ID列表
         * @param title 通知标题
         * @param content 通知内容
         * @param noticeType 通知类型
         * @param relatedId 关联ID
         * @return 成功发送的数量
         */
        public int sendBatchSystemNotice(java.util.List<Long> userIds, String title, String content,
                                         String noticeType, String relatedId) {
            if (userIds == null || userIds.isEmpty()) {
                log.warn("用户ID列表为空，无法发送批量系统通知");
                return 0;
            }

            int successCount = 0;
            for (Long userId : userIds) {
                Result<Boolean> result = sendSystemNotice(userId, title, content, noticeType, relatedId);
                if (result.isSuccess() && Boolean.TRUE.equals(result.getData())) {
                    successCount++;
                }
            }

            log.info("批量系统通知发送完成 - 总数: {}, 成功: {}, 失败: {}",
                    userIds.size(), successCount, userIds.size() - successCount);
            return successCount;
        }

        /**
         * 发送简单系统通知（最简化的方法）
         * @param userId 接收用户ID
         * @param title 通知标题
         * @param content 通知内容
         * @return 发送结果
         */
        public boolean sendSimpleNotice(Long userId, String title, String content) {
            Result<Boolean> result = sendSystemNotice(userId, title, content, "SIMPLE_NOTICE", null);
            return result.isSuccess() && Boolean.TRUE.equals(result.getData());
        }
}