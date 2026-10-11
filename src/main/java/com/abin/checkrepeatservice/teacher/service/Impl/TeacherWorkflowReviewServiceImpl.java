package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.admin.service.ReviewWorkflowService;
import com.abin.checkrepeatservice.admin.vo.ReviewWorkflowVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.component.PaperStageGate;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.ReviewWorkflowProgress;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.dto.WorkflowVoteReq;
import com.abin.checkrepeatservice.teacher.mapper.ReviewWorkflowProgressMapper;
import com.abin.checkrepeatservice.teacher.service.TeacherWorkflowReviewService;
import com.abin.checkrepeatservice.teacher.vo.WorkflowReviewVO;
import com.abin.checkrepeatservice.user.service.PaperStatusLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 多级初审服务实现（论文全生命周期 P2，ReviewWorkflow 配置壳接线）。
 *
 * 进度模型：
 *  - paper_info.workflow_step = 当前审批中的步骤序号（1-based，0=未进入工作流）；
 *  - review_workflow_progress 记录每票；(paper_id, step_index, approver_id) 唯一；
 *  - all：该步全部审批人投通过才过步；any：任一人投通过即过步；任一驳回 → 整体 rejected；
 *  - 最后一步通过 → 初审完成（auditing→completed，过 PaperStageGate）。
 */
@Slf4j
@Service
public class TeacherWorkflowReviewServiceImpl implements TeacherWorkflowReviewService {

    @Resource
    private ReviewWorkflowService reviewWorkflowService;

    @Resource
    private ReviewWorkflowProgressMapper progressMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PaperStageGate paperStageGate;

    @Resource
    private PaperStatusLogService paperStatusLogService;

    private static final String APPROVAL_ALL = "all";

    @Override
    public Result<List<WorkflowReviewVO>> listMyWorkflowTasks() {
        Long me = UserBusinessInfoUtils.getCurrentUserId();

        ReviewWorkflowVO workflow = reviewWorkflowService.getWorkflow();
        if (!Boolean.TRUE.equals(workflow.getEnabled()) || workflow.getSteps() == null
                || workflow.getSteps().isEmpty()) {
            return Result.success("当前未启用多级初审工作流", List.of());
        }
        List<ReviewWorkflowVO.Step> steps = workflow.getSteps();

        // 审批中的论文（多级初审阶段）：数量天然有限（初审进行中的论文），直接全量拉取后内存过滤
        List<PaperInfo> papers = paperInfoMapper.selectList(new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.AUDITING)
                .ge(PaperInfo::getWorkflowStep, 1)
                .orderByDesc(PaperInfo::getUpdateTime));
        if (papers.isEmpty()) {
            return Result.success("暂无初审工作流待办", List.of());
        }

        // 学生姓名批量解析
        List<Long> studentIds = papers.stream().map(PaperInfo::getStudentId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> studentNameMap = studentIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));

        List<WorkflowReviewVO> voList = new ArrayList<>();
        for (PaperInfo paper : papers) {
            int stepIndex = paper.getWorkflowStep() == null ? 0 : paper.getWorkflowStep();
            if (stepIndex < 1 || stepIndex > steps.size()) {
                // 步骤配置被删改后可能出现的脏进度：跳过并告警，不阻断他人待办
                log.warn("论文{}的workflow_step={}超出当前工作流步骤范围（共{}步），已跳过",
                        paper.getId(), stepIndex, steps.size());
                continue;
            }
            ReviewWorkflowVO.Step step = steps.get(stepIndex - 1);
            List<String> approvers = step.getApprovers() == null ? List.of() : step.getApprovers();
            if (!approvers.contains(String.valueOf(me))) {
                continue;
            }

            ReviewWorkflowProgress myVote = progressMapper.selectOne(
                    new LambdaQueryWrapper<ReviewWorkflowProgress>()
                            .eq(ReviewWorkflowProgress::getPaperId, paper.getId())
                            .eq(ReviewWorkflowProgress::getStepIndex, stepIndex)
                            .eq(ReviewWorkflowProgress::getApproverId, me));

            WorkflowReviewVO vo = new WorkflowReviewVO();
            vo.setPaperId(paper.getId());
            vo.setPaperTitle(paper.getPaperTitle());
            vo.setStudentName(studentNameMap.get(paper.getStudentId()));
            vo.setTeacherName(paper.getTeacherName());
            vo.setStepIndex(stepIndex);
            vo.setStepName(step.getName());
            vo.setApprovalType(step.getApprovalType());
            vo.setApproverNames(resolveNames(approvers));
            vo.setMyVoted(myVote != null);
            vo.setMyVoteResult(myVote == null ? null : myVote.getVoteResult());
            vo.setStepEnterTime(paper.getUpdateTime());
            voList.add(vo);
        }
        return Result.success("初审工作流待办查询成功", voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> vote(Long paperId, WorkflowVoteReq req) {
        Long me = UserBusinessInfoUtils.getCurrentUserId();

        PaperInfo paper = paperInfoMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }
        if (!DictConstants.PaperStatus.AUDITING.equals(paper.getPaperStatus())
                || paper.getWorkflowStep() == null || paper.getWorkflowStep() < 1) {
            throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS, "该论文当前不在多级初审阶段");
        }

        ReviewWorkflowVO workflow = reviewWorkflowService.getWorkflow();
        if (!Boolean.TRUE.equals(workflow.getEnabled()) || workflow.getSteps() == null
                || workflow.getSteps().isEmpty()) {
            throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS, "当前未启用多级初审工作流");
        }
        List<ReviewWorkflowVO.Step> steps = workflow.getSteps();
        int stepIndex = paper.getWorkflowStep();
        if (stepIndex > steps.size()) {
            throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS, "工作流步骤异常，请联系管理员检查工作流配置");
        }
        ReviewWorkflowVO.Step step = steps.get(stepIndex - 1);
        List<String> approvers = step.getApprovers() == null ? List.of() : step.getApprovers();
        if (!approvers.contains(String.valueOf(me))) {
            throw new BusinessException(ResultCode.PERMISSION_TEACHER_OWNER, "您不是该步骤的审批人");
        }

        ReviewWorkflowProgress existing = progressMapper.selectOne(
                new LambdaQueryWrapper<ReviewWorkflowProgress>()
                        .eq(ReviewWorkflowProgress::getPaperId, paperId)
                        .eq(ReviewWorkflowProgress::getStepIndex, stepIndex)
                        .eq(ReviewWorkflowProgress::getApproverId, me));
        if (existing != null) {
            throw new BusinessException(ResultCode.BUSINESS_NO_REPEAT, "您已对该步骤投过票");
        }

        boolean pass = "pass".equals(req.getResult());
        ReviewWorkflowProgress progress = new ReviewWorkflowProgress();
        progress.setPaperId(paperId);
        progress.setStepIndex(stepIndex);
        progress.setApproverId(me);
        progress.setVoteResult(pass ? ReviewWorkflowProgress.VOTE_PASS : ReviewWorkflowProgress.VOTE_REJECT);
        progress.setOpinion(StringUtils.hasText(req.getOpinion())
                ? Jsoup.clean(req.getOpinion(), Safelist.none()) : null);
        progress.setVoteTime(LocalDateTime.now());
        UserBusinessInfoUtils.setAuditField(progress, true);
        try {
            progressMapper.insert(progress);
        } catch (DuplicateKeyException e) {
            // 并发双击兜底：唯一索引 (paper_id, step_index, approver_id) 保证一人一票
            throw new BusinessException(ResultCode.BUSINESS_NO_REPEAT, "您已对该步骤投过票");
        }

        // 驳回：任一步骤驳回 → 论文整体退回修改
        if (!pass) {
            paperStageGate.assertTransition(DictConstants.PaperStatus.AUDITING,
                    DictConstants.PaperStatus.REJECTED);
            PaperInfo update = new PaperInfo();
            update.setId(paperId);
            update.setPaperStatus(DictConstants.PaperStatus.REJECTED);
            update.setWorkflowStep(0);
            paperInfoMapper.updateById(update);
            paperStatusLogService.recordStatusLog(paperId,
                    DictConstants.PaperStatus.AUDITING, DictConstants.PaperStatus.REJECTED,
                    String.format("多级初审第%d步（%s）驳回", stepIndex, step.getName()), me, null);
            log.info("多级初审驳回：paperId={}，stepIndex={}，approverId={}", paperId, stepIndex, me);
            return Result.success("投票成功：该论文多级初审未通过，已退回修改");
        }

        // 通过：all 语义需该步全部审批人投通过；any 语义任一人通过即过步
        boolean stepPassed;
        if (APPROVAL_ALL.equals(step.getApprovalType())) {
            Long approved = progressMapper.selectCount(new LambdaQueryWrapper<ReviewWorkflowProgress>()
                    .eq(ReviewWorkflowProgress::getPaperId, paperId)
                    .eq(ReviewWorkflowProgress::getStepIndex, stepIndex)
                    .eq(ReviewWorkflowProgress::getVoteResult, ReviewWorkflowProgress.VOTE_PASS));
            stepPassed = approved >= approvers.size();
        } else {
            stepPassed = true;
        }

        if (!stepPassed) {
            return Result.success(String.format("第%d步投票成功，等待其他审批人投票（全部审批模式）", stepIndex));
        }

        // 过步：还有后续步骤 → 推进到下一步；已是最后一步 → 初审完成
        if (stepIndex < steps.size()) {
            ReviewWorkflowVO.Step next = steps.get(stepIndex);
            PaperInfo update = new PaperInfo();
            update.setId(paperId);
            update.setWorkflowStep(stepIndex + 1);
            paperInfoMapper.updateById(update);
            paperStatusLogService.recordStatusLog(paperId,
                    DictConstants.PaperStatus.AUDITING, DictConstants.PaperStatus.AUDITING,
                    String.format("多级初审第%d步（%s）通过，进入第%d步（%s）",
                            stepIndex, step.getName(), stepIndex + 1, next.getName()), me, null);
            log.info("多级初审过步：paperId={}，stepIndex={}→{}", paperId, stepIndex, stepIndex + 1);
            return Result.success(String.format("第%d步（%s）通过，已进入第%d步（%s）",
                    stepIndex, step.getName(), stepIndex + 1, next.getName()));
        }

        paperStageGate.assertTransition(DictConstants.PaperStatus.AUDITING,
                DictConstants.PaperStatus.COMPLETED);
        PaperInfo update = new PaperInfo();
        update.setId(paperId);
        update.setPaperStatus(DictConstants.PaperStatus.COMPLETED);
        update.setWorkflowStep(0);
        paperInfoMapper.updateById(update);
        paperStatusLogService.recordStatusLog(paperId,
                DictConstants.PaperStatus.AUDITING, DictConstants.PaperStatus.COMPLETED,
                String.format("多级初审全部通过（共%d步），初审完成", steps.size()), me, null);
        log.info("多级初审完成：paperId={}，共{}步", paperId, steps.size());
        return Result.success("多级初审全部通过，论文初审完成");
    }

    // ------------------------------ 私有辅助方法 ------------------------------

    /**
     * 审批人ID列表 → 姓名（逗号分隔展示用）；解析失败的 ID 原样保留，不吞人。
     */
    private String resolveNames(List<String> approverIds) {
        List<Long> ids = approverIds.stream()
                .map(id -> {
                    try {
                        return Long.parseLong(id.trim());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return "";
        }
        Map<Long, SysUser> userMap = sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return ids.stream()
                .map(id -> {
                    SysUser u = userMap.get(id);
                    if (u == null) {
                        return String.valueOf(id);
                    }
                    return StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername();
                })
                .collect(Collectors.joining("、"));
    }
}
