package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.dto.ReviewAllocateReq;
import com.abin.checkrepeatservice.admin.mapper.PaperSubmitMapper;
import com.abin.checkrepeatservice.admin.service.AdminReviewAssignService;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.component.PaperStageGate;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperCheckSignoff;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperSubmit;
import com.abin.checkrepeatservice.pojo.entity.ReviewAssignment;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.mapper.PaperCheckSignoffMapper;
import com.abin.checkrepeatservice.teacher.mapper.ReviewAssignmentMapper;
import com.abin.checkrepeatservice.teacher.vo.ReviewAssignVO;
import com.abin.checkrepeatservice.user.service.PaperStatusLogService;
import com.abin.checkrepeatservice.user.service.Impl.NotificationFacadeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端评阅任务分配服务实现（论文全生命周期 P2）。
 *
 * 约束与规则：
 *  - 分配入口在初审通过（completed）之后；首次成功分配时经 PaperStageGate 推进 completed→reviewing；
 *  - 评阅人回避规则：默认不允许指导教师评阅本人指导的论文（admin.review-assign.avoid-advisor）；
 *  - 容量控制：单个教师同时进行中的评阅任务数上限（admin.review-assign.max-per-teacher）；
 *  - (paper_id, submit_version, reviewer_id) 唯一索引兜底并发，重复分配按跳过处理。
 */
@Slf4j
@Service
public class AdminReviewAssignServiceImpl implements AdminReviewAssignService {

    /** 进行中状态集合（待评阅/评阅中），用于容量统计 */
    private static final Set<Integer> ACTIVE_STATUSES = Set.of(
            ReviewAssignment.STATUS_PENDING, ReviewAssignment.STATUS_IN_PROGRESS);

    @Resource
    private ReviewAssignmentMapper reviewAssignmentMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private PaperSubmitMapper paperSubmitMapper;

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private PaperCheckSignoffMapper paperCheckSignoffMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PaperStageGate paperStageGate;

    @Resource
    private PaperStatusLogService paperStatusLogService;

    @Resource
    private NotificationFacadeService notificationFacadeService;

    /** 评阅人回避指导教师开关（评阅公正性：默认开启） */
    @Value("${admin.review-assign.avoid-advisor:true}")
    private boolean avoidAdvisor;

    /** 单个教师同时进行中的评阅任务上限（容量控制） */
    @Value("${admin.review-assign.max-per-teacher:10}")
    private int maxPerTeacher;

    /** 未显式指定截止时间时的默认评阅期限（天） */
    @Value("${admin.review-assign.deadline-days:7}")
    private int deadlineDays;

    /**
     * 查重签核门槛开关（论文全生命周期 P5）：默认开启。
     * true 时评阅分配前必须存在合格签核；灰度/应急可通过 check.signoff.required=false 整体关闭。
     */
    @Value("${check.signoff.required:true}")
    private boolean checkSignoffRequired;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> allocate(ReviewAllocateReq req) {
        Long operatorId = UserBusinessInfoUtils.getCurrentUserId();

        // 1. 论文校验：评阅任务只能在初审通过（completed）或评阅中（reviewing）时分配
        PaperInfo paper = paperInfoMapper.selectById(req.getPaperId());
        if (paper == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }
        String paperStatus = paper.getPaperStatus();
        if (!DictConstants.PaperStatus.COMPLETED.equals(paperStatus)
                && !DictConstants.PaperStatus.REVIEWING.equals(paperStatus)) {
            throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS,
                    "评阅任务只能在初审通过后分配，论文当前状态：" + paperStatus);
        }

        // 1.5 查重签核门槛（论文全生命周期 P5）：check.signoff.required=true 时，
        // 该论文最新一次 completed 查重任务必须已有导师签核且结论为合格（qualified=1），
        // 否则一律以 40912 拦下。语义口径：
        //   - 没有 completed 查重任务 → 无法签核 → 同样 40912（流程上学生必须先完成一次成功查重）；
        //   - 有任务但无签核记录 → 导师尚未在教师端签核 → 40912；
        //   - 最新签核 qualified=0（不合格退回）→ 查重未过、论文在退回修改环节 → 40912。
        // 正确操作路径：管理员推动学生完成再检，并让导师在教师端
        // POST /api/teacher/papers/{paperId}/check-signoff 签核合格后再来分配评阅；
        // 不提供绕过入口，防止查重未过的论文流入评阅环节。重复检测会产生新任务、
        // 新任务对应新签核（(paper_id, task_id) 唯一），所以这里永远校验"最新任务"的签核。
        if (checkSignoffRequired) {
            CheckTask latestTask = checkTaskMapper.selectOne(new LambdaQueryWrapper<CheckTask>()
                    .eq(CheckTask::getPaperId, req.getPaperId())
                    .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                    .orderByDesc(CheckTask::getCreateTime)
                    .orderByDesc(CheckTask::getId)
                    .last("LIMIT 1"));
            PaperCheckSignoff signoff = latestTask == null ? null
                    : paperCheckSignoffMapper.selectOne(new LambdaQueryWrapper<PaperCheckSignoff>()
                            .eq(PaperCheckSignoff::getPaperId, req.getPaperId())
                            .eq(PaperCheckSignoff::getTaskId, latestTask.getId())
                            .last("LIMIT 1"));
            if (signoff == null || !Integer.valueOf(1).equals(signoff.getQualified())) {
                throw new BusinessException(ResultCode.BUSINESS_CHECK_NOT_SIGNOFF);
            }
        }

        // 2. 分配方式与评阅人集合解析
        String assignType = StringUtils.hasText(req.getAssignType())
                ? req.getAssignType() : ReviewAssignment.ASSIGN_MANUAL;
        String reviewType = StringUtils.hasText(req.getReviewType())
                ? req.getReviewType() : ReviewAssignment.TYPE_PEER;
        String method = StringUtils.hasText(req.getMethod())
                ? req.getMethod() : ReviewAssignment.METHOD_ONLINE;
        LocalDateTime deadline = req.getDeadline() != null
                ? req.getDeadline() : LocalDateTime.now().plusDays(deadlineDays);
        Integer submitVersion = resolveVersion(req, paper.getId());

        Set<Long> existingReviewers = reviewAssignmentMapper.selectList(
                        new LambdaQueryWrapper<ReviewAssignment>()
                                .eq(ReviewAssignment::getPaperId, req.getPaperId())
                                .eq(ReviewAssignment::getSubmitVersion, submitVersion))
                .stream().map(ReviewAssignment::getReviewerId).collect(Collectors.toSet());

        List<Long> reviewerIds;
        if (ReviewAssignment.ASSIGN_AUTO.equals(assignType)) {
            reviewerIds = pickAutoCandidates(req, paper, existingReviewers);
        } else {
            if (CollectionUtils.isEmpty(req.getReviewerIds())) {
                throw new BusinessException(ResultCode.PARAM_EMPTY, "手动分配必须指定评阅教师列表");
            }
            // LinkedHashSet 去重并保持传参顺序
            reviewerIds = new ArrayList<>(new LinkedHashSet<>(req.getReviewerIds()));
        }

        // 3. 逐个落库；唯一索引冲突（并发重复分配）按跳过处理，不中断整批
        int successCount = 0;
        List<String> skipReasons = new ArrayList<>();
        List<Long> notifiedReviewers = new ArrayList<>();
        for (Long reviewerId : reviewerIds) {
            if (existingReviewers.contains(reviewerId)) {
                skipReasons.add("教师" + reviewerId + "：已有该论文同版本的评阅任务");
                continue;
            }
            SysUser reviewer = sysUserMapper.selectById(reviewerId);
            if (reviewer == null || !"TEACHER".equals(reviewer.getUserType())
                    || !Integer.valueOf(1).equals(reviewer.getStatus())) {
                skipReasons.add("教师" + reviewerId + "：不存在或不是启用状态的教师");
                continue;
            }
            try {
                ReviewAssignment assignment = new ReviewAssignment();
                assignment.setPaperId(req.getPaperId());
                assignment.setSubmitVersion(submitVersion);
                assignment.setReviewerId(reviewerId);
                assignment.setReviewType(reviewType);
                assignment.setMethod(method);
                assignment.setStatus(ReviewAssignment.STATUS_PENDING);
                assignment.setDeadline(deadline);
                assignment.setAssignType(assignType);
                UserBusinessInfoUtils.setAuditField(assignment, true);
                reviewAssignmentMapper.insert(assignment);
                successCount++;
                notifiedReviewers.add(reviewerId);
            } catch (DuplicateKeyException e) {
                skipReasons.add("教师" + reviewerId + "：已有该论文同版本的评阅任务（并发重复）");
            }
        }

        if (successCount == 0) {
            throw new BusinessException(ResultCode.BUSINESS_NO_REPEAT,
                    "未新增任何评阅任务：" + String.join("；", skipReasons));
        }

        // 4. 阶段门推进：初审通过 → 评阅中（仅首次分配时发生；reviewing→reviewing 不涉及流转）
        if (DictConstants.PaperStatus.COMPLETED.equals(paperStatus)) {
            paperStageGate.assertTransition(DictConstants.PaperStatus.COMPLETED,
                    DictConstants.PaperStatus.REVIEWING);
            PaperInfo updatePaper = new PaperInfo();
            updatePaper.setId(paper.getId());
            updatePaper.setPaperStatus(DictConstants.PaperStatus.REVIEWING);
            paperInfoMapper.updateById(updatePaper);
            paperStatusLogService.recordStatusLog(paper.getId(),
                    DictConstants.PaperStatus.COMPLETED, DictConstants.PaperStatus.REVIEWING,
                    "评阅任务已下发（" + successCount + "名评阅人），进入评阅阶段",
                    operatorId, null);
        }

        // 5. 通知被分配教师（门面吞异常，不影响主流程）
        for (Long reviewerId : notifiedReviewers) {
            notificationFacadeService.sendReviewAssignNotice(reviewerId, paper.getId(),
                    paper.getPaperTitle(), deadline, method);
        }

        log.info("评阅任务分配完成：paperId={}，assignType={}，成功={}，跳过={}",
                req.getPaperId(), assignType, successCount, skipReasons.size());

        Map<String, Object> resultMap = Map.of(
                "total", reviewerIds.size(),
                "success", successCount,
                "skipped", skipReasons.size(),
                "skipReasons", skipReasons);
        return Result.success("评阅任务分配完成", resultMap);
    }

    @Override
    public Result<Page<ReviewAssignVO>> listAssignments(Long paperId, Long reviewerId, Integer status,
                                                        Integer currentPage, Integer pageSize) {
        int page = currentPage == null || currentPage < 1 ? 1 : currentPage;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<ReviewAssignment> wrapper = new LambdaQueryWrapper<ReviewAssignment>()
                .orderByDesc(ReviewAssignment::getCreateTime);
        if (paperId != null) {
            wrapper.eq(ReviewAssignment::getPaperId, paperId);
        }
        if (reviewerId != null) {
            wrapper.eq(ReviewAssignment::getReviewerId, reviewerId);
        }
        if (status != null) {
            wrapper.eq(ReviewAssignment::getStatus, status);
        }

        Page<ReviewAssignment> result = reviewAssignmentMapper.selectPage(new Page<>(page, size), wrapper);
        Page<ReviewAssignVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(enrichForAdmin(result.getRecords()));
        return Result.success("评阅任务列表查询成功", voPage);
    }

    // ------------------------------ 私有辅助方法 ------------------------------

    /**
     * 解析评阅针对的版本号：显式指定优先，否则取该论文最新提交版本；从未提交过按 1。
     */
    private Integer resolveVersion(ReviewAllocateReq req, Long paperId) {
        if (req.getSubmitVersion() != null) {
            return req.getSubmitVersion();
        }
        PaperSubmit latest = paperSubmitMapper.selectOne(new LambdaQueryWrapper<PaperSubmit>()
                .eq(PaperSubmit::getPaperId, paperId)
                .orderByDesc(PaperSubmit::getSubmitVersion)
                .last("LIMIT 1"));
        return latest == null || latest.getSubmitVersion() == null ? 1 : latest.getSubmitVersion();
    }

    /**
     * 自动挑选评阅人：启用状态教师 → 回避指导教师 → 回避已有任务者 →
     * 按进行中任务数升序（负载均衡）且不超容量上限 → 取前 count 个。
     */
    private List<Long> pickAutoCandidates(ReviewAllocateReq req, PaperInfo paper, Set<Long> existingReviewers) {
        if (req.getCount() == null) {
            throw new BusinessException(ResultCode.PARAM_EMPTY, "自动分配必须指定人数");
        }

        List<SysUser> teachers = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserType, "TEACHER")
                .eq(SysUser::getStatus, 1));

        // 排除指导教师（回避规则）；paper.teacherId 为空时不做回避
        Set<Long> excluded = new HashSet<>(existingReviewers);
        if (avoidAdvisor && paper.getTeacherId() != null) {
            excluded.add(paper.getTeacherId());
        }

        List<Long> candidateIds = teachers.stream()
                .map(SysUser::getId)
                .filter(id -> !excluded.contains(id))
                .toList();
        if (candidateIds.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_NO_ADVISOR,
                    "暂无符合条件的可用评阅教师（回避规则/已有任务排除后为空）");
        }

        // 统计候选教师进行中的评阅任务数（负载均衡 + 容量控制）
        Map<Long, Long> activeCountMap = reviewAssignmentMapper.selectList(
                        new LambdaQueryWrapper<ReviewAssignment>()
                                .in(ReviewAssignment::getReviewerId, candidateIds)
                                .in(ReviewAssignment::getStatus, ACTIVE_STATUSES))
                .stream().collect(Collectors.groupingBy(ReviewAssignment::getReviewerId, Collectors.counting()));

        List<Long> picked = candidateIds.stream()
                .sorted((a, b) -> Long.compare(activeCountMap.getOrDefault(a, 0L),
                        activeCountMap.getOrDefault(b, 0L)))
                .filter(id -> activeCountMap.getOrDefault(id, 0L) < maxPerTeacher)
                .limit(req.getCount())
                .toList();
        if (picked.size() < req.getCount()) {
            throw new BusinessException(ResultCode.BUSINESS_NO_ADVISOR,
                    String.format("可用评阅教师不足：需要%d人，实际仅%d人（受回避规则与单教师任务上限约束）",
                            req.getCount(), picked.size()));
        }
        return picked;
    }

    /**
     * 管理端列表组装：全量可见（不脱敏），额外补评阅人姓名。
     */
    private List<ReviewAssignVO> enrichForAdmin(List<ReviewAssignment> assignments) {
        if (CollectionUtils.isEmpty(assignments)) {
            return List.of();
        }

        List<Long> paperIds = assignments.stream().map(ReviewAssignment::getPaperId).distinct().toList();
        Map<Long, PaperInfo> paperMap = paperInfoMapper.selectBatchIds(paperIds).stream()
                .collect(Collectors.toMap(PaperInfo::getId, Function.identity()));

        List<Long> reviewerIds = assignments.stream()
                .map(ReviewAssignment::getReviewerId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> reviewerNameMap = reviewerIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(reviewerIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));

        List<Long> studentIds = paperMap.values().stream()
                .map(PaperInfo::getStudentId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> studentNameMap = studentIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));

        return assignments.stream().map(a -> {
            PaperInfo paper = paperMap.get(a.getPaperId());
            ReviewAssignVO vo = buildBaseVO(a, paper);
            vo.setReviewerName(reviewerNameMap.get(a.getReviewerId()));
            vo.setStudentName(paper == null ? null : studentNameMap.get(paper.getStudentId()));
            return vo;
        }).toList();
    }

    private ReviewAssignVO buildBaseVO(ReviewAssignment assignment, PaperInfo paper) {
        ReviewAssignVO vo = new ReviewAssignVO();
        vo.setId(assignment.getId());
        vo.setPaperId(assignment.getPaperId());
        vo.setPaperTitle(paper == null ? null : paper.getPaperTitle());
        vo.setSubmitVersion(assignment.getSubmitVersion());
        vo.setReviewerId(assignment.getReviewerId());
        vo.setReviewType(assignment.getReviewType());
        vo.setMethod(assignment.getMethod());
        vo.setStatus(assignment.getStatus());
        vo.setScore(assignment.getScore());
        vo.setComment(assignment.getComment());
        vo.setAttachmentId(assignment.getAttachmentId());
        vo.setDeadline(assignment.getDeadline());
        vo.setAssignType(assignment.getAssignType());
        vo.setCreateTime(assignment.getCreateTime());
        vo.setTeacherName(paper == null ? null : paper.getTeacherName());
        return vo;
    }
}
