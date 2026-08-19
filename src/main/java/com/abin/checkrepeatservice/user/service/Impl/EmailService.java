package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.enums.NoticeType;
import com.abin.checkrepeatservice.common.service.Impl.NoticeLogService;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统内邮箱通知服务（替代短信，无第三方依赖）
 */
@Service
@Slf4j
public class EmailService {

    // 从配置文件注入系统发送邮箱账号
    @Value("${spring.mail.username}")
    private String systemEmail;

    // 从配置文件注入邮件主题前缀、内容模板
    @Value("${spring.notice.email.subject-prefix}")
    private String emailSubjectPrefix;
    @Value("${spring.notice.email.content-header}")
    private String emailContentHeader;
    @Value("${spring.notice.email.content-footer}")
    private String emailContentFooter;


    @Resource
    private JavaMailSender javaMailSender;

    @Resource
    private NoticeLogService noticeLogService;

    /**
     * 发送系统通知邮件（带日志记录）
     */
    public boolean sendNoticeEmail(String toEmail, String subject, String content,
                                   String noticeType, Long relatedId, String relatedType) {
        if (!StringUtils.hasText(toEmail) || !StringUtils.hasText(subject) || !StringUtils.hasText(content)) {
            log.error("发送邮件失败：参数为空（toEmail={}, subject={}", toEmail, subject);
            noticeLogService.saveNoticeLog(toEmail, subject, content, noticeType,
                    false, "参数为空", relatedId, relatedType);
            return false;
        }

        String fullSubject = emailSubjectPrefix + subject;

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(systemEmail);
            helper.setTo(toEmail);
            helper.setSubject(fullSubject);

            String fullContent = buildEmailContent(content);
            helper.setText(fullContent, true);

            javaMailSender.send(mimeMessage);
            log.info("邮件发送成功：toEmail={}, subject={}", toEmail, subject);

            // 记录成功日志
            noticeLogService.saveNoticeLog(toEmail, fullSubject, content, noticeType,
                    true, null, relatedId, relatedType);

            return true;
        } catch (Exception e) {
            log.error("发送邮件失败：toEmail={}, subject={}", toEmail, subject, e);

            // 记录失败日志
            noticeLogService.saveNoticeLog(toEmail, fullSubject, content, noticeType,
                    false, e.getMessage(), relatedId, relatedType);

            return false;
        }
    }

    /**
     * 发送任务完成通知邮件（带日志记录）
     */
    public boolean sendTaskCompletionEmail(String toEmail, NoticeType noticeType,
                                           Map<String, Object> params, Long relatedId, String relatedType) {
        try {
            String subject = buildTaskSubject(noticeType, params);
            String content = buildTaskContent(noticeType, params);

            return sendNoticeEmail(toEmail, subject, content, noticeType.name(),
                    relatedId, relatedType);
        } catch (Exception e) {
            log.error("发送任务完成邮件失败：toEmail={}, noticeType={}", toEmail, noticeType, e);
            return false;
        }
    }

    /**
     * 构建邮件内容（支持HTML模板）
     */
    private String buildEmailContent(String coreContent) {
        StringBuilder content = new StringBuilder();

        // 添加HTML头部样式
        content.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset=\"UTF-8\">
                <style>
                    body { font-family: 'Microsoft YaHei', Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; }
                    .email-container { max-width: 600px; margin: 0 auto; background: #ffffff; }
                    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; text-align: center; }
                    .content { padding: 30px 20px; background: #f9f9f9; }
                    .footer { padding: 20px; text-align: center; color: #666; font-size: 12px; border-top: 1px solid #eee; }
                    .button { display: inline-block; padding: 12px 24px; background: #667eea; color: white; text-decoration: none; border-radius: 4px; margin: 10px 0; }
                    .info-box { background: #e8f4fd; border-left: 4px solid #1890ff; padding: 15px; margin: 15px 0; }
                    .success-box { background: #f6ffed; border-left: 4px solid #52c41a; padding: 15px; margin: 15px 0; }
                    .warning-box { background: #fffbe6; border-left: 4px solid #faad14; padding: 15px; margin: 15px 0; }
                    .danger-box { background: #fff2f0; border-left: 4px solid #ff4d4f; padding: 15px; margin: 15px 0; }
                    ul { padding-left: 20px; }
                    li { margin-bottom: 8px; }
                </style>
            </head>
            <body>
                <div class=\"email-container\">
                    <div class=\"header\">
                        <h1>论文查重系统通知</h1>
                    </div>
                    <div class=\"content\">
            """);

        // 添加核心内容
        content.append(coreContent);

        // 添加尾部
        content.append("""
                    </div>
                    <div class=\"footer\">
                        <p>此为系统自动发送邮件，请勿回复</p>
                        <p>如有问题，请联系系统管理员</p>
                        <p>© 2024 论文查重系统 版权所有</p>
                    </div>
                </div>
            </body>
            </html>
            """);

        return content.toString();
    }

    /**
     * 构建任务邮件主题
     */
    private String buildTaskSubject(NoticeType noticeType, Map<String, Object> params) {
        switch (noticeType) {
            case PAPER_SUBMITTED:
                return "论文提交成功通知";
            case PAPER_CHECK_COMPLETED:
                String similarityRate = params.get("similarityRate") != null ?
                        params.get("similarityRate").toString() : "0";
                return String.format("论文查重完成（相似度%s%%）", similarityRate);
            case PAPER_NEEDS_REVISION:
                return "论文需要修改通知";
            case PAPER_APPROVED:
                return "论文审核通过通知";
            case ADVISOR_ASSIGNED:
                return "指导老师分配通知";
            case SYSTEM_ANNOUNCEMENT:
                return params.get("title") != null ? params.get("title").toString() : "系统公告通知";
            case ACCOUNT_ACTIVATION:
                return "账户激活通知";
            case PASSWORD_RESET:
                return "密码重置通知";
            default:
                return noticeType.getTitle();
        }
    }

    /**
     * 构建任务邮件内容
     */
    private String buildTaskContent(NoticeType noticeType, Map<String, Object> params) {
        switch (noticeType) {
            case PAPER_SUBMITTED:
                return buildPaperSubmittedContent(params);
            case PAPER_CHECK_COMPLETED:
                return buildCheckCompletedContent(params);
            case PAPER_NEEDS_REVISION:
                return buildNeedsRevisionContent(params);
            case PAPER_APPROVED:
                return buildPaperApprovedContent(params);
            case ADVISOR_ASSIGNED:
                return buildAdvisorAssignedContent(params);
            case SYSTEM_ANNOUNCEMENT:
                return buildSystemAnnouncementContent(params);
            case ACCOUNT_ACTIVATION:
                return buildAccountActivationContent(params);
            case PASSWORD_RESET:
                return buildPasswordResetContent(params);
            default:
                return buildDefaultContent(noticeType, params);
        }
    }

    /**
     * 论文提交成功内容模板
     */
    private String buildPaperSubmittedContent(Map<String, Object> params) {
        String studentName = (String) params.getOrDefault("studentName", "用户");
        String paperTitle = (String) params.getOrDefault("paperTitle", "您的论文");
        String submitTime = (String) params.getOrDefault("submitTime", LocalDateTime.now().toString());

        return String.format("""
            <div class="success-box">
                <h3>✅ 论文提交成功</h3>
                <p>亲爱的 <strong>%s</strong>：</p>
                <p>您的论文 <strong>《%s》</strong> 已成功提交！</p>
                <p>提交时间：%s</p>
                <p>系统将立即开始处理您的论文，查重完成后会再次通知您。</p>
            </div>
            <div class="info-box">
                <p><strong>下一步：</strong></p>
                <ul>
                    <li>系统将进行论文查重检测</li>
                    <li>查重完成后会分配指导老师</li>
                    <li>您可以在系统中查看处理进度</li>
                </ul>
            </div>
            """, studentName, paperTitle, submitTime);
    }

    /**
     * 查重完成内容模板
     */
    private String buildCheckCompletedContent(Map<String, Object> params) {
        String studentName = (String) params.getOrDefault("studentName", "用户");
        String paperTitle = (String) params.getOrDefault("paperTitle", "您的论文");
        String similarityRate = params.get("similarityRate") != null ?
                params.get("similarityRate").toString() : "0";
        String checkTime = (String) params.getOrDefault("checkTime", LocalDateTime.now().toString());
        boolean needsReview = Boolean.parseBoolean(params.getOrDefault("needsReview", "false").toString());

        String statusBox = needsReview ?
                "<div class=\"warning-box\"><p><strong>⚠️ 注意：</strong>论文相似度较高，可能需要修改</p></div>" :
                "<div class=\"success-box\"><p><strong>✅ 恭喜：</strong>论文相似度在正常范围内</p></div>";

        return String.format("""
            <div class="info-box">
                <h3>📊 论文查重完成</h3>
                <p>亲爱的 <strong>%s</strong>：</p>
                <p>您的论文 <strong>《%s》</strong> 查重已完成！</p>
            </div>
            <div style="text-align: center; margin: 20px 0;">
                <h2 style="color: #1890ff;">相似度：%s%%</h2>
            </div>
            %s
            <div>
                <p><strong>查重时间：</strong>%s</p>
                <p>请登录系统查看详细的查重报告和后续处理进度。</p>
            </div>
            """, studentName, paperTitle, similarityRate, statusBox, checkTime);
    }

    /**
     * 论文需要修改内容模板
     */
    private String buildNeedsRevisionContent(Map<String, Object> params) {
        String studentName = (String) params.getOrDefault("studentName", "用户");
        String paperTitle = (String) params.getOrDefault("paperTitle", "您的论文");
        String feedback = (String) params.getOrDefault("feedback", "请根据指导老师意见进行修改");
        String advisorName = (String) params.getOrDefault("advisorName", "指导老师");

        return String.format("""
            <div class="warning-box">
                <h3>📝 论文需要修改</h3>
                <p>亲爱的 <strong>%s</strong>：</p>
                <p>您的论文 <strong>《%s》</strong> 需要修改。</p>
            </div>
            <div style="background: #fff2e8; border-left: 4px solid #fa541c; padding: 15px; margin: 15px 0;">
                <p><strong>📋 修改意见：</strong></p>
                <p>%s</p>
                <p><em>—— %s</em></p>
            </div>
            <div class="info-box">
                <p><strong>💡 操作指引：</strong></p>
                <ul>
                    <li>请根据上述意见修改论文</li>
                    <li>修改完成后重新提交论文</li>
                    <li>如有疑问，请联系指导老师</li>
                </ul>
            </div>
            """, studentName, paperTitle, feedback, advisorName);
    }

    /**
     * 论文审核通过内容模板
     */
    private String buildPaperApprovedContent(Map<String, Object> params) {
        String studentName = (String) params.getOrDefault("studentName", "用户");
        String paperTitle = (String) params.getOrDefault("paperTitle", "您的论文");

        return String.format("""
            <div class="success-box">
                <h3>🎉 论文审核通过</h3>
                <p>亲爱的 <strong>%s</strong>：</p>
                <p>恭喜！您的论文 <strong>《%s》</strong> 已通过审核！</p>
            </div>
            <div style="text-align: center; margin: 20px 0;">
                <p style="font-size: 18px; color: #52c41a;">✅ 审核状态：<strong>已通过</strong></p>
            </div>
            <div class="info-box">
                <p><strong>🎓 下一步安排：</strong></p>
                <ul>
                    <li>论文已进入最终归档阶段</li>
                    <li>请等待后续的毕业安排通知</li>
                    <li>恭喜您顺利完成论文工作！</li>
                </ul>
            </div>
            """, studentName, paperTitle);
    }

    /**
     * 指导老师分配内容模板（给学生）
     */
    private String buildAdvisorAssignedContent(Map<String, Object> params) {
        String studentName = (String) params.getOrDefault("studentName", "用户");
        String paperTitle = (String) params.getOrDefault("paperTitle", "您的论文");
        String advisorName = (String) params.getOrDefault("advisorName", "待分配");
        String advisorEmail = (String) params.getOrDefault("advisorEmail", "");

        return String.format("""
            <div class="info-box">
                <h3>👨‍🏫 指导老师分配完成</h3>
                <p>亲爱的 <strong>%s</strong>：</p>
                <p>您的论文 <strong>《%s》</strong> 已分配指导老师。</p>
            </div>
            <div style="background: #f0f7ff; padding: 15px; margin: 15px 0; border-radius: 4px;">
                <p><strong>指导老师信息：</strong></p>
                <p>姓名：<strong>%s</strong></p>
                <p>邮箱：%s</p>
            </div>
            <div class="info-box">
                <p><strong>💡 温馨提示：</strong></p>
                <ul>
                    <li>您可以通过系统与指导老师沟通</li>
                    <li>指导老师将为您提供论文指导</li>
                    <li>请及时关注老师的反馈意见</li>
                </ul>
            </div>
            """, studentName, paperTitle, advisorName, advisorEmail);
    }

    /**
     * 系统公告内容模板
     */
    private String buildSystemAnnouncementContent(Map<String, Object> params) {
        String title = (String) params.getOrDefault("title", "系统公告");
        String content = (String) params.getOrDefault("content", "");

        return String.format("""
            <div class="info-box">
                <h3>📢 %s</h3>
                <p>%s</p>
            </div>
            <div style="background: #f6ffed; padding: 15px; margin: 15px 0; border-radius: 4px;">
                <p><strong>📋 说明：</strong></p>
                <p>此邮件为系统自动发送，请勿直接回复。</p>
                <p>如需了解更多信息，请登录系统查看详情。</p>
            </div>
            """, title, content.replace("\n", "<br>"));
    }

    /**
     * 账户激活内容模板
     */
    private String buildAccountActivationContent(Map<String, Object> params) {
        String userName = (String) params.getOrDefault("userName", "用户");
        String activationUrl = (String) params.getOrDefault("activationUrl", "");

        return String.format("""
            <div class="info-box">
                <h3>🔐 账户激活</h3>
                <p>亲爱的 <strong>%s</strong>：</p>
                <p>感谢您注册论文查重系统！请点击以下链接激活您的账户：</p>
            </div>
            <div style="text-align: center; margin: 20px 0;">
                <a href="%s" class="button" style="color: white;">激活账户</a>
            </div>
            <div style="background: #fff7e6; padding: 15px; margin: 15px 0; border-radius: 4px;">
                <p><strong>⚠️ 重要提示：</strong></p>
                <ul>
                    <li>此链接24小时内有效</li>
                    <li>如果按钮无法点击，请复制以下链接到浏览器：</li>
                    <li style="word-break: break-all; font-size: 12px;">%s</li>
                </ul>
            </div>
            """, userName, activationUrl, activationUrl);
    }

    /**
     * 密码重置内容模板
     */
    private String buildPasswordResetContent(Map<String, Object> params) {
        String userName = (String) params.getOrDefault("userName", "用户");
        String resetUrl = (String) params.getOrDefault("resetUrl", "");

        return String.format("""
            <div class="warning-box">
                <h3>🔒 密码重置</h3>
                <p>亲爱的 <strong>%s</strong>：</p>
                <p>您请求重置密码，请点击以下链接完成密码重置：</p>
            </div>
            <div style="text-align: center; margin: 20px 0;">
                <a href="%s" class="button" style="color: white;">重置密码</a>
            </div>
            <div style="background: #fff2f0; padding: 15px; margin: 15px 0; border-radius: 4px;">
                <p><strong>🔐 安全提示：</strong></p>
                <ul>
                    <li>此链接30分钟内有效</li>
                    <li>如果您没有请求重置密码，请忽略此邮件</li>
                    <li>为保障账户安全，请及时修改密码</li>
                    <li>如果按钮无法点击，请复制以下链接到浏览器：</li>
                    <li style="word-break: break-all; font-size: 12px;">%s</li>
                </ul>
            </div>
            """, userName, resetUrl, resetUrl);
    }

    /**
     * 默认内容模板
     */
    private String buildDefaultContent(NoticeType noticeType, Map<String, Object> params) {
        return String.format("""
            <div class="info-box">
                <h3>%s</h3>
                <p>%s</p>
            </div>
            """, noticeType.getTitle(), noticeType.getDefaultContent());
    }

    /**
     * 向后兼容的方法 - 保持原有接口不变
     */
    public boolean sendNoticeEmail(String toEmail, String subject, String content) throws MessagingException {
        return sendNoticeEmail(toEmail, subject, content, "MANUAL", null, "USER");
    }
}