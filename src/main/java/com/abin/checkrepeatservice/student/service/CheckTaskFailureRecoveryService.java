package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.admin.service.CollegeQuotaService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.constant.PaperNoticeConstants;
import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.common.statemachine.CheckTaskStateMachine;
import com.abin.checkrepeatservice.notification.service.NotificationService;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 查重任务失败统一恢复服务。
 *
 * <p>执行顺序固定为：任务置 failure -> 退还学院额度 -> 回滚论文状态 -> 发送站内信。
 * 监听器失败分支与超时扫描共用本方法，避免两条链路各自实现导致状态不一致。</p>
 *
 * <p>不回滚论文状态的情形：论文已不在 {@code checking}（例如学生已撤回、已重新提交，
 * 或上一次查重已完成进入 auditing）。此时只处理任务与额度，不覆盖业务状态。</p>
 */
@Slf4j
@Service
public class CheckTaskFailureRecoveryService {

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private CheckTaskStateMachine stateMachine;

    @Resource
    private CollegeQuotaService collegeQuotaService;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private NotificationService notificationService;

    /**
     * 将任务置为失败，并恢复论文状态、退还额度、通知学生。
     *
     * @param taskId 任务ID
     * @param reason 失败原因（会落 fail_reason 并展示给学生）
     * @return true=本次调用真正完成了失败流转；false=任务不存在、已终态或已被并发改写
     */
    public boolean failTaskAndRestorePaper(Long taskId, String reason) {
        if (taskId == null) {
            return false;
        }

        CheckTask task = checkTaskMapper.selectById(taskId);
        if (task == null || Integer.valueOf(1).equals(task.getIsDeleted())) {
            log.warn("查重失败恢复跳过：任务不存在或已删除 - taskId={}", taskId);
            return false;
        }

        CheckTaskStatusEnum currentStatus = resolveStatus(task.getCheckStatus());
        if (currentStatus != CheckTaskStatusEnum.PENDING
                && currentStatus != CheckTaskStatusEnum.CHECKING) {
            log.info("查重失败恢复跳过：任务已是终态 - taskId={}, status={}", taskId, task.getCheckStatus());
            return false;
        }

        String failReason = reason == null || reason.isBlank() ? "查重任务执行失败" : reason;
        boolean transitioned = stateMachine.transitionStatus(
                taskId, currentStatus, CheckTaskStatusEnum.FAILURE, failReason);
        if (!transitioned) {
            log.info("查重失败恢复跳过：任务状态已被并发改写 - taskId={}, expected={}",
                    taskId, currentStatus.getCode());
            return false;
        }

        try {
            collegeQuotaService.refundForFailedTask(taskId);
        } catch (Exception e) {
            // 额度返还失败不能阻断论文状态恢复与通知
            log.error("查重失败返还学院额度异常 - taskId={}", taskId, e);
        }

        PaperInfo paper = paperInfoMapper.selectById(task.getPaperId());
        restorePaperStatus(paper);
        notifyStudent(paper, failReason);
        return true;
    }

    /**
     * 仅当论文仍处于 checking 时回滚：有导师回 assigned，无导师回 pending。
     * 使用条件更新，避免并发撤回/重新提交后把新状态覆盖。
     */
    private void restorePaperStatus(PaperInfo paper) {
        if (paper == null) {
            return;
        }
        if (!DictConstants.PaperStatus.CHECKING.equals(paper.getPaperStatus())) {
            return;
        }

        String restoreStatus = paper.getTeacherId() != null
                ? DictConstants.PaperStatus.ASSIGNED
                : DictConstants.PaperStatus.PENDING;

        LambdaUpdateWrapper<PaperInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PaperInfo::getId, paper.getId())
                .eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.CHECKING)
                .eq(PaperInfo::getIsDeleted, 0)
                .set(PaperInfo::getPaperStatus, restoreStatus)
                .set(PaperInfo::getCheckTime, null)
                .set(PaperInfo::getUpdateTime, LocalDateTime.now());

        int rows = paperInfoMapper.update(null, wrapper);
        if (rows > 0) {
            log.info("查重失败已恢复论文状态 - paperId={}, {} -> {}", paper.getId(),
                    DictConstants.PaperStatus.CHECKING, restoreStatus);
        } else {
            log.warn("查重失败恢复论文状态跳过（状态已被并发改写）- paperId={}", paper.getId());
        }
    }

    /**
     * 发送失败站内信；通知失败只记日志，不影响失败恢复结果。
     */
    private void notifyStudent(PaperInfo paper, String reason) {
        if (paper == null || paper.getStudentId() == null) {
            return;
        }
        try {
            String paperTitle = paper.getPaperTitle() == null || paper.getPaperTitle().isBlank()
                    ? "未命名论文" : paper.getPaperTitle();
            String safeReason = reason.length() > 300 ? reason.substring(0, 300) + "..." : reason;
            String content = String.format(
                    "您的论文《%s》查重未完成：%s。请稍后重新发起查重，如多次失败请联系管理员。",
                    paperTitle, safeReason);

            Result<Boolean> result = notificationService.sendSystemNotice(
                    paper.getStudentId(),
                    "查重任务失败",
                    content,
                    PaperNoticeConstants.NOTICE_TYPE_CHECK_FAILED,
                    paper.getId());
            if (result == null || !result.isSuccess()) {
                log.warn("查重失败站内信发送未成功 - paperId={}, message={}",
                        paper.getId(), result == null ? "空响应" : result.getMessage());
            }
        } catch (Exception e) {
            log.error("查重失败站内信发送异常 - paperId={}", paper.getId(), e);
        }
    }

    private CheckTaskStatusEnum resolveStatus(String code) {
        try {
            return CheckTaskStatusEnum.fromCode(code);
        } catch (Exception e) {
            return null;
        }
    }
}
