package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 查重任务前置校验服务
 * 负责所有查重请求的合法性验证
 */
@Service
@Slf4j
public class CheckTaskValidationService {

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Value("${admin.check-rule.default-max-count}")
    private int defaultMaxCount;

    @Value("${admin.check-rule.default-interval}")
    private Long checkInterval;

    @Value("${check.task.max-concurrent}")
    private int maxConcurrentTasks;

    /**
     * 完整的查重前置校验
     * @param paperId 论文 ID
     * @param studentId 学生 ID
     * @return 校验结果
     */
    public ValidationResult validateCheckRequest(Long paperId, Long studentId) {
        log.info("开始查重前置校验 - 论文 ID: {}, 学生 ID: {}", paperId, studentId);
        
        ValidationResult result = new ValidationResult();
        
        // 1. 校验论文是否存在且属于该学生
        PaperInfo paper = checkPaperOwnership(paperId, studentId);
        if (paper == null) {
            result.setSuccess(false);
            result.setMessage("论文不存在或无权访问");
            return result;
        }
        
        // 2. 校验论文状态是否允许查重
        if (!isPaperEligibleForCheck(paper)) {
            result.setSuccess(false);
            result.setMessage(String.format("当前论文状态不允许发起查重（当前状态：%s）", 
                                          getPaperStatusName(paper.getPaperStatus())));
            return result;
        }
        
        // 3. 校验历史查重次数
        List<CheckTask> historyTasks = getHistoryCheckTasks(paperId);
        if (historyTasks.size() >= defaultMaxCount) {
            result.setSuccess(false);
            result.setMessage(String.format("该论文已达到最大查重次数（%d次），无法继续发起", defaultMaxCount));
            return result;
        }
        
        // 4. 校验二次查重间隔
        if (!historyTasks.isEmpty()) {
            CheckTask lastTask = historyTasks.get(0);
            long interval = Duration.between(lastTask.getCreateTime(), LocalDateTime.now()).getSeconds();
            if (interval < checkInterval) {
                long remainingSeconds = checkInterval - interval;
                result.setSuccess(false);
                result.setMessage(String.format("二次查重需间隔%d秒，剩余%d秒后可发起", 
                                              checkInterval, remainingSeconds));
                result.setRetryAfter(remainingSeconds);
                return result;
            }
        }
        
        // 5. 校验系统并发负载
        long runningCount = getRunningTaskCount();
        if (runningCount >= maxConcurrentTasks) {
            int waitMinutes = estimateWaitTime(runningCount);
            result.setSuccess(false);
            result.setMessage(String.format("当前系统查重任务繁忙，预计等待%d分钟，请稍后再试", waitMinutes));
            result.setRetryAfter(waitMinutes * 60L);
            return result;
        }
        
        // 所有校验通过
        result.setSuccess(true);
        result.setPaper(paper);
        result.setQueuePosition((int)(runningCount + 1));
        
        log.info("查重前置校验通过 - 论文 ID: {}, 排队位置：{}", paperId, result.getQueuePosition());
        return result;
    }
    
    /**
     * 校验论文所有权
     */
    private PaperInfo checkPaperOwnership(Long paperId, Long studentId) {
        PaperInfo paper = paperInfoMapper.selectById(paperId);
        if (paper == null || paper.getIsDeleted() == 1) {
            return null;
        }
        
        // 严格校验：只有论文所属学生可发起查重
        if (!paper.getStudentId().equals(studentId)) {
            log.warn("学生尝试为他人论文发起查重 - 论文 ID: {}, 学生 ID: {}, 实际作者 ID: {}", 
                    paperId, studentId, paper.getStudentId());
            return null;
        }
        
        return paper;
    }
    
    /**
     * 检查论文状态是否允许查重
     */
    private boolean isPaperEligibleForCheck(PaperInfo paper) {
        String status = paper.getPaperStatus();
        // 只有以下状态允许发起查重
        return DictConstants.PaperStatus.ASSIGNED.equals(status) ||
               DictConstants.PaperStatus.PENDING.equals(status) ||
               DictConstants.PaperStatus.CHECKING.equals(status); // 允许重新触发
    }
    
    /**
     * 获取历史查重任务
     */
    private List<CheckTask> getHistoryCheckTasks(Long paperId) {
        return checkTaskMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getPaperId, paperId)
                .eq(CheckTask::getIsDeleted, 0)
                .orderByDesc(CheckTask::getCreateTime)
        );
    }
    
    /**
     * 获取运行中的任务数
     */
    private long getRunningTaskCount() {
        return checkTaskMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.CHECKING)
                .eq(CheckTask::getIsDeleted, 0)
        );
    }
    
    /**
     * 预估等待时间（分钟）
     * 单任务耗时取最近已完成任务的真实 duration_seconds 均值，无历史样本时才退回 3 分钟。
     */
    private int estimateWaitTime(long runningCount) {
        int avgDurationMinutes = getAverageDurationMinutes();
        int availableSlots = maxConcurrentTasks - (int)runningCount;
        if (availableSlots <= 0) {
            return avgDurationMinutes;
        }
        return (int)(runningCount / availableSlots) * avgDurationMinutes;
    }

    /**
     * 最近已完成任务的平均耗时（分钟）。
     * 取最近 100 条真实 duration_seconds 的均值；无样本返回 3 分钟兜底。
     */
    private int getAverageDurationMinutes() {
        try {
            List<CheckTask> recent = checkTaskMapper.selectList(
                new LambdaQueryWrapper<CheckTask>()
                    .isNotNull(CheckTask::getDurationSeconds)
                    .gt(CheckTask::getDurationSeconds, 0)
                    .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                    .eq(CheckTask::getIsDeleted, 0)
                    .orderByDesc(CheckTask::getEndTime)
                    .last("LIMIT 100")
                    .select(CheckTask::getDurationSeconds)
            );
            if (recent.isEmpty()) {
                return 3;
            }
            long sum = recent.stream().mapToLong(CheckTask::getDurationSeconds).sum();
            return Math.max(1, (int) Math.ceil(sum / 100.0 / recent.size()));
        } catch (Exception e) {
            log.warn("获取历史平均耗时失败，等待预估退回 3 分钟", e);
            return 3;
        }
    }
    
    /**
     * 获取论文状态名称
     */
    private String getPaperStatusName(String statusCode) {
        switch (statusCode) {
            case DictConstants.PaperStatus.PENDING: return "待分配";
            case DictConstants.PaperStatus.ASSIGNED: return "已分配";
            case DictConstants.PaperStatus.CHECKING: return "查重中";
            case DictConstants.PaperStatus.AUDITING: return "审核中";
            case DictConstants.PaperStatus.COMPLETED: return "已完成";
            case DictConstants.PaperStatus.REJECTED: return "已驳回";
            case DictConstants.PaperStatus.WITHDRAWN: return "已撤回";
            default: return "未知状态";
        }
    }
    
    /**
     * 校验结果封装类
     */
    @Data
    public static class ValidationResult {
        /**
         * 校验是否通过
         */
        private boolean success;
        
        /**
         * 失败消息
         */
        private String message;
        
        /**
         * 建议重试时间（秒）
         */
        private Long retryAfter;
        
        /**
         * 通过的论文对象
         */
        private PaperInfo paper;
        
        /**
         * 当前排队位置
         */
        private int queuePosition;
    }
}
