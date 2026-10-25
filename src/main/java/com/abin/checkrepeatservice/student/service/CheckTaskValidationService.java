package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 单用户发起查重的最小间隔（秒），用于入口防刷 */
    @Value("${check.rate-limit.user-window-seconds:30}")
    private long userRateWindowSeconds;

    /**
     * 完整的查重前置校验（单次发起：带用户级限流）
     */
    public ValidationResult validateCheckRequest(Long paperId, Long studentId) {
        return validateCheckRequest(paperId, studentId, true);
    }

    /**
     * 完整的查重前置校验。
     *
     * @param applyUserRateLimit 是否消耗“单用户 30 秒一次”的限流额度；
     *                           批量查重由一次用户动作触发，应传 false，避免逐篇被拦。
     */
    public ValidationResult validateCheckRequest(Long paperId, Long studentId,
                                                 boolean applyUserRateLimit) {
        if (applyUserRateLimit) {
            enforceUserRateLimit();
        }

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
        
        // 5. 通过：并发容量由 CheckTaskDispatchScheduler 统一控制。
        //    入口不再按 running 数拒绝，否则 pending 队列永远攒不起来（5000 份场景会
        //    在 API 层被挡回，而不是进入 DB 队列）。真正防刷由上面的用户级限流负责。
        result.setSuccess(true);
        result.setPaper(paper);
        Long pendingCount = checkTaskMapper.selectCount(new LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.PENDING)
                .eq(CheckTask::getIsDeleted, 0));
        result.setQueuePosition((int) ((pendingCount == null ? 0 : pendingCount) + 1));
        
        log.info("查重前置校验通过 - 论文 ID: {}, 排队位置：{}", paperId, result.getQueuePosition());
        return result;
    }

    /**
     * 单用户入口限流：默认 30 秒内只允许发起 1 次查重。
     *
     * <p>使用 Redis SET NX + TTL 实现固定窗口；Redis 不可用时放行（限流是防刷手段，
     * 不应成为业务可用性的单点）。批量查重通过 {@code applyUserRateLimit=false} 跳过。</p>
     */
    private void enforceUserRateLimit() {
        Long userId;
        try {
            userId = UserBusinessInfoUtils.getCurrentUserId();
        } catch (Exception e) {
            // 论文提交后的自动查重运行在后台线程，没有 SecurityContext：跳过用户级限流
            log.debug("无登录上下文，跳过查重入口限流");
            return;
        }
        if (userId == null) {
            return;
        }
        String key = "check:rate:user:" + userId;
        try {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    key, "1", Math.max(1, userRateWindowSeconds), TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(acquired)) {
                log.warn("查重入口限流命中 - userId: {}", userId);
                throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT,
                        "操作过于频繁，请稍后再试", null);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("用户级限流检查失败（Redis 异常），放行本次请求 - userId: {}, 原因: {}",
                    userId, e.getMessage());
        }
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
