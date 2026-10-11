package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.GuidanceRecord;
import com.abin.checkrepeatservice.pojo.entity.PaperAdvisorRel;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.vo.GuidanceRecordVO;
import com.abin.checkrepeatservice.teacher.dto.GuidanceCreateReq;
import com.abin.checkrepeatservice.teacher.mapper.GuidanceRecordMapper;
import com.abin.checkrepeatservice.teacher.service.TeacherGuidanceService;
import com.abin.checkrepeatservice.user.mapper.PaperAdvisorRelMapper;
import com.abin.checkrepeatservice.user.service.Impl.NotificationFacadeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 教师端指导记录服务实现（论文全生命周期 P1）
 */
@Slf4j
@Service
public class TeacherGuidanceServiceImpl implements TeacherGuidanceService {

    @Resource
    private GuidanceRecordMapper guidanceRecordMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private PaperAdvisorRelMapper paperAdvisorRelMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private NotificationFacadeService notificationFacadeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<GuidanceRecordVO> createGuidance(GuidanceCreateReq req) {
        var currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        Long teacherId = currentUser.getId();

        // 1. 论文合法性
        PaperInfo paper = paperInfoMapper.selectById(req.getPaperId());
        if (paper == null || Objects.equals(paper.getIsDeleted(), 1)) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }

        // 2. 指导关系校验：paper_info.teacher_id 直派，或 paper_advisor_rel 有效关联（导师可能随轮次更换）
        if (!isAdvisorOf(teacherId, paper)) {
            throw new BusinessException(ResultCode.PERMISSION_TEACHER_OWNER, "仅该论文的指导教师可发起指导记录");
        }

        // 3. 轮次 = 当前最大轮次 + 1；并发场景由唯一索引 uk_paper_round 兜底
        GuidanceRecord latest = guidanceRecordMapper.selectOne(new LambdaQueryWrapper<GuidanceRecord>()
                .eq(GuidanceRecord::getPaperId, req.getPaperId())
                .orderByDesc(GuidanceRecord::getRound)
                .last("LIMIT 1"));
        int nextRound = (latest == null || latest.getRound() == null) ? 1 : latest.getRound() + 1;

        // 4. 组装落库（content 做 XSS 清洗，只留纯文本）
        GuidanceRecord record = new GuidanceRecord();
        record.setPaperId(req.getPaperId());
        record.setStudentId(paper.getStudentId());
        record.setTeacherId(teacherId);
        record.setRound(nextRound);
        record.setGuidanceType(req.getGuidanceType());
        record.setGuidanceTime(req.getGuidanceTime());
        record.setContent(Jsoup.clean(req.getContent(), Safelist.none()));
        record.setAttachmentId(req.getAttachmentId());
        record.setConfirmStatus(GuidanceRecord.CONFIRM_PENDING);
        UserBusinessInfoUtils.setAuditField(record, true);
        try {
            guidanceRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发提交撞上 uk_paper_round：轮次已被占用，按业务冲突提示重试
            throw new BusinessException(ResultCode.BUSINESS_NO_REPEAT, "同一轮次指导记录已存在，请刷新后重试");
        }

        // 5. 通知学生确认（门面内部吞异常，不影响主流程）
        notificationFacadeService.sendGuidanceCreatedNotice(record.getId(), paper.getPaperTitle(),
                paper.getStudentId(), currentUser.getRealName(), nextRound);

        log.info("指导记录发起成功 - 记录ID: {}, 论文ID: {}, 轮次: {}, 教师: {}",
                record.getId(), req.getPaperId(), nextRound, currentUser.getRealName());
        return Result.success("指导记录发起成功", toVO(record,
                Map.of(paper.getId(), paper.getPaperTitle()),
                Map.of(teacherId, currentUser.getRealName())));
    }

    @Override
    public Result<Page<GuidanceRecordVO>> listMyGuidance(Long paperId, Integer currentPage, Integer pageSize) {
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();

        Page<GuidanceRecord> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<GuidanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GuidanceRecord::getTeacherId, teacherId);
        if (paperId != null) {
            wrapper.eq(GuidanceRecord::getPaperId, paperId);
        }
        wrapper.orderByDesc(GuidanceRecord::getCreateTime);
        Page<GuidanceRecord> result = guidanceRecordMapper.selectPage(page, wrapper);

        Page<GuidanceRecordVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(enrich(result.getRecords(), teacherId));
        return Result.success("指导记录列表查询成功", voPage);
    }

    // ------------------------------ 私有辅助方法 ------------------------------

    /**
     * 当前教师是否该论文的指导教师：
     * paper_info.teacher_id 直派命中，或 paper_advisor_rel 存在有效关联（rel_status=1）
     */
    private boolean isAdvisorOf(Long teacherId, PaperInfo paper) {
        if (teacherId.equals(paper.getTeacherId())) {
            return true;
        }
        Long relCount = paperAdvisorRelMapper.selectCount(new LambdaQueryWrapper<PaperAdvisorRel>()
                .eq(PaperAdvisorRel::getPaperId, paper.getId())
                .eq(PaperAdvisorRel::getAdvisorId, teacherId)
                .eq(PaperAdvisorRel::getRelStatus, 1));
        return relCount != null && relCount > 0;
    }

    /**
     * 批量组装 VO（论文标题 + 教师姓名），避免 N+1 查询
     */
    private List<GuidanceRecordVO> enrich(List<GuidanceRecord> records, Long currentTeacherId) {
        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptyList();
        }
        List<Long> paperIds = records.stream()
                .map(GuidanceRecord::getPaperId).distinct().toList();
        Map<Long, String> paperTitles = paperInfoMapper.selectBatchIds(paperIds).stream()
                .collect(Collectors.toMap(PaperInfo::getId, PaperInfo::getPaperTitle));
        String teacherName = sysUserMapper.selectById(currentTeacherId).getRealName();
        Map<Long, String> teacherNames = Map.of(currentTeacherId, teacherName);

        return records.stream()
                .map(record -> toVO(record, paperTitles, teacherNames))
                .collect(Collectors.toList());
    }

    /**
     * 实体转 VO；确认状态描述集中在这里维护，避免前端各写一套
     */
    private GuidanceRecordVO toVO(GuidanceRecord record, Map<Long, String> paperTitles,
                                  Map<Long, String> teacherNames) {
        GuidanceRecordVO vo = new GuidanceRecordVO();
        vo.setId(record.getId());
        vo.setPaperId(record.getPaperId());
        vo.setPaperTitle(paperTitles.getOrDefault(record.getPaperId(), ""));
        vo.setTeacherId(record.getTeacherId());
        vo.setTeacherName(teacherNames.getOrDefault(record.getTeacherId(), ""));
        vo.setRound(record.getRound());
        vo.setGuidanceType(record.getGuidanceType());
        vo.setGuidanceTime(record.getGuidanceTime());
        vo.setContent(record.getContent());
        vo.setStudentFeedback(record.getStudentFeedback());
        vo.setAttachmentId(record.getAttachmentId());
        vo.setConfirmStatus(record.getConfirmStatus());
        vo.setConfirmStatusDesc(confirmStatusDesc(record.getConfirmStatus()));
        vo.setConfirmTime(record.getConfirmTime());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private String confirmStatusDesc(Integer confirmStatus) {
        if (confirmStatus == null) {
            return "";
        }
        return switch (confirmStatus) {
            case GuidanceRecord.CONFIRM_PENDING -> "待学生确认";
            case GuidanceRecord.CONFIRMED -> "已确认";
            case GuidanceRecord.DISPUTED -> "有异议";
            default -> "未知状态";
        };
    }
}
