package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.PaperSubmitMapper;
import com.abin.checkrepeatservice.admin.mapper.ThesisArchiveMapper;
import com.abin.checkrepeatservice.admin.service.ThesisArchiveService;
import com.abin.checkrepeatservice.admin.vo.ThesisArchiveVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.component.PaperStageGate;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.mapper.CollegeMapper;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.College;
import com.abin.checkrepeatservice.pojo.entity.Major;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperSubmit;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.pojo.entity.ThesisArchive;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.MajorMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.user.service.PaperStatusLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 论文定稿与归档服务实现（P4）。
 */
@Slf4j
@Service
public class ThesisArchiveServiceImpl implements ThesisArchiveService {

    private static final DateTimeFormatter ARCHIVE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private ThesisArchiveMapper thesisArchiveMapper;

    @Resource
    private PaperSubmitMapper paperSubmitMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private CollegeMapper collegeMapper;

    @Resource
    private MajorMapper majorMapper;

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private CheckReportMapper checkReportMapper;

    @Resource
    private PaperStageGate paperStageGate;

    @Resource
    private PaperStatusLogService paperStatusLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> finalizePaper(Long paperId) {
        Long operatorId = UserBusinessInfoUtils.getCurrentUserId();
        PaperInfo paper = paperInfoMapper.selectById(paperId);
        if (paper == null || Integer.valueOf(1).equals(paper.getIsDeleted())) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }
        // 定稿是指导教师对本人指导论文的确认动作；管理员可用于兜底
        if (!UserBusinessInfoUtils.isAdmin() && !Objects.equals(operatorId, paper.getTeacherId())) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "仅该论文的指导教师可执行定稿确认");
        }

        String fromStatus = paperStageGate.normalizeLegacy(paper.getPaperStatus());
        if (!DictConstants.PaperStatus.DEFENSE_PASSED.equals(fromStatus)) {
            return Result.error(ResultCode.PERMISSION_NOT_STATUS,
                    "仅终期答辩通过的论文可定稿，当前状态：" + fromStatus);
        }
        try {
            paperStageGate.assertTransition(fromStatus, DictConstants.PaperStatus.FINALIZED);
        } catch (BusinessException e) {
            return Result.error(e.getResultCode(), e.getMessage());
        }

        PaperInfo update = new PaperInfo();
        update.setId(paperId);
        update.setPaperStatus(DictConstants.PaperStatus.FINALIZED);
        update.setUpdateTime(LocalDateTime.now());
        paperInfoMapper.updateById(update);

        paperStatusLogService.recordStatusLog(paperId, fromStatus,
                DictConstants.PaperStatus.FINALIZED, "指导教师定稿确认", operatorId, null);
        log.info("论文定稿成功：paperId={}, operatorId={}", paperId, operatorId);
        return Result.success("论文定稿成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<ThesisArchiveVO> archivePaper(Long paperId, String remark) {
        Long operatorId = UserBusinessInfoUtils.getCurrentUserId();
        PaperInfo paper = paperInfoMapper.selectById(paperId);
        if (paper == null || Integer.valueOf(1).equals(paper.getIsDeleted())) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }

        String fromStatus = paperStageGate.normalizeLegacy(paper.getPaperStatus());
        if (!DictConstants.PaperStatus.FINALIZED.equals(fromStatus)) {
            return Result.error(ResultCode.PERMISSION_NOT_STATUS,
                    "仅已定稿的论文可归档，当前状态：" + fromStatus);
        }
        try {
            paperStageGate.assertTransition(fromStatus, DictConstants.PaperStatus.ARCHIVED);
        } catch (BusinessException e) {
            return Result.error(e.getResultCode(), e.getMessage());
        }

        // 归档 = 比对库正式准入节点：正文必须先入库，否则宁可阻断也不放半成品进比对库
        if (!StringUtils.hasText(paper.getContentPath())) {
            return Result.error(ResultCode.PERMISSION_NOT_STATUS,
                    "论文正文尚未入库，无法归档（请先完成一次查重）");
        }

        int paperVersion = paper.getVersion() == null ? 1 : paper.getVersion();
        Long archived = thesisArchiveMapper.selectCount(new LambdaQueryWrapper<ThesisArchive>()
                .eq(ThesisArchive::getPaperId, paperId)
                .eq(ThesisArchive::getPaperVersion, paperVersion));
        if (archived != null && archived > 0) {
            return Result.error(ResultCode.BUSINESS_NO_SAFE,
                    "该论文版本（v" + paperVersion + "）已归档，请勿重复操作");
        }

        ThesisArchive archive = buildArchiveSnapshot(paper, paperVersion, operatorId, remark);
        try {
            thesisArchiveMapper.insert(archive);
        } catch (DuplicateKeyException e) {
            // 并发归档撞 uk_ta_paper_version / uk_ta_archive_no
            return Result.error(ResultCode.BUSINESS_NO_SAFE, "该论文版本已归档，请勿重复操作");
        }

        // 论文进入归档终态，并正式准入比对库
        PaperInfo update = new PaperInfo();
        update.setId(paperId);
        update.setPaperStatus(DictConstants.PaperStatus.ARCHIVED);
        update.setLibraryAdmitted(1);
        update.setUpdateTime(LocalDateTime.now());
        paperInfoMapper.updateById(update);

        paperStatusLogService.recordStatusLog(paperId, fromStatus,
                DictConstants.PaperStatus.ARCHIVED,
                "论文归档完成，归档编号：" + archive.getArchiveNo(), operatorId, null);
        log.info("论文归档成功：paperId={}, archiveNo={}, operatorId={}",
                paperId, archive.getArchiveNo(), operatorId);
        return Result.success("论文归档成功", toVO(archive));
    }

    @Override
    public Result<Page<ThesisArchiveVO>> listArchives(String keyword, Long collegeId, Long majorId,
                                                      Integer currentPage, Integer pageSize) {
        LambdaQueryWrapper<ThesisArchive> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ThesisArchive::getPaperTitle, kw)
                    .or().like(ThesisArchive::getStudentName, kw)
                    .or().like(ThesisArchive::getStudentNo, kw)
                    .or().like(ThesisArchive::getArchiveNo, kw)
                    .or().like(ThesisArchive::getTeacherName, kw));
        }
        if (collegeId != null) {
            wrapper.eq(ThesisArchive::getCollegeId, collegeId);
        }
        if (majorId != null) {
            wrapper.eq(ThesisArchive::getMajorId, majorId);
        }
        wrapper.orderByDesc(ThesisArchive::getArchiveTime);

        Page<ThesisArchive> page = thesisArchiveMapper.selectPage(
                new Page<>(currentPage == null ? 1 : currentPage, pageSize == null ? 10 : pageSize),
                wrapper);
        Page<ThesisArchiveVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return Result.success(voPage);
    }

    @Override
    public Result<ThesisArchiveVO> getArchive(Long id) {
        ThesisArchive archive = thesisArchiveMapper.selectById(id);
        if (archive == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "归档记录不存在");
        }
        return Result.success(toVO(archive));
    }

    /**
     * 构建归档证据快照：把论文、学生、导师、学院专业、最近一次成功查重的报告要素冻结下来。
     * 快照存的是归档这一刻的事实，后续论文/用户资料变更不影响已归档证据。
     */
    private ThesisArchive buildArchiveSnapshot(PaperInfo paper, int paperVersion,
                                               Long operatorId, String remark) {
        ThesisArchive archive = new ThesisArchive();
        archive.setArchiveNo("ARC" + LocalDateTime.now().format(ARCHIVE_DATE_FORMATTER)
                + com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr());
        archive.setPaperId(paper.getId());
        archive.setPaperVersion(paperVersion);
        archive.setSubmitId(resolveLatestSubmitId(paper.getId()));
        archive.setPaperTitle(paper.getPaperTitle());
        archive.setAuthor(paper.getAuthor());
        archive.setStudentId(paper.getStudentId());
        archive.setTeacherId(paper.getTeacherId());
        archive.setTeacherName(paper.getTeacherName());
        archive.setCollegeId(paper.getCollegeId());
        archive.setMajorId(paper.getMajorId());
        archive.setDegreeType(paper.getDegreeType());
        archive.setFileId(paper.getFileId());
        archive.setFilePath(paper.getFilePath());
        archive.setFileMd5(paper.getFileMd5());
        archive.setCheckSource(paper.getCheckSource());
        archive.setArchiveTime(LocalDateTime.now());
        archive.setArchiveBy(operatorId);
        archive.setArchiveRemark(remark == null ? "" : remark);

        // 学生/学院/专业名称快照（被删除的资料留空，不阻断归档）
        if (paper.getStudentId() != null) {
            SysUser student = sysUserMapper.selectById(paper.getStudentId());
            if (student != null) {
                archive.setStudentNo(student.getUsername());
                archive.setStudentName(student.getRealName());
            }
        }
        if (paper.getCollegeId() != null) {
            College college = collegeMapper.selectById(paper.getCollegeId());
            if (college != null) {
                archive.setCollegeName(college.getCollegeName());
            }
        }
        if (paper.getMajorId() != null) {
            Major major = majorMapper.selectById(paper.getMajorId());
            if (major != null) {
                archive.setMajorName(major.getMajorName());
            }
        }

        // 最近一次成功查重作为学位授予的查重证据
        CheckTask latestTask = checkTaskMapper.selectOne(new LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getPaperId, paper.getId())
                .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                .eq(CheckTask::getIsDeleted, 0)
                .orderByDesc(CheckTask::getEndTime)
                .last("LIMIT 1"));
        if (latestTask != null) {
            archive.setCheckTaskId(latestTask.getId());
            archive.setCheckRate(latestTask.getCheckRate());
            archive.setMaxPerSource(latestTask.getMaxPerSource());
            archive.setCitationRate(latestTask.getCitationRate());
            archive.setCheckReportId(latestTask.getReportId());
            if (latestTask.getReportId() != null) {
                CheckReport report = checkReportMapper.selectById(latestTask.getReportId());
                if (report != null) {
                    archive.setReportNo(report.getReportNo());
                    archive.setReportHash(report.getContentHash());
                }
            }
        }

        UserBusinessInfoUtils.setAuditField(archive, true);
        return archive;
    }

    /**
     * 取该论文最新一次提交记录 ID（与 M3.1 任务绑定口径一致）
     */
    private Long resolveLatestSubmitId(Long paperId) {
        PaperSubmit latest = paperSubmitMapper.selectOne(new LambdaQueryWrapper<PaperSubmit>()
                .eq(PaperSubmit::getPaperId, paperId)
                .orderByDesc(PaperSubmit::getSubmitVersion)
                .last("LIMIT 1"));
        return latest != null ? latest.getId() : null;
    }

    private ThesisArchiveVO toVO(ThesisArchive archive) {
        ThesisArchiveVO vo = new ThesisArchiveVO();
        vo.setId(archive.getId());
        vo.setArchiveNo(archive.getArchiveNo());
        vo.setPaperId(archive.getPaperId());
        vo.setPaperVersion(archive.getPaperVersion());
        vo.setSubmitId(archive.getSubmitId());
        vo.setPaperTitle(archive.getPaperTitle());
        vo.setAuthor(archive.getAuthor());
        vo.setStudentId(archive.getStudentId());
        vo.setStudentNo(archive.getStudentNo());
        vo.setStudentName(archive.getStudentName());
        vo.setTeacherId(archive.getTeacherId());
        vo.setTeacherName(archive.getTeacherName());
        vo.setCollegeId(archive.getCollegeId());
        vo.setCollegeName(archive.getCollegeName());
        vo.setMajorId(archive.getMajorId());
        vo.setMajorName(archive.getMajorName());
        vo.setDegreeType(archive.getDegreeType());
        vo.setFileId(archive.getFileId());
        vo.setFilePath(archive.getFilePath());
        vo.setFileMd5(archive.getFileMd5());
        vo.setCheckTaskId(archive.getCheckTaskId());
        vo.setCheckReportId(archive.getCheckReportId());
        vo.setReportNo(archive.getReportNo());
        vo.setReportHash(archive.getReportHash());
        vo.setCheckRate(archive.getCheckRate());
        vo.setMaxPerSource(archive.getMaxPerSource());
        vo.setCitationRate(archive.getCitationRate());
        vo.setCheckSource(archive.getCheckSource());
        vo.setArchiveTime(archive.getArchiveTime());
        vo.setArchiveBy(archive.getArchiveBy());
        vo.setArchiveRemark(archive.getArchiveRemark());
        return vo;
    }
}
