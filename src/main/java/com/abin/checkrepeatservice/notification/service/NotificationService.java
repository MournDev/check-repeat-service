package com.abin.checkrepeatservice.notification.service;

import com.abin.checkrepeatservice.common.Result;

/**
 * 消息通知服务
 *
 * 全项目发站内信的唯一入口：查模板、填参数、交给 MessageService.sendMessageByTemplate，
 * 由它负责落 system_message 表 + WebSocket 推给在线用户。
 *
 * 原本还有第二个同类服务 InternalMessageNotificationService（在 user/service/Impl 下，
 * 包名像是站内信的正统入口，实际只是个重复实现），两边各有方法、各有死代码，
 * 且方法重名，调错就会对同一个事件发两条通知。该服务已合并到这里。
 *
 * 注意：模板文案不在代码里，在 message_template 表。本类只负责把变量凑齐，
 * 改了某个模板引用的变量名，必须同步改 db/种子数据.sql 段 6（message_template）。
 */
public interface NotificationService {

    /**
     * 发送论文状态变更通知给学生
     * @param studentId 学生ID
     * @param paperId 论文ID
     * @param paperTitle 论文标题
     * @param newStatus 新状态
     * @param remarks 备注信息
     */
    Result<Boolean> sendPaperStatusChangeNotification(Long studentId, Long paperId,
                                                     String paperTitle, String newStatus, String remarks);

    /**
     * 发送论文审核结果通知给学生
     * @param studentId 学生ID
     * @param paperId 论文ID
     * @param paperTitle 论文标题
     * @param reviewResult 审核结果（通过/不通过）
     * @param reviewComments 审核意见
     */
    Result<Boolean> sendPaperReviewResultNotification(Long studentId, Long paperId,
                                                     String paperTitle, String reviewResult, String reviewComments);

    /**
     * 发送系统公告给指定用户群体
     *
     * @param targetType 目标类型（ALL/STUDENT/TEACHER/ADMIN）。当前实现只写一条系统公告，
     *                   未按 targetType 查用户列表，故该参数仅进日志，不参与发送。
     * @param title 公告标题
     * @param content 公告内容
     * @param priority 优先级
     */
    Result<Boolean> sendSystemAnnouncement(String targetType, String title,
                                          String content, String priority);

    /**
     * 发送查重结果通知给学生（相似度 + 风险等级）
     *
     * 由 CheckTaskEventListener 在查重完成、报告与明细均已落库之后调用。
     * @param studentId 学生ID
     * @param paperId 论文ID
     * @param paperTitle 论文标题
     * @param similarityRate 相似度（百分比数值，如 12.34）
     * @param riskLevel 风险等级，取 {@link com.abin.checkrepeatservice.common.utils.RiskLevelUtils#of} 的结果
     */
    Result<Boolean> sendSimilarityCheckResultNotification(Long studentId, Long paperId,
                                                         String paperTitle, Double similarityRate, String riskLevel);

    /**
     * 发送紧急系统通知
     * @param userId 用户ID
     * @param title 通知标题
     * @param content 通知内容
     */
    Result<Boolean> sendEmergencyNotification(Long userId, String title, String content);

    /**
     * 发送教师分配通知 - 站内信
     */
    void sendTeacherAssignmentNotice(Long paperId, String paperTitle, Long studentId, Long teacherId);

    /**
     * 发送学生分配结果通知 - 站内信
     */
    void sendStudentAssignmentNotice(Long paperId, String paperTitle, Long studentId, Long teacherId);

    /**
     * 发送超时自动拒绝通知 - 站内信
     *
     * 由 TeacherAssignmentServiceImpl.doRejectAssignment(byTimeout=true)（即 AssignmentTimeoutTask
     * 每天凌晨的分配超时处理）调用。超时场景老师并没有操作过，不能复用「指导老师已拒绝接收」那条文案。
     */
    void sendTimeoutRejectNotice(Long paperId, Long studentId, Long teacherId);

    /**
     * 发送系统通知 - 通用方法
     * @param userId 接收用户ID
     * @param title 通知标题
     * @param content 通知内容
     * @param noticeType 通知类型（用于模板匹配）
     * @param relatedId 关联ID（如论文ID等），可为 null
     * @return 发送结果
     */
    Result<Boolean> sendSystemNotice(Long userId, String title, String content, String noticeType, Long relatedId);
}
