package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.Impl.DictService;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.AdvisorEvaluationMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.AdvisorEvaluation;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperStatusLog;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.dto.AdvisorEvalStatusVO;
import com.abin.checkrepeatservice.student.dto.AdvisorEvalSubmitReq;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.AdvisorEvaluationService;
import com.abin.checkrepeatservice.user.mapper.PaperStatusLogMapper;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 学生端导师评价服务实现（论文全生命周期 P6a）。
 *
 * 关键规则：
 *  - 软提醒定位：评价不卡定稿/归档阶段门，只作教师考核数据源；
 *  - 可评时机：paper_status ∈ {defense_passed, finalized} 且已分配导师；
 *  - 窗口：最新一条 defense_passed 状态日志时间 + admin.advisor-eval.window-days（默认30天），
 *    超窗抛 40919；存量无日志论文不限窗口（老数据没有日志可循，一刀切关窗不公平）；
 *  - 每篇论文一次且不可改：uk_paper_id 库级兜底，先查库给 40918 业务语义；
 *  - 标签白名单：仅接受字典 advisor_eval_tag 的 dict_value，防脏数据进统计；
 *  - 评语纯文本清洗（jsoup Safelist.none），与签核/答辩评语同口径。
 */
@Slf4j
@Service
public class AdvisorEvaluationServiceImpl implements AdvisorEvaluationService {

    /** 评价窗口天数（答辩通过后 N 天内可评），application.yml admin.advisor-eval.window-days */
    @Value("${admin.advisor-eval.window-days:30}")
    private int evalWindowDays;

    @Resource
    private AdvisorEvaluationMapper advisorEvaluationMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private PaperStatusLogMapper paperStatusLogMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private DictService dictService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> submit(AdvisorEvalSubmitReq req) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();

        // 1. 论文存在性 + 本人归属校验（防替他人评价）
        PaperInfo paper = paperInfoMapper.selectById(req.getPaperId());
        if (paper == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除", null);
        }
        if (!Objects.equals(paper.getStudentId(), currentUserId)) {
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "仅论文学生本人可评价导师", null);
        }

        // 2. 可评时机：终期答辩通过后（defense_passed/finalized）开放
        if (!DictConstants.PaperStatus.DEFENSE_PASSED.equals(paper.getPaperStatus())
                && !DictConstants.PaperStatus.FINALIZED.equals(paper.getPaperStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_EVAL_NOT_READY);
        }
        // 未分配导师的论文没有评价对象（历史遗留数据可能存在 teacherId 为空的通过论文）
        if (paper.getTeacherId() == null) {
            throw new BusinessException(ResultCode.BUSINESS_EVAL_NOT_READY, "论文尚未分配指导教师，暂不可评价", null);
        }

        // 3. 重复提交校验：uk_paper_id 库级兜底，这里先查给出 40918 业务语义
        long submittedCount = advisorEvaluationMapper.selectCount(new LambdaQueryWrapper<AdvisorEvaluation>()
                .eq(AdvisorEvaluation::getPaperId, req.getPaperId()));
        if (submittedCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_EVAL_ALREADY_SUBMITTED);
        }

        // 4. 窗口校验：defense_passed 日志时间 + windowDays；存量无日志不限制
        LocalDateTime windowEnd = resolveWindowEnd(req.getPaperId());
        if (windowEnd != null && LocalDateTime.now().isAfter(windowEnd)) {
            throw new BusinessException(ResultCode.BUSINESS_EVAL_WINDOW_CLOSED);
        }

        // 5. 标签白名单校验：仅接受字典 advisor_eval_tag 的 dict_value
        if (req.getTags() != null && !req.getTags().isEmpty()) {
            Map<String, String> tagMap = dictService.getDictValueMap(DictConstants.DICT_ADVISOR_EVAL_TAG);
            List<String> invalidTags = req.getTags().stream()
                    .filter(t -> !tagMap.containsKey(t))
                    .toList();
            if (!invalidTags.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_VALUE_INVALID,
                        "存在无效标签：" + String.join("、", invalidTags), null);
            }
        }

        // 6. 落库：teacher_id 取提交时刻快照，后续改派导师不影响统计归属。
        // 步骤3先查后插存在竞态窗口（并发双击同时通过 selectCount 检查），由 uk_paper_id
        // 唯一索引兜底；撞索引抛 DuplicateKeyException 转业务语义 40918，避免兜底成 500。
        AdvisorEvaluation evaluation = new AdvisorEvaluation();
        evaluation.setPaperId(req.getPaperId());
        evaluation.setStudentId(currentUserId);
        evaluation.setTeacherId(paper.getTeacherId());
        evaluation.setScoreTimeliness(req.getScoreTimeliness());
        evaluation.setScoreQuality(req.getScoreQuality());
        evaluation.setScoreAttitude(req.getScoreAttitude());
        evaluation.setScoreProfessional(req.getScoreProfessional());
        evaluation.setScoreOverall(req.getScoreOverall());
        evaluation.setTags(req.getTags() == null || req.getTags().isEmpty()
                ? null : JSON.toJSONString(req.getTags()));
        evaluation.setComment(StringUtils.hasText(req.getComment())
                ? Jsoup.clean(req.getComment(), Safelist.none()) : null);
        UserBusinessInfoUtils.setAuditField(evaluation, true);
        try {
            advisorEvaluationMapper.insert(evaluation);
        } catch (DuplicateKeyException e) {
            log.warn("导师评价并发重复提交被唯一索引拦截：paperId={}，studentId={}", req.getPaperId(), currentUserId);
            throw new BusinessException(ResultCode.BUSINESS_EVAL_ALREADY_SUBMITTED);
        }

        log.info("导师评价提交成功：evalId={}，paperId={}，studentId={}，teacherId={}，overall={}",
                evaluation.getId(), req.getPaperId(), currentUserId, paper.getTeacherId(), req.getScoreOverall());

        return Result.success("导师评价提交成功", String.valueOf(evaluation.getId()));
    }

    @Override
    public Result<AdvisorEvalStatusVO> my(Long paperId) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();

        AdvisorEvalStatusVO vo = new AdvisorEvalStatusVO();
        vo.setTagOptions(buildTagOptions());

        // paperId 为空取学生最新一篇论文；学生无论文时返回空状态（evaluable=false）
        PaperInfo paper;
        if (paperId == null) {
            paper = paperInfoMapper.selectOne(new LambdaQueryWrapper<PaperInfo>()
                    .eq(PaperInfo::getStudentId, currentUserId)
                    .orderByDesc(PaperInfo::getCreateTime)
                    .orderByDesc(PaperInfo::getId)
                    .last("LIMIT 1"));
            if (paper == null) {
                vo.setEvaluable(false);
                vo.setReason("您暂无已提交的论文");
                vo.setSubmitted(false);
                return Result.success(vo);
            }
        } else {
            paper = paperInfoMapper.selectById(paperId);
            if (paper == null) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除", null);
            }
            if (!Objects.equals(paper.getStudentId(), currentUserId)) {
                throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "仅可查看本人论文的评价状态", null);
            }
        }

        vo.setPaperId(paper.getId());
        vo.setPaperTitle(paper.getPaperTitle());

        // 导师姓名（realName 优先，空回落 username，与其他 VO 同口径）
        if (paper.getTeacherId() != null) {
            SysUser teacher = sysUserMapper.selectById(paper.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(StringUtils.hasText(teacher.getRealName())
                        ? teacher.getRealName() : teacher.getUsername());
            }
        }

        // 已提交回显 + 可评性判定（与 submit 同规则，前端置灰入口用）
        AdvisorEvaluation existing = advisorEvaluationMapper.selectOne(new LambdaQueryWrapper<AdvisorEvaluation>()
                .eq(AdvisorEvaluation::getPaperId, paper.getId())
                .last("LIMIT 1"));
        LocalDateTime windowEnd = resolveWindowEnd(paper.getId());
        vo.setWindowEndTime(windowEnd);

        if (existing != null) {
            vo.setSubmitted(true);
            vo.setEvaluable(false);
            vo.setReason("您已提交过导师评价");
            vo.setScoreTimeliness(existing.getScoreTimeliness());
            vo.setScoreQuality(existing.getScoreQuality());
            vo.setScoreAttitude(existing.getScoreAttitude());
            vo.setScoreProfessional(existing.getScoreProfessional());
            vo.setScoreOverall(existing.getScoreOverall());
            vo.setTags(StringUtils.hasText(existing.getTags())
                    ? JSON.parseArray(existing.getTags(), String.class) : List.of());
            vo.setComment(existing.getComment());
            vo.setSubmitTime(existing.getCreateTime());
        } else {
            vo.setSubmitted(false);
            if (!DictConstants.PaperStatus.DEFENSE_PASSED.equals(paper.getPaperStatus())
                    && !DictConstants.PaperStatus.FINALIZED.equals(paper.getPaperStatus())) {
                vo.setEvaluable(false);
                vo.setReason("论文尚未通过终期答辩，暂不可评价");
            } else if (paper.getTeacherId() == null) {
                vo.setEvaluable(false);
                vo.setReason("论文尚未分配指导教师");
            } else if (windowEnd != null && LocalDateTime.now().isAfter(windowEnd)) {
                vo.setEvaluable(false);
                vo.setReason("导师评价窗口已关闭");
            } else {
                vo.setEvaluable(true);
            }
        }

        return Result.success(vo);
    }

    /**
     * 解析评价窗口截止时间：最新一条 new_status=defense_passed 的状态日志时间 + 窗口天数。
     * 返回 null 表示不限窗口（存量论文无状态日志）。
     */
    private LocalDateTime resolveWindowEnd(Long paperId) {
        PaperStatusLog passedLog = paperStatusLogMapper.selectOne(new LambdaQueryWrapper<PaperStatusLog>()
                .eq(PaperStatusLog::getPaperId, paperId)
                .eq(PaperStatusLog::getNewStatus, DictConstants.PaperStatus.DEFENSE_PASSED)
                .orderByDesc(PaperStatusLog::getCreateTime)
                .orderByDesc(PaperStatusLog::getId)
                .last("LIMIT 1"));
        return passedLog == null ? null : passedLog.getCreateTime().plusDays(evalWindowDays);
    }

    /**
     * 字典 advisor_eval_tag → 可选标签项（label/value），免前端再查字典
     */
    private List<AdvisorEvalStatusVO.TagOption> buildTagOptions() {
        return dictService.getDictValueMap(DictConstants.DICT_ADVISOR_EVAL_TAG).entrySet().stream()
                .map(e -> {
                    AdvisorEvalStatusVO.TagOption option = new AdvisorEvalStatusVO.TagOption();
                    option.setValue(e.getKey());
                    option.setLabel(e.getValue());
                    return option;
                })
                .toList();
    }
}
