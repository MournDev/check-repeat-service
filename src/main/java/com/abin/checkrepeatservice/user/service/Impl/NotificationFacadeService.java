package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.enums.NoticeType;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 通知服务门面类 - 统一管理所有系统通知
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationFacadeService {

    private final EmailService emailService;
    private final UserQueryService userQueryService;

    /**
     * 发送论文提交成功通知
     */
    public void sendPaperSubmittedNotice(Long paperId, String paperTitle, Long studentId) {
        try {
            SysUser student = userQueryService.getUserById(studentId);
            if (student == null || !StringUtils.hasText(student.getEmail())) {
                log.warn("无法发送论文提交通知：学生不存在或邮箱为空, studentId={}", studentId);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paperTitle);
            params.put("submitTime", LocalDateTime.now().toString());

            boolean success = emailService.sendTaskCompletionEmail(
                    student.getEmail(), NoticeType.PAPER_SUBMITTED, params, paperId, "PAPER");

            log.info("论文提交通知发送{}：student={}, paperTitle={}",
                    success ? "成功" : "失败", student.getRealName(), paperTitle);

        } catch (Exception e) {
            log.error("发送论文提交通知异常：paperId={}, studentId={}", paperId, studentId, e);
        }
    }

    /**
     * 发送查重完成通知
     */
    public void sendCheckCompletedNotice(Long paperId, String paperTitle, Long studentId,
                                         Double similarityRate) {
        try {
            SysUser student = userQueryService.getUserById(studentId);
            if (student == null || !StringUtils.hasText(student.getEmail())) {
                log.warn("无法发送查重完成通知：学生不存在或邮箱为空, studentId={}", studentId);
                return;
            }

            boolean needsReview = similarityRate > 30.0; // 可配置阈值

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paperTitle);
            params.put("similarityRate", String.format("%.2f", similarityRate));
            params.put("checkTime", LocalDateTime.now().toString());
            params.put("needsReview", needsReview);

            boolean success = emailService.sendTaskCompletionEmail(
                    student.getEmail(), NoticeType.PAPER_CHECK_COMPLETED, params, paperId, "PAPER");

            log.info("查重完成通知发送{}：student={}, similarityRate={}%",
                    success ? "成功" : "失败", student.getRealName(), similarityRate);

        } catch (Exception e) {
            log.error("发送查重完成通知异常：paperId={}, studentId={}", paperId, studentId, e);
        }
    }

    /**
     * 发送论文需要修改通知
     */
    public void sendNeedsRevisionNotice(Long paperId, String paperTitle, Long studentId,
                                        String feedback, Long advisorId) {
        try {
            SysUser student = userQueryService.getUserById(studentId);
            if (student == null || !StringUtils.hasText(student.getEmail())) {
                log.warn("无法发送修改通知：学生不存在或邮箱为空, studentId={}", studentId);
                return;
            }

            String advisorName = "指导老师";
            if (advisorId != null) {
                SysUser advisor = userQueryService.getAdvisorById(advisorId);
                if (advisor != null) {
                    advisorName = advisor.getRealName();
                }
            }

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paperTitle);
            params.put("feedback", feedback);
            params.put("advisorName", advisorName);

            boolean success = emailService.sendTaskCompletionEmail(
                    student.getEmail(), NoticeType.PAPER_NEEDS_REVISION, params, paperId, "PAPER");

            log.info("论文修改通知发送{}：student={}, paperTitle={}",
                    success ? "成功" : "失败", student.getRealName(), paperTitle);

        } catch (Exception e) {
            log.error("发送论文修改通知异常：paperId={}, studentId={}", paperId, studentId, e);
        }
    }

    /**
     * 发送论文审核通过通知
     */
    public void sendPaperApprovedNotice(Long paperId, String paperTitle, Long studentId) {
        try {
            SysUser student = userQueryService.getUserById(studentId);
            if (student == null || !StringUtils.hasText(student.getEmail())) {
                log.warn("无法发送审核通过通知：学生不存在或邮箱为空, studentId={}", studentId);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paperTitle);

            boolean success = emailService.sendTaskCompletionEmail(
                    student.getEmail(), NoticeType.PAPER_APPROVED, params, paperId, "PAPER");

            log.info("论文通过通知发送{}：student={}, paperTitle={}",
                    success ? "成功" : "失败", student.getRealName(), paperTitle);

        } catch (Exception e) {
            log.error("发送论文通过通知异常：paperId={}, studentId={}", paperId, studentId, e);
        }
    }

    /**
     * 发送指导老师分配通知（同时通知学生和老师）
     */
    public void sendAdvisorAssignedNotice(Long paperId, String paperTitle,
                                          Long studentId, Long advisorId) {
        try {
            // 1. 通知学生
            sendAdvisorAssignedNoticeToStudent(paperId, paperTitle, studentId, advisorId);

            // 2. 通知老师
            sendAdvisorAssignedNoticeToAdvisor(paperId, paperTitle, studentId, advisorId);

        } catch (Exception e) {
            log.error("发送老师分配通知异常：paperId={}, studentId={}, advisorId={}",
                    paperId, studentId, advisorId, e);
        }
    }

    /**
     * 发送老师分配通知给学生
     */
    private void sendAdvisorAssignedNoticeToStudent(Long paperId, String paperTitle,
                                                    Long studentId, Long advisorId) {
        SysUser student = userQueryService.getUserById(studentId);
        if (student == null || !StringUtils.hasText(student.getEmail())) {
            log.warn("无法发送老师分配通知给学生：学生不存在或邮箱为空, studentId={}", studentId);
            return;
        }

        SysUser advisor = userQueryService.getAdvisorById(advisorId);
        if (advisor == null) {
            log.warn("无法发送老师分配通知给学生：指导老师不存在, advisorId={}", advisorId);
            return;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("studentName", student.getRealName());
        params.put("paperTitle", paperTitle);
        params.put("advisorName", advisor.getRealName());
        params.put("advisorEmail", advisor.getEmail());

        boolean success = emailService.sendTaskCompletionEmail(
                student.getEmail(), NoticeType.ADVISOR_ASSIGNED, params, paperId, "PAPER");

        log.info("老师分配通知发送{}给学生：student={}, advisor={}",
                success ? "成功" : "失败", student.getRealName(), advisor.getRealName());
    }

    /**
     * 发送老师分配通知给老师
     */
    private void sendAdvisorAssignedNoticeToAdvisor(Long paperId, String paperTitle,
                                                    Long studentId, Long advisorId) {
        SysUser advisor = userQueryService.getAdvisorById(advisorId);
        if (advisor == null || !StringUtils.hasText(advisor.getEmail())) {
            log.warn("无法发送老师分配通知给老师：指导老师不存在或邮箱为空, advisorId={}", advisorId);
            return;
        }

        SysUser student = userQueryService.getUserById(studentId);
        String studentName = student.getRealName();
        String studentEmail =student.getEmail();

        Map<String, Object> params = new HashMap<>();
        params.put("advisorName", advisor.getRealName());
        params.put("studentName", studentName);
        params.put("paperTitle", paperTitle);
        params.put("studentEmail", studentEmail);

        // 使用自定义主题和内容给老师
        String subject = "新的论文指导任务分配";
        String content = buildAdvisorAssignmentContent(params);

        boolean success = emailService.sendNoticeEmail(
                advisor.getEmail(), subject, content, "ADVISOR_ASSIGNED", paperId, "PAPER");

        log.info("老师分配通知发送{}给老师：advisor={}, student={}",
                success ? "成功" : "失败", advisor.getRealName(), studentName);
    }

    /**
     * 发送高相似度预警通知给指导老师
     */
    public void sendHighSimilarityAlertToAdvisor(Long paperId, String paperTitle,
                                                 Long studentId, Long advisorId, Double similarityRate) {
        try {
            if (advisorId == null) {
                log.warn("无法发送高相似度预警：论文未分配指导老师, paperId={}", paperId);
                return;
            }

            SysUser advisor = userQueryService.getAdvisorById(advisorId);
            if (advisor == null || !StringUtils.hasText(advisor.getEmail())) {
                log.warn("无法发送高相似度预警：指导老师不存在或邮箱为空, advisorId={}", advisorId);
                return;
            }

            SysUser student = userQueryService.getUserById(studentId);
            String studentName = student.getRealName();

            Map<String, Object> params = new HashMap<>();
            params.put("advisorName", advisor.getRealName());
            params.put("studentName", studentName);
            params.put("paperTitle", paperTitle);
            params.put("similarityRate", String.format("%.2f", similarityRate));

            // 使用自定义主题
            String subject = "学生论文高相似度预警（" + similarityRate + "%）";
            String content = buildHighSimilarityAlertContent(params);

            boolean success = emailService.sendNoticeEmail(
                    advisor.getEmail(), subject, content, "HIGH_SIMILARITY_ALERT", paperId, "PAPER");

            log.info("高相似度预警通知发送{}：advisor={}, paperTitle={}, similarityRate={}%",
                    success ? "成功" : "失败", advisor.getRealName(), paperTitle, similarityRate);

        } catch (Exception e) {
            log.error("发送高相似度预警通知异常：paperId={}, advisorId={}", paperId, advisorId, e);
        }
    }

    /**
     * 发送系统公告通知
     */
    public void sendSystemAnnouncement(String title, String content, String toEmail, Long userId) {
        try {
            if (!StringUtils.hasText(toEmail)) {
                log.warn("无法发送系统公告：邮箱为空");
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("title", title);
            params.put("content", content);

            boolean success = emailService.sendTaskCompletionEmail(
                    toEmail, NoticeType.SYSTEM_ANNOUNCEMENT, params, userId, "USER");

            log.info("系统公告发送{}：toEmail={}, title={}",
                    success ? "成功" : "失败", toEmail, title);

        } catch (Exception e) {
            log.error("发送系统公告异常：toEmail={}, title={}", toEmail, title, e);
        }
    }

    /**
     * 发送账户激活通知
     */
    public void sendAccountActivationNotice(String toEmail, String userName, String activationUrl) {
        try {
            if (!StringUtils.hasText(toEmail)) {
                log.warn("无法发送账户激活通知：邮箱为空");
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("userName", userName);
            params.put("activationUrl", activationUrl);

            boolean success = emailService.sendTaskCompletionEmail(
                    toEmail, NoticeType.ACCOUNT_ACTIVATION, params, null, "USER");

            log.info("账户激活通知发送{}：toEmail={}, userName={}",
                    success ? "成功" : "失败", toEmail, userName);

        } catch (Exception e) {
            log.error("发送账户激活通知异常：toEmail={}, userName={}", toEmail, userName, e);
        }
    }

    /**
     * 发送密码重置通知
     */
    public void sendPasswordResetNotice(String toEmail, String userName, String resetUrl) {
        try {
            if (!StringUtils.hasText(toEmail)) {
                log.warn("无法发送密码重置通知：邮箱为空");
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("userName", userName);
            params.put("resetUrl", resetUrl);

            boolean success = emailService.sendTaskCompletionEmail(
                    toEmail, NoticeType.PASSWORD_RESET, params, null, "USER");

            log.info("密码重置通知发送{}：toEmail={}, userName={}",
                    success ? "成功" : "失败", toEmail, userName);

        } catch (Exception e) {
            log.error("发送密码重置通知异常：toEmail={}, userName={}", toEmail, userName, e);
        }
    }

    /**
     * 构建老师分配通知内容（给老师）
     */
    private String buildAdvisorAssignmentContent(Map<String, Object> params) {
        String advisorName = (String) params.get("advisorName");
        String studentName = (String) params.get("studentName");
        String paperTitle = (String) params.get("paperTitle");
        String studentEmail = (String) params.get("studentEmail");

        return String.format("""
            <div class="info-box">
                <h3>📚 新的论文指导任务</h3>
                <p>尊敬的 <strong>%s</strong> 老师：</p>
                <p>您已被分配为以下论文的指导老师，请及时关注并指导学生完成论文工作。</p>
            </div>
            <div style="background: #f0f7ff; padding: 15px; margin: 15px 0; border-radius: 4px;">
                <p><strong>论文信息：</strong></p>
                <p>论文标题：<strong>《%s》</strong></p>
                <p>学生姓名：<strong>%s</strong></p>
                <p>学生邮箱：%s</p>
                <p>分配时间：%s</p>
            </div>
            <div class="info-box">
                <p><strong>💡 您的职责：</strong></p>
                <ul>
                    <li>审阅学生论文内容和结构</li>
                    <li>提供专业的修改建议</li>
                    <li>指导学生完成论文修改</li>
                    <li>审核论文最终版本</li>
                </ul>
            </div>
            <div style="background: #fff7e6; padding: 15px; margin: 15px 0; border-radius: 4px;">
                <p><strong>📋 操作指引：</strong></p>
                <p>请登录论文查重系统，在「我的指导论文」页面查看论文详情并给出指导建议。</p>
            </div>
            """, advisorName, paperTitle, studentName, studentEmail, LocalDateTime.now().toString());
    }

    /**
     * 构建高相似度预警通知内容
     */
    private String buildHighSimilarityAlertContent(Map<String, Object> params) {
        String advisorName = (String) params.get("advisorName");
        String studentName = (String) params.get("studentName");
        String paperTitle = (String) params.get("paperTitle");
        String similarityRate = (String) params.get("similarityRate");

        return String.format("""
            <div class="warning-box">
                <h3>⚠️ 高相似度论文预警</h3>
                <p>尊敬的 <strong>%s</strong> 老师：</p>
                <p>您指导的学生 <strong>%s</strong> 的论文查重结果相似度较高，需要您关注。</p>
            </div>
            <div style="background: #fff2e8; padding: 15px; margin: 15px 0; border-radius: 4px;">
                <p><strong>论文信息：</strong></p>
                <p>论文标题：<strong>《%s》</strong></p>
                <p>相似度：<strong style="color: #ff4d4f;">%s%%</strong></p>
                <p>学生姓名：%s</p>
                <p>检测时间：%s</p>
            </div>
            <div class="info-box">
                <p><strong>💡 建议操作：</strong></p>
                <ul>
                    <li>登录系统查看详细查重报告</li>
                    <li>与学生沟通论文修改事宜</li>
                    <li>必要时安排面对面指导</li>
                    <li>重点关注论文的原创性</li>
                </ul>
            </div>
            <div style="background: #f6ffed; padding: 15px; margin: 15px 0; border-radius: 4px;">
                <p><strong>📝 系统建议：</strong></p>
                <p>建议您指导学生：</p>
                <ul>
                    <li>重新组织高相似度段落的内容</li>
                    <li>增加个人分析和见解</li>
                    <li>规范引用格式和参考文献</li>
                    <li>确保论文的原创性符合要求</li>
                </ul>
            </div>
            """, advisorName, studentName, paperTitle, similarityRate, studentName, LocalDateTime.now().toString());
    }
}