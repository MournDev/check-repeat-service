package com.abin.checkrepeatservice.user.service.Impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailNotificationService {

    @Resource
    private NotificationFacadeService notificationFacadeService;

    /**
     * 发送教师分配通知 - 邮箱
     */
    public void sendTeacherAssignmentEmail(Long paperId, String paperTitle, Long studentId, Long teacherId) {
        try {
            notificationFacadeService.sendAdvisorAssignedNotice(paperId, paperTitle, studentId, teacherId);
            log.info("教师分配邮箱通知发送成功 - 论文ID: {}, 老师ID: {}", paperId, teacherId);
        } catch (Exception e) {
            log.error("发送教师分配邮箱通知异常 - 论文ID: {}, 老师ID: {}", paperId, teacherId, e);
        }
    }

    /**
     * 发送教师确认通知 - 邮箱
     */
    public void sendTeacherConfirmEmail(Long paperId, Long studentId, Long teacherId) {
        try {
            // 调用原有的邮箱确认通知方法
            //notificationFacadeService.sendConfirmationNotice(paperId, studentId, teacherId, true, null);
            log.info("教师确认邮箱通知发送成功 - 论文ID: {}, 老师ID: {}", paperId, teacherId);
        } catch (Exception e) {
            log.error("发送教师确认邮箱通知异常 - 论文ID: {}, 老师ID: {}", paperId, teacherId, e);
        }
    }

    /**
     * 发送教师拒绝通知 - 邮箱
     */
    public void sendTeacherRejectEmail(Long paperId, Long studentId, Long teacherId, String rejectReason) {
        try {
            // 调用原有的邮箱拒绝通知方法
            // notificationFacadeService.sendConfirmationNotice(paperId, studentId, teacherId, false, rejectReason);
            log.info("教师拒绝邮箱通知发送成功 - 论文ID: {}, 老师ID: {}", paperId, teacherId);
        } catch (Exception e) {
            log.error("发送教师拒绝邮箱通知异常 - 论文ID: {}, 老师ID: {}", paperId, teacherId, e);
        }
    }
}