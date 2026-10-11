package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.student.dto.GuidanceConfirmReq;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.GuidanceRecord;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.StudentGuidanceService;
import com.abin.checkrepeatservice.student.vo.GuidanceRecordVO;
import com.abin.checkrepeatservice.teacher.mapper.GuidanceRecordMapper;
import com.abin.checkrepeatservice.user.service.Impl.NotificationFacadeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 学生端指导记录服务实现（论文全生命周期 P1）
 */
@Slf4j
@Service
public class StudentGuidanceServiceImpl implements StudentGuidanceService {

    @Resource
    private GuidanceRecordMapper guidanceRecordMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private NotificationFacadeService notificationFacadeService;

    @Override
    public Result<Page<GuidanceRecordVO>> listMyGuidance(Integer confirmStatus, Integer currentPage, Integer pageSize) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();

        Page<GuidanceRecord> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<GuidanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GuidanceRecord::getStudentId, studentId);
        if (confirmStatus != null) {
            wrapper.eq(GuidanceRecord::getConfirmStatus, confirmStatus);
        }
        wrapper.orderByDesc(GuidanceRecord::getGuidanceTime);
        Page<GuidanceRecord> result = guidanceRecordMapper.selectPage(page, wrapper);

        Page<GuidanceRecordVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(enrich(result.getRecords()));
        return Result.success("指导记录列表查询成功", voPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> confirmRecord(Long recordId, GuidanceConfirmReq req) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();

        // 1. 记录存在性 + 本人校验
        GuidanceRecord record = guidanceRecordMapper.selectById(recordId);
        if (record == null || Objects.equals(record.getIsDeleted(), 1)) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "指导记录不存在或已删除");
        }
        if (!studentId.equals(record.getStudentId())) {
            throw new BusinessException(ResultCode.PERMISSION_STUDENT_OWNER, "仅学生本人可确认自己的指导记录");
        }

        // 2. 仅待确认状态可确认，确认后固化（防止重复确认覆盖反馈）
        if (record.getConfirmStatus() == null || record.getConfirmStatus() != GuidanceRecord.CONFIRM_PENDING) {
            throw new BusinessException(ResultCode.BUSINESS_NO_REPEAT, "该指导记录已确认，请勿重复操作");
        }

        // 3. 固化确认结果（反馈做 XSS 清洗，只留纯文本）
        GuidanceRecord update = new GuidanceRecord();
        update.setId(recordId);
        update.setConfirmStatus(GuidanceRecord.CONFIRMED);
        update.setConfirmTime(LocalDateTime.now());
        if (StringUtils.hasText(req.getFeedback())) {
            update.setStudentFeedback(Jsoup.clean(req.getFeedback(), Safelist.none()));
        }
        UserBusinessInfoUtils.setAuditField(update, false);
        guidanceRecordMapper.updateById(update);

        // 4. 通知指导教师（门面内部吞异常，不影响主流程）
        PaperInfo paper = paperInfoMapper.selectById(record.getPaperId());
        String paperTitle = paper == null ? "" : paper.getPaperTitle();
        SysUser student = sysUserMapper.selectById(studentId);
        String studentName = student == null ? "" : student.getRealName();
        notificationFacadeService.sendGuidanceConfirmedNotice(record.getPaperId(), paperTitle,
                record.getTeacherId(), studentName, record.getRound());

        log.info("指导记录确认成功 - 记录ID: {}, 学生ID: {}, 轮次: {}", recordId, studentId, record.getRound());
        return Result.success("指导记录确认成功", null);
    }

    // ------------------------------ 私有辅助方法 ------------------------------

    /**
     * 批量组装 VO（论文标题 + 教师姓名），避免 N+1 查询
     */
    private List<GuidanceRecordVO> enrich(List<GuidanceRecord> records) {
        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptyList();
        }
        List<Long> paperIds = records.stream()
                .map(GuidanceRecord::getPaperId).distinct().toList();
        Map<Long, String> paperTitles = paperInfoMapper.selectBatchIds(paperIds).stream()
                .collect(Collectors.toMap(PaperInfo::getId, PaperInfo::getPaperTitle));

        List<Long> teacherIds = records.stream()
                .map(GuidanceRecord::getTeacherId).distinct().toList();
        Map<Long, String> teacherNames = sysUserMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));

        return records.stream()
                .map(record -> {
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
                })
                .collect(Collectors.toList());
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
