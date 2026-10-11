package com.abin.checkrepeatservice.schedule;

import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.ReviewAssignment;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.mapper.ReviewAssignmentMapper;
import com.abin.checkrepeatservice.user.service.Impl.NotificationFacadeService;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评阅任务超时扫描定时任务（论文全生命周期 P2）。
 *
 * 行为（默认关闭，由 schedule.review-assign.timeout-enabled 控制）：
 *  - 每 10 分钟扫描截止时间已过、仍处于待评阅/评阅中的任务 → 标记 status=3（已超时转派）；
 *  - admin.review-assign.auto-reassign=true 时自动改派：挑选一名新评阅人
 *    （回避指导教师与原评阅人、受单教师任务上限约束、按负载均衡）生成新任务并通知；
 *  - auto-reassign=false 时仅标记超时，由管理员在评阅分配页手动补派。
 *
 * 与 CheckTaskScheduler 同一套保守约定：默认不启用，确认输出后再打开。
 */
@Component
@Slf4j
public class ReviewAssignTimeoutScheduler {

    private static final Set<Integer> ACTIVE_STATUSES = Set.of(
            ReviewAssignment.STATUS_PENDING, ReviewAssignment.STATUS_IN_PROGRESS);

    @Resource
    private ReviewAssignmentMapper reviewAssignmentMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private NotificationFacadeService notificationFacadeService;

    /** 超时扫描开关，默认关：开启后会写库（标记超时/自动改派） */
    @Value("${schedule.review-assign.timeout-enabled:false}")
    private boolean timeoutEnabled;

    @Value("${admin.review-assign.auto-reassign:true}")
    private boolean autoReassign;

    @Value("${admin.review-assign.avoid-advisor:true}")
    private boolean avoidAdvisor;

    @Value("${admin.review-assign.max-per-teacher:10}")
    private int maxPerTeacher;

    @Value("${admin.review-assign.deadline-days:7}")
    private int deadlineDays;

    @Scheduled(fixedDelay = 600000) // 每10分钟扫描一次
    public void scanTimeoutAssignments() {
        if (!timeoutEnabled) {
            log.debug("评阅超时扫描已关闭（schedule.review-assign.timeout-enabled=false），跳过");
            return;
        }
        try {
            List<ReviewAssignment> overdue = reviewAssignmentMapper.selectList(
                    new LambdaQueryWrapper<ReviewAssignment>()
                            .in(ReviewAssignment::getStatus, ACTIVE_STATUSES)
                            .lt(ReviewAssignment::getDeadline, LocalDateTime.now()));
            if (overdue.isEmpty()) {
                return;
            }
            log.warn("发现{}个超时评阅任务，开始处理", overdue.size());

            Map<Long, PaperInfo> paperMap = paperInfoMapper.selectBatchIds(
                            overdue.stream().map(ReviewAssignment::getPaperId).distinct().toList())
                    .stream().collect(Collectors.toMap(PaperInfo::getId, Function.identity()));

            for (ReviewAssignment assignment : overdue) {
                try {
                    handleTimeout(assignment, paperMap.get(assignment.getPaperId()));
                } catch (Exception e) {
                    // 单条失败不阻断本轮扫描，下一轮会重试该任务（仍是超时状态）
                    log.error("超时评阅任务处理失败：assignmentId={}", assignment.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("评阅超时扫描执行失败", e);
        }
    }

    private void handleTimeout(ReviewAssignment assignment, PaperInfo paper) {
        // 1. 标记原任务已超时转派（保留审计，不再可提交）
        ReviewAssignment mark = new ReviewAssignment();
        mark.setId(assignment.getId());
        mark.setStatus(ReviewAssignment.STATUS_TIMEOUT);
        reviewAssignmentMapper.updateById(mark);

        // 2. 自动改派（可关）：挑一名新评阅人生成新任务
        if (!autoReassign) {
            log.info("评阅任务已标记超时（自动改派关闭，请管理员手动补派）：assignmentId={}，paperId={}",
                    assignment.getId(), assignment.getPaperId());
            return;
        }

        Long newReviewerId = pickCandidate(assignment, paper);
        if (newReviewerId == null) {
            log.warn("评阅任务超时但无可用改派教师，请管理员手动补派：assignmentId={}，paperId={}",
                    assignment.getId(), assignment.getPaperId());
            return;
        }

        LocalDateTime newDeadline = LocalDateTime.now().plusDays(deadlineDays);
        ReviewAssignment reassign = new ReviewAssignment();
        reassign.setPaperId(assignment.getPaperId());
        reassign.setSubmitVersion(assignment.getSubmitVersion());
        reassign.setReviewerId(newReviewerId);
        reassign.setReviewType(assignment.getReviewType());
        reassign.setMethod(assignment.getMethod());
        reassign.setStatus(ReviewAssignment.STATUS_PENDING);
        reassign.setDeadline(newDeadline);
        reassign.setAssignType(ReviewAssignment.ASSIGN_AUTO);
        reviewAssignmentMapper.insert(reassign);

        String paperTitle = paper == null ? null : paper.getPaperTitle();
        notificationFacadeService.sendReviewAssignNotice(newReviewerId, assignment.getPaperId(),
                paperTitle, newDeadline, assignment.getMethod());
        log.info("评阅任务超时自动改派完成：原任务={}，新任务={}，新评阅人={}",
                assignment.getId(), reassign.getId(), newReviewerId);
    }

    /**
     * 改派候选挑选：启用状态教师 → 回避指导教师与原评阅人 →
     * 进行中任务数不超上限 → 按负载升序取最少的一个。
     */
    private Long pickCandidate(ReviewAssignment assignment, PaperInfo paper) {
        List<SysUser> teachers = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, "TEACHER")
                .eq(SysUser::getStatus, 1));

        // 计算排除集合：原评阅人必排除；启用回避规则时同时排除指导教师
        // 使用不可变 Set 一次性构造，避免后续在 lambda 中引用被重新赋值的局部变量
        Set<Long> excluded;
        if (avoidAdvisor && paper != null && paper.getTeacherId() != null) {
            excluded = Set.of(assignment.getReviewerId(), paper.getTeacherId());
        } else {
            excluded = Set.of(assignment.getReviewerId());
        }

        // excluded 在此后不再被重新赋值，保持 effectively final 以便 lambda 引用
        final Set<Long> finalExcluded = excluded;

        List<Long> candidateIds = teachers.stream()
                .map(SysUser::getId)
                .filter(id -> !finalExcluded.contains(id))
                .toList();
        if (candidateIds.isEmpty()) {
            return null;
        }
        Map<Long, Long> activeCountMap = reviewAssignmentMapper.selectList(
                        new LambdaQueryWrapper<ReviewAssignment>()
                                .in(ReviewAssignment::getReviewerId, candidateIds)
                                .in(ReviewAssignment::getStatus, ACTIVE_STATUSES))
                .stream().collect(Collectors.groupingBy(ReviewAssignment::getReviewerId, Collectors.counting()));

        return candidateIds.stream()
                .filter(id -> activeCountMap.getOrDefault(id, 0L) < maxPerTeacher)
                .min((a, b) -> Long.compare(activeCountMap.getOrDefault(a, 0L),
                        activeCountMap.getOrDefault(b, 0L)))
                .orElse(null);
    }
}
