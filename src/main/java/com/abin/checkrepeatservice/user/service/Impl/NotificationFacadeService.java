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
     * 发送指导记录待确认通知给学生（论文全生命周期 P1）
     */
    public void sendGuidanceCreatedNotice(Long recordId, String paperTitle, Long studentId,
                                          String teacherName, Integer round) {
        try {
            SysUser student = userQueryService.getUserById(studentId);
            if (student == null || !StringUtils.hasText(student.getEmail())) {
                log.warn("无法发送指导记录通知：学生不存在或邮箱为空, studentId={}", studentId);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paperTitle);
            params.put("teacherName", teacherName);
            params.put("round", String.valueOf(round));
            params.put("createTime", LocalDateTime.now().toString());

            boolean success = emailService.sendTaskCompletionEmail(
                    student.getEmail(), NoticeType.GUIDANCE_CREATED, params, recordId, "PAPER");

            log.info("指导记录待确认通知发送{}：student={}, round={}",
                    success ? "成功" : "失败", student.getRealName(), round);

        } catch (Exception e) {
            log.error("发送指导记录待确认通知异常：recordId={}, studentId={}", recordId, studentId, e);
        }
    }

    /**
     * 发送指导记录已确认通知给指导教师（论文全生命周期 P1）
     */
    public void sendGuidanceConfirmedNotice(Long paperId, String paperTitle, Long teacherId,
                                            String studentName, Integer round) {
        try {
            SysUser advisor = userQueryService.getAdvisorById(teacherId);
            if (advisor == null || !StringUtils.hasText(advisor.getEmail())) {
                log.warn("无法发送指导记录确认通知：教师不存在或邮箱为空, teacherId={}", teacherId);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("advisorName", advisor.getRealName());
            params.put("paperTitle", paperTitle);
            params.put("studentName", studentName);
            params.put("round", String.valueOf(round));
            params.put("confirmTime", LocalDateTime.now().toString());

            boolean success = emailService.sendTaskCompletionEmail(
                    advisor.getEmail(), NoticeType.GUIDANCE_CONFIRMED, params, paperId, "PAPER");

            log.info("指导记录确认通知发送{}：teacher={}, paperTitle={}, round={}",
                    success ? "成功" : "失败", advisor.getRealName(), paperTitle, round);

        } catch (Exception e) {
            log.error("发送指导记录确认通知异常：paperId={}, teacherId={}", paperId, teacherId, e);
        }
    }

    /**
     * 发送评阅任务通知给评阅教师（论文全生命周期 P2）
     */
    public void sendReviewAssignNotice(Long reviewerId, Long paperId, String paperTitle,
                                       LocalDateTime deadline, String method) {
        try {
            SysUser teacher = userQueryService.getAdvisorById(reviewerId);
            if (teacher == null || !StringUtils.hasText(teacher.getEmail())) {
                log.warn("无法发送评阅任务通知：教师不存在或邮箱为空, reviewerId={}", reviewerId);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("teacherName", teacher.getRealName());
            params.put("paperTitle", paperTitle);
            params.put("deadline", deadline == null ? "未设置" : deadline.toString());
            params.put("method", "offline".equals(method) ? "线下评阅（请线下评阅后回填结果并上传扫描件）" : "线上评阅");

            boolean success = emailService.sendTaskCompletionEmail(
                    teacher.getEmail(), NoticeType.REVIEW_ASSIGN, params, paperId, "PAPER");

            log.info("评阅任务通知发送{}：reviewer={}，paperTitle={}",
                    success ? "成功" : "失败", teacher.getRealName(), paperTitle);

        } catch (Exception e) {
            log.error("发送评阅任务通知异常：paperId={}，reviewerId={}", paperId, reviewerId, e);
        }
    }

    /**
     * 发送答辩结论通知给学生（论文全生命周期 P3）。
     *
     * 由答辩组长提交结论后触发：终期 pass 时学生论文进入 defense_passed（成绩已回写 final_score），
     * fail 退回修改，postpone 缓辩择期重排。score/grade 仅 pass 时非空。
     */
    public void sendDefenseResultNotice(Long paperId, String paperTitle, Long studentId,
                                        String batchNo, String result, String score, String grade) {
        try {
            SysUser student = userQueryService.getUserById(studentId);
            if (student == null || !StringUtils.hasText(student.getEmail())) {
                log.warn("无法发送答辩结论通知：学生不存在或邮箱为空, studentId={}", studentId);
                return;
            }

            String resultText = switch (result) {
                case "pass" -> "通过";
                case "fail" -> "不通过（需退回修改后重新安排答辩）";
                case "postpone" -> "缓辩（择期重新安排答辩批次）";
                default -> result;
            };

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paperTitle);
            params.put("batchNo", batchNo);
            params.put("result", resultText);
            params.put("score", score == null ? "—" : score);
            params.put("grade", grade == null ? "—" : grade);
            params.put("submitTime", LocalDateTime.now().toString());

            boolean success = emailService.sendTaskCompletionEmail(
                    student.getEmail(), NoticeType.DEFENSE_RESULT, params, paperId, "PAPER");

            log.info("答辩结论通知发送{}：student={}，batchNo={}，result={}",
                    success ? "成功" : "失败", student.getRealName(), batchNo, result);

        } catch (Exception e) {
            log.error("发送答辩结论通知异常：paperId={}，studentId={}，batchNo={}", paperId, studentId, batchNo, e);
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
     * 发送查重报告签核退回通知给学生（论文全生命周期 P5）。
     *
     * 由导师签核 qualified=0（查重报告不合格）后触发：学生需按导师意见修改后重新提交再检。
     * 与 sendDefenseResultNotice 同一套兜底口径——取不到学生/邮箱只告警返回，
     * 任何异常内部吞掉只记日志，绝不阻断签核主流程。
     */
    public void sendCheckSignoffRejectedNotice(Long studentId, String paperTitle) {
        try {
            SysUser student = userQueryService.getUserById(studentId);
            if (student == null || !StringUtils.hasText(student.getEmail())) {
                log.warn("无法发送签核退回通知：学生不存在或邮箱为空, studentId={}", studentId);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paperTitle);
            params.put("submitTime", LocalDateTime.now().toString());

            // 无 paperId 入参（签核服务侧只持有学生与标题），审计 related_id 传 null，
            // 与 sendAccountActivationNotice 的 null relatedId 口径一致
            boolean success = emailService.sendTaskCompletionEmail(
                    student.getEmail(), NoticeType.CHECK_SIGNOFF_REJECTED, params, null, "PAPER");

            log.info("查重报告签核退回通知发送{}：student={}，paperTitle={}",
                    success ? "成功" : "失败", student.getRealName(), paperTitle);

        } catch (Exception e) {
            log.error("发送查重报告签核退回通知异常：paperTitle={}，studentId={}", paperTitle, studentId, e);
        }
    }

    /**
     * 发送总评成绩合成通知给学生（论文全生命周期 P5）。
     *
     * 终期答辩通过、加权合成（指导/评阅/答辩按 system_param 权重）成功回写 final_score 后触发；
     * 合成失败（缺指导分 40914 / 权重非法 40915）走不到这里，由管理端重算兜底。异常内部吞掉。
     */
    public void sendScoreSynthesizedNotice(Long paperId, String paperTitle, Long studentId, String finalScore) {
        try {
            SysUser student = userQueryService.getUserById(studentId);
            if (student == null || !StringUtils.hasText(student.getEmail())) {
                log.warn("无法发送总评成绩通知：学生不存在或邮箱为空, studentId={}", studentId);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paperTitle);
            params.put("finalScore", finalScore);
            params.put("submitTime", LocalDateTime.now().toString());

            boolean success = emailService.sendTaskCompletionEmail(
                    student.getEmail(), NoticeType.SCORE_SYNTHESIZED, params, paperId, "PAPER");

            log.info("总评成绩通知发送{}：student={}，paperTitle={}，finalScore={}",
                    success ? "成功" : "失败", student.getRealName(), paperTitle, finalScore);

        } catch (Exception e) {
            log.error("发送总评成绩通知异常：paperId={}，studentId={}", paperId, studentId, e);
        }
    }

    /**
     * 发送导师评价邀请通知给学生（论文全生命周期 P6a）。
     *
     * 终期答辩结论 pass（defense_passed 推进成功）后触发，软提醒定位——只邀请一次，
     * 不重复催办、不卡定稿/归档。与 sendCheckSignoffRejectedNotice 同一套兜底口径：
     * 取不到学生/邮箱只告警返回，任何异常内部吞掉只记日志，绝不阻断答辩主流程。
     */
    public void sendAdvisorEvalInviteNotice(Long paperId, String paperTitle, Long studentId) {
        try {
            SysUser student = userQueryService.getUserById(studentId);
            if (student == null || !StringUtils.hasText(student.getEmail())) {
                log.warn("无法发送导师评价邀请：学生不存在或邮箱为空, studentId={}", studentId);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paperTitle);
            params.put("submitTime", LocalDateTime.now().toString());

            boolean success = emailService.sendTaskCompletionEmail(
                    student.getEmail(), NoticeType.ADVISOR_EVAL_INVITE, params, paperId, "PAPER");

            log.info("导师评价邀请发送{}：student={}，paperTitle={}",
                    success ? "成功" : "失败", student.getRealName(), paperTitle);

        } catch (Exception e) {
            log.error("发送导师评价邀请异常：paperId={}，studentId={}", paperId, studentId, e);
        }
    }

    /**
     * 发送导师评价临期催评通知给学生（论文全生命周期 P6b）。
     *
     * 由 AdvisorEvalRemindScheduler 在窗口临期（剩 remind-days-before 天）时触发，
     * Redis SETNX 去重保证每个学生整个窗口只被催一次。与邀请通知同一套兜底口径：
     * 取不到学生/邮箱只告警返回，任何异常内部吞掉只记日志，绝不阻断调度主流程。
     */
    public void sendAdvisorEvalRemindNotice(Long paperId, String paperTitle, Long studentId,
                                            LocalDateTime windowEndTime) {
        try {
            SysUser student = userQueryService.getUserById(studentId);
            if (student == null || !StringUtils.hasText(student.getEmail())) {
                log.warn("无法发送导师评价催评：学生不存在或邮箱为空, studentId={}", studentId);
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("studentName", student.getRealName());
            params.put("paperTitle", paperTitle);
            params.put("windowEndTime", windowEndTime == null ? null : windowEndTime.toString());
            params.put("submitTime", LocalDateTime.now().toString());

            boolean success = emailService.sendTaskCompletionEmail(
                    student.getEmail(), NoticeType.ADVISOR_EVAL_REMIND, params, paperId, "PAPER");

            log.info("导师评价催评发送{}：student={}，paperTitle={}，窗口截止={}",
                    success ? "成功" : "失败", student.getRealName(), paperTitle, windowEndTime);

        } catch (Exception e) {
            log.error("发送导师评价催评异常：paperId={}，studentId={}", paperId, studentId, e);
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