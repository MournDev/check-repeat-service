package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.DefenseBatchMapper;
import com.abin.checkrepeatservice.admin.mapper.DefenseGroupMemberMapper;
import com.abin.checkrepeatservice.admin.mapper.DefenseRecordMapper;
import com.abin.checkrepeatservice.admin.vo.DefenseBatchVO;
import com.abin.checkrepeatservice.admin.vo.DefenseGroupMemberVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.component.PaperStageGate;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.ScoreComposeService;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.DefenseBatch;
import com.abin.checkrepeatservice.pojo.entity.DefenseGroupMember;
import com.abin.checkrepeatservice.pojo.entity.DefenseRecord;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.dto.DefenseScoreReq;
import com.abin.checkrepeatservice.teacher.service.TeacherDefenseService;
import com.abin.checkrepeatservice.teacher.vo.DefenseRecordVO;
import com.abin.checkrepeatservice.user.service.PaperStatusLogService;
import com.abin.checkrepeatservice.user.service.Impl.NotificationFacadeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 教师端答辩工作台服务实现（论文全生命周期 P3）。
 *
 * 关键规则：
 *  - 教师作为答辩组 leader/member 或批次 secretary 都可访问批次会议信息与记录列表；
 *  - 评分录入（结论提交）仅组长 leader 可执行，成员与秘书不可代录结论；
 *  - 终期批次（batch_type=final）result=pass 时：
 *      a) 回写 paper_info.final_score（取 record.score）；
 *      b) 经 PaperStageGate 推进 final_defense → defense_passed；
 *      c) 记录状态流转日志；
 *  - result=fail 退回修改：经阶段门 final_defense → rejected（学生重新提交，重走阶段门）；
 *  - result=postpone 缓辩：状态不推进，择期重排批次即可（论文保持在 mid_defense/final_defense）；
 *  - 批次所有记录提交结论后由 Service 自动推进批次状态 finished（基于 submittedCount==paperCount 判定）。
 */
@Slf4j
@Service
public class TeacherDefenseServiceImpl implements TeacherDefenseService {

    @Resource
    private DefenseBatchMapper defenseBatchMapper;

    @Resource
    private DefenseGroupMemberMapper defenseGroupMemberMapper;

    @Resource
    private DefenseRecordMapper defenseRecordMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private PaperStageGate paperStageGate;

    @Resource
    private PaperStatusLogService paperStatusLogService;

    @Resource
    private NotificationFacadeService notificationFacadeService;

    /**
     * 总评成绩加权合成（论文全生命周期 P5）：终期 pass 回写 final_score 时调用。
     * 答辩现场分（req.score）从"直接回写"降级为合成中的"答辩分"数据源。
     */
    @Resource
    private ScoreComposeService scoreComposeService;

    @Override
    public Result<List<DefenseBatchVO>> listMyBatches() {
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();

        // 我作为组员的批次（组长/成员）
        List<Long> myBatchIds = defenseGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<DefenseGroupMember>()
                                .eq(DefenseGroupMember::getTeacherId, teacherId))
                .stream().map(DefenseGroupMember::getBatchId).distinct().toList();

        // 我作为秘书的批次
        List<Long> secretaryBatchIds = defenseBatchMapper.selectList(
                        new LambdaQueryWrapper<DefenseBatch>()
                                .eq(DefenseBatch::getSecretaryId, teacherId))
                .stream().map(DefenseBatch::getId).toList();

        // 合并去重
        List<Long> allBatchIds = new java.util.ArrayList<>();
        java.util.Collections.addAll(allBatchIds, myBatchIds.toArray(new Long[0]));
        for (Long bid : secretaryBatchIds) {
            if (!allBatchIds.contains(bid)) allBatchIds.add(bid);
        }
        if (allBatchIds.isEmpty()) {
            return Result.success("我的答辩批次列表查询成功", List.of());
        }

        List<DefenseBatch> batches = defenseBatchMapper.selectBatchIds(allBatchIds).stream()
                .sorted((a, b) -> {
                    int c = b.getDefenseDate() == null || a.getDefenseDate() == null ? 0
                            : b.getDefenseDate().compareTo(a.getDefenseDate());
                    return c != 0 ? c : (b.getCreateTime() == null || a.getCreateTime() == null ? 0
                            : b.getCreateTime().compareTo(a.getCreateTime()));
                }).toList();

        return Result.success("我的答辩批次列表查询成功", toBatchVOList(batches));
    }

    @Override
    public Result<DefenseBatchVO> getBatchDetail(Long batchId) {
        DefenseBatch batch = mustGetBatchAccessible(batchId);
        return Result.success("答辩批次会议信息查询成功", toBatchVO(batch, true));
    }

    @Override
    public Result<List<DefenseGroupMemberVO>> listBatchMembers(Long batchId) {
        // 复用组成员/秘书可见性校验：名单含全部组员，比会议信息更敏感，权限口径保持一致
        mustGetBatchAccessible(batchId);

        List<DefenseGroupMember> members = defenseGroupMemberMapper.selectList(
                new LambdaQueryWrapper<DefenseGroupMember>()
                        .eq(DefenseGroupMember::getBatchId, batchId)
                        .orderByAsc(DefenseGroupMember::getCreateTime));
        if (members.isEmpty()) {
            return Result.success("答辩组成员名单查询成功", List.of());
        }

        List<Long> teacherIds = members.stream()
                .map(DefenseGroupMember::getTeacherId).filter(Objects::nonNull).distinct().toList();
        Map<Long, SysUser> teacherMap = teacherIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(teacherIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        // 组长在前，成员在后；同角色按加入时间升序
        List<DefenseGroupMemberVO> voList = members.stream()
                .sorted((a, b) -> {
                    boolean aLeader = DefenseGroupMember.ROLE_LEADER.equals(a.getGroupRole());
                    boolean bLeader = DefenseGroupMember.ROLE_LEADER.equals(b.getGroupRole());
                    if (aLeader != bLeader) {
                        return aLeader ? -1 : 1;
                    }
                    if (a.getCreateTime() != null && b.getCreateTime() != null) {
                        return a.getCreateTime().compareTo(b.getCreateTime());
                    }
                    return 0;
                })
                .map(m -> {
                    SysUser teacher = m.getTeacherId() == null ? null : teacherMap.get(m.getTeacherId());
                    DefenseGroupMemberVO vo = new DefenseGroupMemberVO();
                    vo.setId(m.getId());
                    vo.setBatchId(m.getBatchId());
                    vo.setTeacherId(m.getTeacherId());
                    vo.setTeacherName(teacher == null ? null
                            : (StringUtils.hasText(teacher.getRealName()) ? teacher.getRealName() : teacher.getUsername()));
                    vo.setTeacherUsername(teacher == null ? null : teacher.getUsername());
                    vo.setGroupRole(m.getGroupRole());
                    vo.setGroupRoleDesc(DefenseGroupMember.ROLE_LEADER.equals(m.getGroupRole()) ? "组长" : "成员");
                    vo.setCreateTime(m.getCreateTime());
                    return vo;
                }).toList();
        return Result.success("答辩组成员名单查询成功", voList);
    }

    @Override
    public Result<List<DefenseRecordVO>> listBatchRecords(Long batchId, String result) {
        DefenseBatch batch = mustGetBatchAccessible(batchId);

        LambdaQueryWrapper<DefenseRecord> wrapper = new LambdaQueryWrapper<DefenseRecord>()
                .eq(DefenseRecord::getBatchId, batchId)
                .orderByAsc(DefenseRecord::getDefenseOrder)
                .orderByAsc(DefenseRecord::getCreateTime);
        if (StringUtils.hasText(result)) {
            wrapper.eq(DefenseRecord::getResult, result);
        }
        List<DefenseRecord> records = defenseRecordMapper.selectList(wrapper);
        return Result.success("批次内答辩记录列表查询成功", toRecordVOList(records, batch));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<DefenseRecordVO> submitScore(DefenseScoreReq req) {
        Long currentTeacherId = UserBusinessInfoUtils.getCurrentUserId();

        DefenseRecord record = defenseRecordMapper.selectById(req.getRecordId());
        if (record == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "答辩记录不存在或已删除");
        }
        DefenseBatch batch = defenseBatchMapper.selectById(record.getBatchId());
        if (batch == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "答辩批次不存在或已删除");
        }
        if (Integer.valueOf(DefenseBatch.STATUS_FINISHED).equals(batch.getStatus())) {
            throw new BusinessException(ResultCode.PERMISSION_NOT_STATUS, "批次已结束，不可再提交结论");
        }

        // 仅组长可提交结论；成员与秘书不可代录
        if (!Objects.equals(batch.getLeaderId(), currentTeacherId)) {
            throw new BusinessException(ResultCode.PERMISSION_TEACHER_OWNER, "仅答辩组长可提交结论");
        }

        // 重复提交校验
        if (StringUtils.hasText(record.getResult())) {
            throw new BusinessException(ResultCode.BUSINESS_NO_REPEAT, "该论文答辩结论已提交，请勿重复提交");
        }

        // result=pass 必须填 score 与 grade（用于回写 final_score 与归档）
        if (DefenseRecord.RESULT_PASS.equals(req.getResult())) {
            if (req.getScore() == null || !StringUtils.hasText(req.getGrade())) {
                throw new BusinessException(ResultCode.PARAM_EMPTY, "结论为通过时必须填写得分与等级");
            }
        }

        // 落库：清洗 comment（纯文本，与指导记录/评阅意见口径一致）
        DefenseRecord update = new DefenseRecord();
        update.setId(record.getId());
        update.setScore(req.getScore());
        update.setGrade(req.getGrade());
        update.setResult(req.getResult());
        update.setComment(StringUtils.hasText(req.getComment())
                ? Jsoup.clean(req.getComment(), Safelist.none()) : null);
        update.setAttachmentId(req.getAttachmentId());
        update.setSubmitBy(currentTeacherId);
        update.setSubmitTime(LocalDateTime.now());
        defenseRecordMapper.updateById(update);

        // 终期批次 pass：回写 final_score + 阶段门推进 final_defense → defense_passed
        // 终期 fail：阶段门推进 final_defense → rejected（退回修改，学生重新提交）
        // 中期批次 pass：阶段门推进 mid_defense → final_defense（进入终期答辩门槛，等管理员安排终期批次）
        // 中期 fail：阶段门推进 mid_defense → rejected
        // postpone：状态不推进，论文保持原状态，择期重排批次即可
        // P5：合成成功的总评成绩暂存于此，用于阶段门推进后补发成绩通知
        BigDecimal synthesizedScore = null;
        PaperInfo paper = paperInfoMapper.selectById(record.getPaperId());
        if (paper != null) {
            String currentStatus = paper.getPaperStatus();
            boolean isFinal = DefenseBatch.TYPE_FINAL.equals(batch.getBatchType());
            boolean isMid = DefenseBatch.TYPE_MID_TERM.equals(batch.getBatchType());

            String targetStatus = null;
            String reason = null;
            if (DefenseRecord.RESULT_PASS.equals(req.getResult())) {
                if (isFinal) {
                    targetStatus = DictConstants.PaperStatus.DEFENSE_PASSED;
                    reason = "终期答辩通过，结论由组长提交";
                } else if (isMid) {
                    targetStatus = DictConstants.PaperStatus.FINAL_DEFENSE;
                    reason = "中期答辩通过，进入终期答辩门槛";
                }
            } else if (DefenseRecord.RESULT_FAIL.equals(req.getResult())) {
                targetStatus = DictConstants.PaperStatus.REJECTED;
                reason = (isFinal ? "终期" : "中期") + "答辩不通过，退回修改";
            }
            // postpone：targetStatus 保持 null，不推进

            if (targetStatus != null) {
                paperStageGate.assertTransition(currentStatus, targetStatus);
                PaperInfo updatePaper = new PaperInfo();
                updatePaper.setId(paper.getId());
                updatePaper.setPaperStatus(targetStatus);
                // 终期通过时回写 final_score：P5 起改为加权合成
                //（指导分×w1% + 评阅分×w2% + 答辩分×w3%，权重来自 system_param 三列，
                // 组长录入的 req.score 只作为其中"答辩分"数据源，不再直接回写）。
                // 阶段门推进逻辑一字未动——合成失败（缺指导分 40914 / 权重非法 40915 /
                // 任何意外）只吞掉并记日志，final_score 留空照样放行 defense_passed；
                // 存量已答辩数据不回刷，失败论文由管理端
                // POST /api/admin/thesis/{paperId}/recompute-score 重算兜底。
                if (isFinal && DefenseRecord.RESULT_PASS.equals(req.getResult())) {
                    try {
                        synthesizedScore = scoreComposeService.compose(paper.getId());
                        updatePaper.setFinalScore(synthesizedScore);
                    } catch (Exception e) {
                        log.error("总评成绩合成失败，不阻断阶段门，paperId={}", paper.getId(), e);
                    }
                }
                paperInfoMapper.updateById(updatePaper);
                paperStatusLogService.recordStatusLog(paper.getId(), currentStatus, targetStatus,
                        reason + "（批次：" + batch.getBatchNo() + "）",
                        currentTeacherId, null);
            }
        }

        // 批次状态推进：所有记录都已提交结论 → 批次置为已结束
        long total = defenseRecordMapper.selectCount(new LambdaQueryWrapper<DefenseRecord>()
                .eq(DefenseRecord::getBatchId, batch.getId()));
        long submitted = defenseRecordMapper.selectCount(new LambdaQueryWrapper<DefenseRecord>()
                .eq(DefenseRecord::getBatchId, batch.getId())
                .isNotNull(DefenseRecord::getResult));
        if (total > 0 && submitted >= total
                && !Integer.valueOf(DefenseBatch.STATUS_FINISHED).equals(batch.getStatus())) {
            DefenseBatch updateBatch = new DefenseBatch();
            updateBatch.setId(batch.getId());
            updateBatch.setStatus(DefenseBatch.STATUS_FINISHED);
            defenseBatchMapper.updateById(updateBatch);
            log.info("答辩批次已全部提交结论，置为已结束：batchId={}，total={}，submitted={}",
                    batch.getId(), total, submitted);
        }

        // 通知学生答辩结论已出（门面吞异常）
        if (paper != null && paper.getStudentId() != null) {
            notificationFacadeService.sendDefenseResultNotice(paper.getId(),
                    paper.getPaperTitle(), paper.getStudentId(),
                    batch.getBatchNo(), req.getResult(),
                    req.getScore() == null ? null : req.getScore().toPlainString(),
                    req.getGrade());
        }

        // P5：加权合成成功时额外通知学生总评成绩已出（门面吞异常，不影响主流程）；
        // 合成失败的论文不发本通知，避免给学生"成绩已出"的误导
        if (paper != null && paper.getStudentId() != null && synthesizedScore != null) {
            notificationFacadeService.sendScoreSynthesizedNotice(paper.getId(),
                    paper.getPaperTitle(), paper.getStudentId(), synthesizedScore.toPlainString());
        }

        log.info("答辩结论提交成功：recordId={}，batchId={}，paperId={}，result={}，operatorId={}",
                record.getId(), batch.getId(), record.getPaperId(), req.getResult(), currentTeacherId);

        // 回读最新值返回
        DefenseRecord latest = defenseRecordMapper.selectById(record.getId());
        return Result.success("答辩结论提交成功", toRecordVOList(List.of(latest), batch).get(0));
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 取批次并校验当前教师可访问：必须是组成员或秘书。
     */
    private DefenseBatch mustGetBatchAccessible(Long batchId) {
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        DefenseBatch batch = defenseBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "答辩批次不存在或已删除");
        }
        boolean isMember = defenseGroupMemberMapper.selectCount(
                new LambdaQueryWrapper<DefenseGroupMember>()
                        .eq(DefenseGroupMember::getBatchId, batchId)
                        .eq(DefenseGroupMember::getTeacherId, teacherId)) > 0;
        boolean isSecretary = Objects.equals(batch.getSecretaryId(), teacherId);
        if (!isMember && !isSecretary) {
            throw new BusinessException(ResultCode.PERMISSION_TEACHER_OWNER, "仅本批次答辩组成员或秘书可查看");
        }
        return batch;
    }

    private List<DefenseBatchVO> toBatchVOList(List<DefenseBatch> batches) {
        if (batches.isEmpty()) return List.of();
        return batches.stream().map(b -> toBatchVO(b, false)).toList();
    }

    private DefenseBatchVO toBatchVO(DefenseBatch batch, boolean withMembers) {
        DefenseBatchVO vo = new DefenseBatchVO();
        vo.setId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setBatchType(batch.getBatchType());
        vo.setBatchTypeDesc(batchTypeDesc(batch.getBatchType()));
        vo.setCollegeId(batch.getCollegeId());
        vo.setDefenseDate(batch.getDefenseDate());
        vo.setLocation(batch.getLocation());
        vo.setMode(batch.getMode());
        vo.setModeDesc(DefenseBatch.MODE_ONLINE.equals(batch.getMode()) ? "线上答辩" : "线下答辩");
        vo.setLeaderId(batch.getLeaderId());
        vo.setSecretaryId(batch.getSecretaryId());
        vo.setStatus(batch.getStatus());
        vo.setStatusDesc(batchStatusDesc(batch.getStatus()));
        vo.setRemark(batch.getRemark());
        vo.setCreateTime(batch.getCreateTime());

        // 组长/秘书姓名
        List<Long> userIds = java.util.stream.Stream.of(batch.getLeaderId(), batch.getSecretaryId())
                .filter(Objects::nonNull).distinct().toList();
        if (!userIds.isEmpty()) {
            Map<Long, String> userNameMap = sysUserMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(SysUser::getId,
                            u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));
            vo.setLeaderName(batch.getLeaderId() == null ? null : userNameMap.get(batch.getLeaderId()));
            vo.setSecretaryName(batch.getSecretaryId() == null ? null : userNameMap.get(batch.getSecretaryId()));
        }

        // 入册论文数 + 已提交结论数（教师端可见，便于组长判断是否全部完成）
        long paperCount = defenseRecordMapper.selectCount(new LambdaQueryWrapper<DefenseRecord>()
                .eq(DefenseRecord::getBatchId, batch.getId()));
        long submittedCount = defenseRecordMapper.selectCount(new LambdaQueryWrapper<DefenseRecord>()
                .eq(DefenseRecord::getBatchId, batch.getId())
                .isNotNull(DefenseRecord::getResult));
        vo.setPaperCount((int) paperCount);
        vo.setSubmittedCount((int) submittedCount);

        // 详情时返回组员人数（完整名单走 GET /batch/{id}/members 独立接口，便于前端按需加载）
        if (withMembers) {
            long memberCount = defenseGroupMemberMapper.selectCount(
                    new LambdaQueryWrapper<DefenseGroupMember>()
                            .eq(DefenseGroupMember::getBatchId, batch.getId()));
            vo.setMemberCount((int) memberCount);
        }
        return vo;
    }

    private List<DefenseRecordVO> toRecordVOList(List<DefenseRecord> records, DefenseBatch batch) {
        if (records.isEmpty()) return List.of();

        List<Long> paperIds = records.stream().map(DefenseRecord::getPaperId).distinct().toList();
        Map<Long, PaperInfo> paperMap = paperIds.isEmpty() ? Map.of()
                : paperInfoMapper.selectBatchIds(paperIds).stream()
                        .collect(Collectors.toMap(PaperInfo::getId, Function.identity()));

        List<Long> studentIds = records.stream().map(DefenseRecord::getStudentId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, SysUser> studentMap = studentIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        List<Long> submitByIds = records.stream().map(DefenseRecord::getSubmitBy)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> submitByNameMap = submitByIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(submitByIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));

        String batchNo = batch == null ? null : batch.getBatchNo();
        return records.stream().map(r -> {
            PaperInfo paper = paperMap.get(r.getPaperId());
            SysUser student = r.getStudentId() == null ? null : studentMap.get(r.getStudentId());
            DefenseRecordVO vo = new DefenseRecordVO();
            vo.setId(r.getId());
            vo.setBatchId(r.getBatchId());
            vo.setBatchNo(batchNo);
            vo.setPaperId(r.getPaperId());
            vo.setPaperTitle(paper == null ? null : paper.getPaperTitle());
            vo.setStudentId(r.getStudentId());
            vo.setStudentName(student == null ? null
                    : (StringUtils.hasText(student.getRealName()) ? student.getRealName() : student.getUsername()));
            vo.setStudentUsername(student == null ? null : student.getUsername());
            vo.setDefenseOrder(r.getDefenseOrder());
            vo.setScore(r.getScore());
            vo.setGrade(r.getGrade());
            vo.setResult(r.getResult());
            vo.setResultDesc(resultDesc(r.getResult()));
            vo.setComment(r.getComment());
            vo.setAttachmentId(r.getAttachmentId());
            vo.setSubmitBy(r.getSubmitBy());
            vo.setSubmitByName(r.getSubmitBy() == null ? null : submitByNameMap.get(r.getSubmitBy()));
            vo.setSubmitTime(r.getSubmitTime());
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).toList();
    }

    private String batchTypeDesc(String batchType) {
        if (batchType == null) return null;
        return switch (batchType) {
            case DefenseBatch.TYPE_MID_TERM -> "中期答辩";
            case DefenseBatch.TYPE_FINAL -> "终期答辩";
            default -> batchType;
        };
    }

    private String batchStatusDesc(Integer status) {
        if (status == null) return null;
        return switch (status) {
            case DefenseBatch.STATUS_PREPARING -> "筹备中";
            case DefenseBatch.STATUS_IN_PROGRESS -> "进行中";
            case DefenseBatch.STATUS_FINISHED -> "已结束";
            default -> String.valueOf(status);
        };
    }

    private String resultDesc(String result) {
        if (result == null) return null;
        return switch (result) {
            case DefenseRecord.RESULT_PASS -> "通过";
            case DefenseRecord.RESULT_FAIL -> "不通过";
            case DefenseRecord.RESULT_POSTPONE -> "缓辩";
            default -> result;
        };
    }
}
