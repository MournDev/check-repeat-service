package com.abin.checkrepeatservice.teacher.service.Impl;

import com.abin.checkrepeatservice.admin.service.ReviewWorkflowService;
import com.abin.checkrepeatservice.admin.vo.ReviewWorkflowVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.monitor.service.BusinessMetricsService;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.dto.ContactStudentDTO;
import com.abin.checkrepeatservice.teacher.dto.PaperReviewHistoryDTO;
import com.abin.checkrepeatservice.teacher.dto.ReviewOperateReq;
import com.abin.checkrepeatservice.teacher.dto.ReviewQueryReq;
import com.abin.checkrepeatservice.teacher.dto.ReviewResultDTO;
import com.abin.checkrepeatservice.teacher.mapper.PaperCheckSignoffMapper;
import com.abin.checkrepeatservice.teacher.mapper.ReviewRecordMapper;
import com.abin.checkrepeatservice.teacher.service.TeacherReviewService;
import com.abin.checkrepeatservice.teacher.vo.PendingStatsVO;
import com.abin.checkrepeatservice.teacher.vo.TodayReviewedVO;
import com.abin.checkrepeatservice.common.utils.ReviewAttachUtils;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.user.mapper.PaperStatusLogMapper;
import com.abin.checkrepeatservice.user.service.InstantMessageService;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.abin.checkrepeatservice.user.service.Impl.NotificationFacadeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师审核服务实现类
 */
@Service
@Slf4j
public class TeacherReviewServiceImpl extends ServiceImpl<ReviewRecordMapper, ReviewRecord> implements TeacherReviewService {

    @Resource
    private PaperInfoMapper paperInfoMapper;

    // M2.2 阶段门收口：论文状态流转统一校验（默认影子模式，只告警不阻断）
    @Resource
    private com.abin.checkrepeatservice.common.component.PaperStageGate paperStageGate;

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private CheckReportMapper checkReportMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ReviewAttachUtils reviewAttachUtils;

    @Resource
    private PaperStatusLogMapper paperStatusLogMapper;

    @Resource
    private BusinessMetricsService businessMetricsService;

    @Resource
    private MessageService messageService;

    @Resource
    private NotificationFacadeService notificationFacadeService;

    @Resource
    private InstantMessageService instantMessageService;

    /**
     * P2 接线：多级初审工作流配置（单行配置表）。导师初审通过后按此决定
     * 是直接 completed（既有单轮行为）还是进入多级初审链条。
     */
    @Resource
    private ReviewWorkflowService reviewWorkflowService;

    @Resource
    private PaperCheckSignoffMapper paperCheckSignoffMapper;

    // 批量审核最大数量（从配置文件获取）
    @Value("${review.batch.max-count}")
    private Integer batchMaxCount;

    /**
     * 查重签核门槛（论文全生命周期 P5）：与评阅分配同一口径。
     * true 时教师初审前必须存在「最新 completed 查重任务 + 导师合格签核」，
     * 否则管理端有门、教师端无门的缺口会把风险从 UI 挪到 API。
     */
    @Value("${check.signoff.required:true}")
    private boolean checkSignoffRequired;

    @Override
    public Result<Page<ReviewResultDTO>> getPendingReviewList(ReviewQueryReq queryReq) {
        Long currentTeacherId = UserBusinessInfoUtils.getCurrentUserId();
        Integer currentPage = queryReq.getCurrentPage();
        Integer pageSize = queryReq.getPageSize();

        // 1. 构建分页查询条件
        Page<PaperInfo> paperPage = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
        // 仅查询自己指导的、待审核的、未删除的论文
        paperWrapper.eq(PaperInfo::getTeacherId, currentTeacherId)
                .eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.AUDITING) // -待审核
                .eq(PaperInfo::getIsDeleted, 0);

        // 2. 模糊查询条件（学生姓名、论文标题）
        // S11 安全修复：inSql 字符串拼接存在 SQL 注入，改用 apply {0} 参数绑定
        if (org.springframework.util.StringUtils.hasText(queryReq.getStudentName())) {
            // 关联学生表查询姓名（apply 参数绑定子查询）
            paperWrapper.apply("student_id IN (SELECT id FROM sys_user WHERE real_name LIKE CONCAT('%', {0}, '%') AND is_deleted = 0)",
                    queryReq.getStudentName());
        }
        if (org.springframework.util.StringUtils.hasText(queryReq.getPaperTitle())) {
            paperWrapper.like(PaperInfo::getPaperTitle, queryReq.getPaperTitle());
        }

        // 3. 执行分页查询
        IPage<PaperInfo> paperIPage = paperInfoMapper.selectPage(paperPage, paperWrapper);
        List<PaperInfo> paperList = paperIPage.getRecords();
        if (CollectionUtils.isEmpty(paperList)) {
            return Result.success("待审核论文列表查询成功", new Page<>());
        }

        // 4. 转换为DTO（批量查询关联数据，减少SQL次数）
        List<ReviewResultDTO> resultDTOList = convertToReviewResultDTOList(paperList);

        // 5. 构建分页结果
        Page<ReviewResultDTO> pageInfo = new Page<>();
        pageInfo.setRecords(resultDTOList);
        pageInfo.setCurrent(paperIPage.getCurrent());
        pageInfo.setSize(paperIPage.getSize());
        pageInfo.setTotal(paperIPage.getTotal());
        pageInfo.setPages(paperIPage.getPages());
        return Result.success("待审核论文列表查询成功", pageInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doReview(ReviewOperateReq operateReq) {
        Long currentTeacherId = UserBusinessInfoUtils.getCurrentUserId();
        List<Long> paperIds = operateReq.getPaperIds();
        Integer reviewStatus = operateReq.getReviewStatus();
        String reviewOpinion = operateReq.getReviewOpinion();
        MultipartFile reviewAttach = operateReq.getReviewAttach();
        String suggestedModifications = operateReq.getSuggestedModifications(); // 【新增】获取建议修改点

        // 1. 基础校验
        // 校验批量数量
        if (paperIds.size() > batchMaxCount) {
            return Result.error(ResultCode.PARAM_ERROR,
                    String.format("单次审核最多选择%s篇论文，当前选择%s篇", batchMaxCount, paperIds.size()));
        }
        // 校验审核状态
        if (reviewStatus == null
                || (reviewStatus != DictConstants.ReviewStatus.PASS
                && reviewStatus != DictConstants.ReviewStatus.REJECT)) {
            return Result.error(ResultCode.PARAM_ERROR, "审核状态无效（仅支持3-通过、4-不通过）");
        }
        // 清洗审核意见（防XSS）
        String cleanedOpinion = reviewAttachUtils.cleanReviewOpinion(reviewOpinion);

        // 2. 上传审核附件（若有）
        ReviewAttachUtils.AttachInfo attachInfo = null;
        if (reviewAttach != null && !reviewAttach.isEmpty()) {
            attachInfo = reviewAttachUtils.uploadReviewAttach(reviewAttach, currentTeacherId);
        }

        // 3. 批量处理审核（统计成功/失败数量）
        // P2 接线：工作流配置整个请求只读一次（单行配置表，请求内不会变化）。
        // 启用且配置了步骤 → 导师初审通过后进入多级初审链条；否则保持既有单轮行为。
        ReviewWorkflowVO workflowConfig = reviewWorkflowService.getWorkflow();
        boolean hasMultiLevelWorkflow = Boolean.TRUE.equals(workflowConfig.getEnabled())
                && workflowConfig.getSteps() != null && !workflowConfig.getSteps().isEmpty();
        String firstStepName = hasMultiLevelWorkflow
                ? workflowConfig.getSteps().get(0).getName() : null;

        int successCount = 0;
        List<String> failReasons = new ArrayList<>();
        for (Long paperId : paperIds) {
            try {
                // 3.1 校验论文合法性与状态
                PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
                if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
                    failReasons.add(String.format("论文ID：%s，原因：论文不存在或已删除", paperId));
                    continue;
                }
                // 校验是否为当前教师指导的论文
                if (!paperInfo.getTeacherId().equals(currentTeacherId)) {
                    failReasons.add(String.format("论文ID：%s，原因：无权限审核他人指导的论文", paperId));
                    continue;
                }
                // 校验论文状态（仅待审核状态可审核）
                if (!paperInfo.getPaperStatus().equals(DictConstants.PaperStatus.AUDITING)) {
                    failReasons.add(String.format("论文ID：%s，原因：当前状态（%s）不允许审核（仅待审核可审核）",
                            paperId, paperInfo.getPaperStatus()));
                    continue;
                }

                // 3.2 查询关联的查重任务（取最新的成功任务）
                CheckTask checkTask = checkTaskMapper.selectOne(
                        new LambdaQueryWrapper<CheckTask>()
                                .eq(CheckTask::getPaperId, paperId)
                                .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED) // 2-执行成功
                                .eq(CheckTask::getIsDeleted, 0)
                                .orderByDesc(CheckTask::getCreateTime)
                                .last("LIMIT 1")
                );
                if (checkTask == null) {
                    failReasons.add(String.format("论文ID：%s，原因：未找到有效的查重任务结果", paperId));
                    continue;
                }

                // 3.2.1 查重签核门槛（P5）：教师初审与管理端评阅分配必须同一口径。
                // 最新 completed 任务必须已有导师签核且 qualified=1，否则教师路径无门可绕，
                // 风险只是从 UI 挪到 API。
                PaperCheckSignoff signoff = null;
                if (checkSignoffRequired) {
                    signoff = paperCheckSignoffMapper.selectOne(
                            new LambdaQueryWrapper<PaperCheckSignoff>()
                                    .eq(PaperCheckSignoff::getPaperId, paperId)
                                    .eq(PaperCheckSignoff::getTaskId, checkTask.getId())
                                    .last("LIMIT 1"));
                    if (signoff == null || !Integer.valueOf(1).equals(signoff.getQualified())) {
                        failReasons.add(String.format(
                                "论文ID：%s，原因：查重报告未经导师签核或结论不合格，不可审核", paperId));
                        continue;
                    }
                }

                // 3.3 创建审核记录
                ReviewRecord reviewRecord = new ReviewRecord();
                reviewRecord.setPaperId(paperId);
                reviewRecord.setTaskId(checkTask.getId());
                reviewRecord.setTeacherId(currentTeacherId);
                reviewRecord.setReviewStatus(reviewStatus);
                reviewRecord.setReviewOpinion(cleanedOpinion);
                reviewRecord.setSuggestedModifications(suggestedModifications);
                // 填充附件信息（若有）
                if (attachInfo != null) {
                    reviewRecord.setReviewAttach(attachInfo.getAttachPath());
                }
                reviewRecord.setReviewTime(LocalDateTime.now());
                // 审核留痕：责任主体、来源与签核关联由服务端写入
                reviewRecord.setAuditSource("TEACHER");
                reviewRecord.setAuditorId(currentTeacherId);
                reviewRecord.setAuditorRole(UserBusinessInfoUtils.getCurrentUserRoleCode());
                reviewRecord.setSignoffId(signoff == null ? null : signoff.getId());
                UserBusinessInfoUtils.setAuditField(reviewRecord, true); // 填充审计字段
                save(reviewRecord);

                // 3.4 更新论文状态（3-审核通过，4-审核不通过）
                // P2 接线：启用多级初审时，导师初审通过不直接 completed，而是保持 auditing
                // 并置 workflow_step=1 进入工作流链条，由 TeacherWorkflowReviewService 逐步推进；
                // 任一步驳回 → rejected，全部步骤通过 → completed（见 TeacherWorkflowReviewServiceImpl）。
                PaperInfo updatePaper = new PaperInfo();
                updatePaper.setId(paperId);
                String oldStatus = paperInfo.getPaperStatus();
                String statusReason;
                if (reviewStatus == DictConstants.ReviewStatus.PASS) { // 3-审核通过
                    if (hasMultiLevelWorkflow) {
                        updatePaper.setPaperStatus(DictConstants.PaperStatus.AUDITING);
                        updatePaper.setWorkflowStep(1);
                        statusReason = "导师初审通过，进入多级初审第1步：" + firstStepName;
                    } else {
                        updatePaper.setPaperStatus(DictConstants.PaperStatus.COMPLETED);
                        statusReason = "导师审核通过";
                    }
                } else { // 4-审核不通过
                    updatePaper.setPaperStatus(DictConstants.PaperStatus.REJECTED);
                    updatePaper.setWorkflowStep(0);
                    statusReason = "导师审核不通过";
                }
                paperStageGate.guard(oldStatus, updatePaper.getPaperStatus(), "teacherBatchReview");
                paperInfoMapper.updateById(updatePaper);
                
                successCount++;

                // 业务指标埋点：按「篇」计（本接口是批量审核），3=审核通过 4=审核不通过
                if (reviewStatus == DictConstants.ReviewStatus.PASS) {
                    businessMetricsService.record(BusinessMetricsService.Event.PAPER_REVIEW,
                            BusinessMetricsService.Outcome.PASS);
                } else if (reviewStatus == DictConstants.ReviewStatus.REJECT) {
                    businessMetricsService.record(BusinessMetricsService.Event.PAPER_REVIEW,
                            BusinessMetricsService.Outcome.REJECT);
                }

                log.info("论文审核成功：论文 ID={}，审核结果={}，审核记录 ID={}",
                        paperId, reviewStatus == DictConstants.ReviewStatus.PASS ? "通过" : "不通过", reviewRecord.getId());
                
                // 3.5 记录论文状态变更日志（AUDITING → COMPLETED/REJECTED 或进入多级初审）
                recordPaperStatusLog(paperId, oldStatus, updatePaper.getPaperStatus(), statusReason);

            } catch (Exception e) {
                log.error("论文审核失败（论文ID：{}）：", paperId, e);
                failReasons.add(String.format("论文ID：%s，原因：%s", paperId, e.getMessage()));
            }
        }

        // 4. 构建返回结果
        Map<String, Object> resultMap = new HashMap<>(4);
        resultMap.put("totalCount", paperIds.size());
        resultMap.put("successCount", successCount);
        resultMap.put("failCount", paperIds.size() - successCount);
        resultMap.put("failReasons", failReasons);

        return Result.success("审核操作完成", resultMap);
    }

    @Override
    public Result<Page<ReviewResultDTO>> getReviewedList(ReviewQueryReq queryReq) {
        Long currentTeacherId = UserBusinessInfoUtils.getCurrentUserId();
        Integer currentPage = queryReq.getCurrentPage();
        Integer pageSize = queryReq.getPageSize();

        // 1. 构建分页查询条件（已审核：初审通过 completed / 初审不通过 rejected）
        // reviewStatus 为可选筛选：3 → completed，4 → rejected
        Page<PaperInfo> paperPage = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<PaperInfo> paperWrapper = new LambdaQueryWrapper<>();
        paperWrapper.eq(PaperInfo::getTeacherId, currentTeacherId)
                .eq(PaperInfo::getIsDeleted, 0);
        Integer reviewStatusFilter = queryReq.getReviewStatus();
        if (reviewStatusFilter != null && reviewStatusFilter == DictConstants.ReviewStatus.PASS) {
            paperWrapper.eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.COMPLETED);
        } else if (reviewStatusFilter != null && reviewStatusFilter == DictConstants.ReviewStatus.REJECT) {
            paperWrapper.eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.REJECTED);
        } else {
            paperWrapper.in(PaperInfo::getPaperStatus,
                    DictConstants.PaperStatus.COMPLETED,
                    DictConstants.PaperStatus.REJECTED);
        }

        // 2. 模糊查询条件（与待审核列表一致）
        // S11 安全修复：inSql 字符串拼接存在 SQL 注入，改用 apply {0} 参数绑定
        if (org.springframework.util.StringUtils.hasText(queryReq.getStudentName())) {
            paperWrapper.apply("student_id IN (SELECT id FROM sys_user WHERE real_name LIKE CONCAT('%', {0}, '%') AND is_deleted = 0)",
                    queryReq.getStudentName());
        }
        if (org.springframework.util.StringUtils.hasText(queryReq.getPaperTitle())) {
            paperWrapper.like(PaperInfo::getPaperTitle, queryReq.getPaperTitle());
        }

        // 3. 执行分页查询
        IPage<PaperInfo> paperIPage = paperInfoMapper.selectPage(paperPage, paperWrapper);
        List<PaperInfo> paperList = paperIPage.getRecords();
        List<ReviewResultDTO> resultDTOList = CollectionUtils.isEmpty(paperList)
                ? new ArrayList<>()
                : convertToReviewResultDTOList(paperList);

        // 4. 构建分页结果
        Page<ReviewResultDTO> pageInfo = new Page<>();
        pageInfo.setRecords(resultDTOList);
        pageInfo.setCurrent(paperIPage.getCurrent());
        pageInfo.setSize(paperIPage.getSize());
        pageInfo.setTotal(paperIPage.getTotal());
        pageInfo.setPages(paperIPage.getPages());

        return Result.success("已审核论文列表查询成功", pageInfo);
    }


    @Override
    public Result<ReviewResultDTO> getReviewDetail(@RequestParam("paperId") Long paperId) {
        Long currentTeacherId = UserBusinessInfoUtils.getCurrentUserId();

        // 1. 校验论文合法性与权限
        PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
        if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }
//        if (!paperInfo.getTeacherId().equals(currentTeacherId) && !UserBusinessInfoUtils.isAdmin()) {
//            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限查看他人指导论文的审核详情");
//        }

        // 2. 转换为DTO
        List<ReviewResultDTO> dtoList = convertToReviewResultDTOList(Collections.singletonList(paperInfo));
        if (CollectionUtils.isEmpty(dtoList)) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "未找到该论文的审核记录");
        }

        return Result.success("审核详情查询成功", dtoList.get(0));
    }

    @Override
    public void downloadReviewAttach(@RequestParam("attachPath") String attachPath, HttpServletResponse response) {
        // 1. 校验附件路径合法性
        if (attachPath == null || attachPath.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_EMPTY, "附件路径不能为空");
        }
        // 校验路径是否为审核附件目录（防路径遍历攻击）
        if (!attachPath.startsWith("/data/review-attach/")) {
            throw new BusinessException(ResultCode.PARAM_FORMAT_ERROR, "附件路径不合法");
        }

        // 2. 读取附件文件
        java.nio.file.Path filePath = java.nio.file.Paths.get(attachPath);
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "审核附件不存在或已删除");
        }

        // 3. 设置响应头（触发下载）
        try {
            // 获取附件原文件名（从审核记录中查询，此处简化为从路径提取）
            String fileName = "审核附件." + attachPath.substring(attachPath.lastIndexOf(".") + 1);
            // 处理中文文件名编码
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());

            // 设置响应头
            response.setContentType(Files.probeContentType(filePath));
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
            response.setHeader("Content-Length", String.valueOf(Files.size(filePath)));

            // 4. 输出文件流
            try (InputStream in = Files.newInputStream(filePath);
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
            log.info("审核附件下载成功：{}", attachPath);

        } catch (IOException e) {
            log.error("审核附件下载失败（路径：{}）：", attachPath, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "附件下载异常，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> reInitiateReview(@RequestParam("paperId") Long paperId) {
        Long currentTeacherId = UserBusinessInfoUtils.getCurrentUserId();

        // 1. 校验论文合法性与状态
        PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
        if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }
        // 仅审核不通过的论文可重新发起审核
//        if (paperInfo.getPaperStatus() != 4) {
//            return Result.error(ResultCode.PERMISSION_NOT_STATUS,
//                    String.format("仅审核不通过（状态4）的论文可重新发起审核，当前状态：%s", getPaperStatusDesc(paperInfo.getPaperStatus())));
//        }
//        // 校验权限（仅指导教师或管理员可操作）
//        if (!paperInfo.getTeacherId().equals(currentTeacherId) && !UserBusinessInfoUtils.isAdmin()) {
//            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限重新发起他人指导论文的审核");
//        }

        // 2. 更新论文状态为“待审核”
        PaperInfo updatePaper = new PaperInfo();
        updatePaper.setId(paperId);
        paperStageGate.guard(paperInfo.getPaperStatus(),
                DictConstants.PaperStatus.AUDITING, "reInitiateReview");
        updatePaper.setPaperStatus(DictConstants.PaperStatus.AUDITING); // 2-待审核
        // P2 接线：重新发起审核回到导师初审起点，多级初审进度一并归零
        updatePaper.setWorkflowStep(0);
        paperInfoMapper.updateById(updatePaper);

        log.info("论文重新发起审核成功：论文ID={}，指导教师ID={}", paperId, currentTeacherId);
        return Result.success("重新发起审核成功，论文已进入待审核队列");
    }

    // ------------------------------ 私有辅助方法 ------------------------------
    /**
     * 记录论文状态变更日志
     */
    private void recordPaperStatusLog(Long paperId, String oldStatus, String newStatus, String reason) {
        try {
            com.abin.checkrepeatservice.pojo.entity.PaperStatusLog statusLog = 
                new com.abin.checkrepeatservice.pojo.entity.PaperStatusLog();
            statusLog.setPaperId(paperId);
            // 状态值即 DictConstants.PaperStatus 的字符串字典值，与库列 varchar(50) 直接对应，
            // 严禁 Integer.parseInt（"auditing" 这类值会抛异常且被本 catch 吞掉，日志静默丢失）
            statusLog.setOldStatus(oldStatus);
            statusLog.setNewStatus(newStatus);
            statusLog.setStatusReason(reason);
            UserBusinessInfoUtils.setAuditField(statusLog, true);
            paperStatusLogMapper.insert(statusLog);
            log.info("论文状态日志记录成功 - 论文 ID: {}, {} -> {}, 原因：{}", paperId, oldStatus, newStatus, reason);
        } catch (Exception e) {
            log.error("论文状态日志记录失败 - 论文 ID: {}", paperId, e);
            // 日志记录失败不影响主流程，仅警告
        }
    }
        
    /**
     * 批量转换 PaperInfo 列表为 ReviewResultDTO 列表（减少 SQL 查询次数）
     */
    private List<ReviewResultDTO> convertToReviewResultDTOList(List<PaperInfo> paperList) {
        // 1. 批量查询关联数据（学生、任务、报告、审核记录）
        // 1.1 提取论文ID、学生ID列表
        List<Long> paperIds = paperList.stream().map(PaperInfo::getId).collect(Collectors.toList());
        List<Long> studentIds = paperList.stream().map(PaperInfo::getStudentId).distinct().collect(Collectors.toList());

        // 1.2 批量查询学生信息（Map<学生ID, 学生实体>）
        Map<Long, SysUser> studentMap = sysUserMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(SysUser::getId, student -> student));

        // 1.3 批量查询查重任务（Map<论文ID, 最新成功任务>）
        Map<Long, CheckTask> taskMap = new HashMap<>();
        List<CheckTask> taskList = checkTaskMapper.selectList(
                new LambdaQueryWrapper<CheckTask>()
                        .in(CheckTask::getPaperId, paperIds)
                        .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED) // 仅成功任务
                        .eq(CheckTask::getIsDeleted, 0)
        );
        // 按论文ID分组，取最新任务
        Map<Long, List<CheckTask>> taskGroupByPaperId = taskList.stream()
                .collect(Collectors.groupingBy(CheckTask::getPaperId));
        for (Map.Entry<Long, List<CheckTask>> entry : taskGroupByPaperId.entrySet()) {
            // 按创建时间倒序，取第一个
            CheckTask latestTask = entry.getValue().stream()
                    .sorted((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()))
                    .findFirst()
                    .orElse(null);
            taskMap.put(entry.getKey(), latestTask);
        }

        // 1.4 批量查询审核记录（Map<论文ID, 最新审核记录>）
        Map<Long, ReviewRecord> reviewMap = new HashMap<>();
        List<ReviewRecord> reviewList = list(
                new LambdaQueryWrapper<ReviewRecord>()
                        .in(ReviewRecord::getPaperId, paperIds)
                        .eq(ReviewRecord::getIsDeleted, 0)
        );
        Map<Long, List<ReviewRecord>> reviewGroupByPaperId = reviewList.stream()
                .collect(Collectors.groupingBy(ReviewRecord::getPaperId));
        for (Map.Entry<Long, List<ReviewRecord>> entry : reviewGroupByPaperId.entrySet()) {
            ReviewRecord latestReview = entry.getValue().stream()
                    .sorted((r1, r2) -> r2.getReviewTime().compareTo(r1.getReviewTime()))
                    .findFirst()
                    .orElse(null);
            reviewMap.put(entry.getKey(), latestReview);
        }

        // S10 性能修复：批量预载本页报告与评审教师，消除循环内逐条 selectById（N+1）
        // 1.5 批量查询查重报告（Map<报告ID, 报告>）
        List<Long> reportIds = taskMap.values().stream()
                .map(CheckTask::getReportId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, CheckReport> reportMap = reportIds.isEmpty() ? new HashMap<>()
                : checkReportMapper.selectBatchIds(reportIds).stream()
                        .collect(Collectors.toMap(CheckReport::getId, r -> r));

        // 1.6 批量查询评审教师姓名（Map<教师ID, 用户>）
        List<Long> reviewerIds = reviewMap.values().stream()
                .map(ReviewRecord::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> reviewerMap = reviewerIds.isEmpty() ? new HashMap<>()
                : sysUserMapper.selectBatchIds(reviewerIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));

        // Wave 3：批量预载最新签核，待审列表直接携带 signoffQualified / signoffId / canBatchReview，
        // 前端批量提交前无需再逐篇调用 check-signoff（消除 N 次请求）。
        Map<Long, PaperCheckSignoff> signoffMap = new HashMap<>();
        List<PaperCheckSignoff> signoffList = paperCheckSignoffMapper.selectList(
                new LambdaQueryWrapper<PaperCheckSignoff>()
                        .in(PaperCheckSignoff::getPaperId, paperIds)
                        .orderByDesc(PaperCheckSignoff::getSignTime));
        for (PaperCheckSignoff signoff : signoffList) {
            signoffMap.putIfAbsent(signoff.getPaperId(), signoff);
        }

        // 2. 转换为DTO
        return paperList.stream().map(paper -> {
            ReviewResultDTO dto = new ReviewResultDTO();

            // 2.1 填充论文基础信息
            ReviewResultDTO.PaperBaseInfoDTO paperBaseInfo = new ReviewResultDTO.PaperBaseInfoDTO();
            paperBaseInfo.setPaperId(paper.getId());
            paperBaseInfo.setPaperTitle(paper.getPaperTitle());
            SysUser student = studentMap.get(paper.getStudentId());
            if (student != null) {
                paperBaseInfo.setStudentName(student.getRealName());
                paperBaseInfo.setStudentNo(student.getUsername()); // 学生学号=username
                paperBaseInfo.setCollege(student.getCollegeName());
                paperBaseInfo.setEmail(student.getEmail());
            }
            paperBaseInfo.setSubmitTime(paper.getSubmitTime());
            paperBaseInfo.setPaperStatus(paper.getPaperStatus());
            dto.setPaperBaseInfo(paperBaseInfo);

            // Wave 3：签核状态（最新一条，按 signTime 倒序取首条）
            PaperCheckSignoff signoff = signoffMap.get(paper.getId());
            boolean signoffQualified = signoff != null && Integer.valueOf(1).equals(signoff.getQualified());
            dto.setSignoffQualified(signoffQualified);
            dto.setSignoffId(signoff == null ? null : signoff.getId());
            dto.setCanBatchReview(signoffQualified
                    && DictConstants.PaperStatus.AUDITING.equals(paper.getPaperStatus()));

            // 2.2 填充查重任务信息
            CheckTask task = taskMap.get(paper.getId());
            if (task != null) {
                ReviewResultDTO.CheckTaskBaseDTO taskBaseInfo = new ReviewResultDTO.CheckTaskBaseDTO();
                taskBaseInfo.setTaskId(task.getId());
                taskBaseInfo.setTaskNo(task.getTaskNo());
                taskBaseInfo.setCheckRate(task.getCheckRate() != null ? task.getCheckRate().doubleValue() : 0.0);
                taskBaseInfo.setCheckEndTime(task.getEndTime());
                // 查询报告编号（取自批量预载 Map）
                CheckReport report = task.getReportId() == null ? null : reportMap.get(task.getReportId());
                if (report != null) {
                    taskBaseInfo.setReportNo(report.getReportNo());
                }
                dto.setTaskBaseInfo(taskBaseInfo);
            }

            // 2.3 填充审核操作信息
            ReviewRecord review = reviewMap.get(paper.getId());
            if (review != null) {
                ReviewResultDTO.ReviewOperateInfoDTO reviewOperateInfo = new ReviewResultDTO.ReviewOperateInfoDTO();
                reviewOperateInfo.setReviewStatus(review.getReviewStatus());
                reviewOperateInfo.setReviewStatusDesc(
                        review.getReviewStatus() != null
                                && review.getReviewStatus() == DictConstants.ReviewStatus.PASS
                                ? "审核通过" : "审核不通过");
                reviewOperateInfo.setReviewOpinion(review.getReviewOpinion());
                // 查询审核教师姓名（取自批量预载 Map）
                SysUser teacher = review.getTeacherId() == null ? null : reviewerMap.get(review.getTeacherId());
                if (teacher != null) {
                    reviewOperateInfo.setReviewerName(teacher.getRealName());
                }
                reviewOperateInfo.setReviewTime(review.getReviewTime());
                dto.setReviewOperateInfo(reviewOperateInfo);

                // 2.4 填充审核附件信息（若有）
                String attachPath = review.getReviewAttach();
                if (org.springframework.util.StringUtils.hasText(attachPath) && Files.exists(Paths.get(attachPath))) {
                    ReviewResultDTO.ReviewAttachDTO attachDTO = new ReviewResultDTO.ReviewAttachDTO();
                    // 提取附件原文件名（从路径提取，简化处理；实际应存储原文件名）
                    attachDTO.setAttachName("审核附件." + attachPath.substring(attachPath.lastIndexOf(".") + 1));
                    attachDTO.setAttachPath(attachPath);
                    try {
                        attachDTO.setAttachSize(Files.size(Paths.get(attachPath)));
                    } catch (IOException e) {
                        attachDTO.setAttachSize(0L);
                    }
                    attachDTO.setAttachType(attachPath.substring(attachPath.lastIndexOf(".") + 1));
                    // 构建下载URL（前端拼接域名）
                    attachDTO.setDownloadUrl("/api/teacher/reviews/download-attach?attachPath=" + URLEncoder.encode(attachPath, StandardCharsets.UTF_8));
                    dto.setReviewAttach(attachDTO);
                }

                // 2.5 填充审核记录ID
                dto.setReviewId(review.getId());
            }

            // 2.6 审核意见回显（S12 补链）：正式审核记录优先，
            // 无 review_record（TeacherDashboard/管理员审核路径）时回退 paper_info.audit_comment
            String recordOpinion = review == null ? null : review.getReviewOpinion();
            dto.setFeedback(org.springframework.util.StringUtils.hasText(recordOpinion)
                    ? recordOpinion : paper.getAuditComment());

            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public Result<PendingStatsVO> getPendingStats() {
        try {
            Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
            PendingStatsVO stats = new PendingStatsVO();

            LambdaQueryWrapper<PaperInfo> pendingWrapper = new LambdaQueryWrapper<>();
            pendingWrapper.eq(PaperInfo::getTeacherId, teacherId)
                    .eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.AUDITING)
                    .eq(PaperInfo::getIsDeleted, 0);
            stats.setTotalPending(paperInfoMapper.selectCount(pendingWrapper).intValue());
            stats.setUrgentPending(0);

            List<PaperInfo> pendingPapers = paperInfoMapper.selectList(pendingWrapper);
            if (pendingPapers.isEmpty()) {
                stats.setAvgWaitingTime(BigDecimal.ZERO);
            } else {
                long totalWaitingDays = pendingPapers.stream()
                        .map(PaperInfo::getSubmitTime)
                        .filter(Objects::nonNull)
                        .mapToLong(submitTime -> ChronoUnit.DAYS.between(
                                submitTime.toLocalDate(), LocalDateTime.now().toLocalDate()))
                        .sum();
                stats.setAvgWaitingTime(BigDecimal.valueOf(totalWaitingDays)
                        .divide(BigDecimal.valueOf(pendingPapers.size()), 1, RoundingMode.HALF_UP));
            }

            stats.setTodayReviewed(countTodayReviewed(teacherId).intValue());
            stats.setOverdueCount(0);
            stats.setUrgentCount(0);

            return Result.success("获取统计信息成功", stats);
        } catch (Exception e) {
            log.error("获取待审核统计信息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取统计信息失败: 系统异常，请稍后重试");
        }
    }

    @Override
    public Result<TodayReviewedVO> getTodayReviewedCount() {
        try {
            Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
            TodayReviewedVO todayStats = new TodayReviewedVO();
            todayStats.setCount(countTodayReviewed(teacherId).intValue());

            List<TodayReviewedVO.HourlyCountVO> hourlyStats = new ArrayList<>();
            LocalDateTime dayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            for (int hour = 0; hour < 24; hour += 3) {
                LocalDateTime hourStart = dayStart.plusHours(hour);
                LocalDateTime hourEnd = hourStart.plusHours(3);

                LambdaQueryWrapper<PaperInfo> hourWrapper = new LambdaQueryWrapper<>();
                hourWrapper.eq(PaperInfo::getTeacherId, teacherId)
                        .ne(PaperInfo::getPaperStatus, DictConstants.PaperStatus.AUDITING)
                        .between(PaperInfo::getUpdateTime, hourStart, hourEnd)
                        .eq(PaperInfo::getIsDeleted, 0);

                TodayReviewedVO.HourlyCountVO hourlyStat = new TodayReviewedVO.HourlyCountVO();
                hourlyStat.setHour(hour);
                hourlyStat.setCount(paperInfoMapper.selectCount(hourWrapper).intValue());
                hourlyStats.add(hourlyStat);
            }
            todayStats.setDetails(hourlyStats);

            return Result.success("获取今日审核统计成功", todayStats);
        } catch (Exception e) {
            log.error("获取今日审核统计失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取今日审核统计失败: 系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Map<String, Object>> contactStudent(ContactStudentDTO contactDTO) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (contactDTO.getMessageType() == null || contactDTO.getMessage() == null
                    || contactDTO.getMessage().isBlank()) {
                return Result.error(ResultCode.PARAM_ERROR, "消息类型和内容不能为空");
            }

            // 前端审核记录列表不返回 studentId，缺省时按 paperId 反查并校验归属
            if (contactDTO.getStudentId() == null || contactDTO.getStudentId().isBlank()) {
                if (contactDTO.getPaperId() == null || contactDTO.getPaperId().isBlank()) {
                    return Result.error(ResultCode.PARAM_ERROR, "缺少学生ID或论文ID");
                }
                Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
                PaperInfo paper = paperInfoMapper.selectById(Long.parseLong(contactDTO.getPaperId()));
                if (paper == null) {
                    return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
                }
                if (!paper.getTeacherId().equals(teacherId)) {
                    return Result.error(ResultCode.PARAM_ERROR, "无权限联系该学生");
                }
                contactDTO.setStudentId(paper.getStudentId().toString());
            }

            // 归属校验：显式传 studentId 时也必须确认该学生在本教师名下，
            // 否则可向任意学生发送消息（原先仅在缺省 studentId 时才校验论文归属）
            Result<Map<String, Object>> targetError = validateContactTarget(contactDTO);
            if (targetError != null) {
                return targetError;
            }

            switch (contactDTO.getMessageType().toLowerCase()) {
                case "chat":
                    return sendInstantMessage(contactDTO, result);
                case "email":
                    return sendEmailMessage(contactDTO, result);
                case "system":
                    return sendSystemMessage(contactDTO, result);
                default:
                    return Result.error(ResultCode.PARAM_ERROR, "不支持的消息类型: " + contactDTO.getMessageType());
            }
        } catch (Exception e) {
            log.error("联系学生失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "联系学生失败: 系统异常，请稍后重试");
        }
    }

    private Long countTodayReviewed(Long teacherId) {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperInfo::getTeacherId, teacherId)
                .ne(PaperInfo::getPaperStatus, DictConstants.PaperStatus.AUDITING)
                .between(PaperInfo::getUpdateTime, todayStart, todayEnd)
                .eq(PaperInfo::getIsDeleted, 0);
        return paperInfoMapper.selectCount(wrapper);
    }

    private Result<Map<String, Object>> sendInstantMessage(ContactStudentDTO contactDTO, Map<String, Object> result) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        InstantMessage message = new InstantMessage();
        message.setSenderId(currentUserId);
        message.setReceiverId(Long.parseLong(contactDTO.getStudentId()));
        message.setContent(contactDTO.getMessage());
        message.setMessageType("PRIVATE");
        message.setContentType("TEXT");
        message.setStatus("SENT");
        message.setRelatedType("PAPER");
        message.setRelatedId(contactDTO.getPaperId() != null ? Long.parseLong(contactDTO.getPaperId()) : null);

        Result<Boolean> sendResult = instantMessageService.sendMessage(message);
        if (sendResult.isSuccess() && Boolean.TRUE.equals(sendResult.getData())) {
            result.put("messageId", message.getId());
            result.put("sendTime", LocalDateTime.now().toString());
            return Result.success("即时消息发送成功", result);
        }
        return Result.error(ResultCode.SYSTEM_ERROR, "即时消息发送失败: " + sendResult.getMessage());
    }

    private Result<Map<String, Object>> sendSystemMessage(ContactStudentDTO contactDTO, Map<String, Object> result) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        SystemMessage message = new SystemMessage();
        message.setSenderId(currentUserId);
        message.setReceiverId(Long.parseLong(contactDTO.getStudentId()));
        message.setTitle("论文指导反馈");
        message.setContent(contactDTO.getMessage());
        message.setMessageType("GUIDANCE");
        message.setPriority(1);
        message.setRelatedType("PAPER");
        message.setRelatedId(contactDTO.getPaperId() != null ? Long.parseLong(contactDTO.getPaperId()) : null);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        message.setIsRead(0);

        Result<Boolean> sendResult = messageService.sendMessage(message);
        if (sendResult.isSuccess() && Boolean.TRUE.equals(sendResult.getData())) {
            result.put("messageId", "sys_" + System.currentTimeMillis());
            result.put("sendTime", LocalDateTime.now().toString());
            return Result.success("系统消息发送成功", result);
        }
        return Result.error(ResultCode.SYSTEM_ERROR, "系统消息发送失败: " + sendResult.getMessage());
    }

    /**
     * 校验教师是否有权联系目标学生：有 paperId 时按论文归属校验，无 paperId 时要求
     * 该教师名下至少有一篇该学生的论文。
     */
    private Result<Map<String, Object>> validateContactTarget(ContactStudentDTO contactDTO) {
        Long studentId;
        try {
            studentId = Long.parseLong(contactDTO.getStudentId());
        } catch (NumberFormatException e) {
            return Result.error(ResultCode.PARAM_ERROR, "学生ID格式不正确");
        }
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();

        if (StringUtils.hasText(contactDTO.getPaperId())) {
            Long paperId;
            try {
                paperId = Long.parseLong(contactDTO.getPaperId());
            } catch (NumberFormatException e) {
                return Result.error(ResultCode.PARAM_ERROR, "论文ID格式不正确");
            }
            PaperInfo paper = paperInfoMapper.selectById(paperId);
            if (paper == null || Integer.valueOf(1).equals(paper.getIsDeleted())) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在");
            }
            if (!Objects.equals(paper.getTeacherId(), teacherId)) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限联系该学生");
            }
            if (!Objects.equals(paper.getStudentId(), studentId)) {
                return Result.error(ResultCode.PARAM_ERROR, "学生与论文不匹配");
            }
            return null;
        }

        Long relationCount = paperInfoMapper.selectCount(new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getTeacherId, teacherId)
                .eq(PaperInfo::getStudentId, studentId)
                .eq(PaperInfo::getIsDeleted, 0));
        if (relationCount == null || relationCount == 0) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限联系该学生");
        }
        return null;
    }

    private Result<Map<String, Object>> sendEmailMessage(ContactStudentDTO contactDTO, Map<String, Object> result) {
        Long studentId;
        try {
            studentId = Long.parseLong(contactDTO.getStudentId());
        } catch (NumberFormatException e) {
            return Result.error(ResultCode.PARAM_ERROR, "学生ID格式不正确");
        }

        SysUser student = sysUserMapper.selectById(studentId);
        if (student == null || Integer.valueOf(1).equals(student.getIsDeleted())) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "学生不存在");
        }
        if (!StringUtils.hasText(student.getEmail())) {
            return Result.error(ResultCode.PARAM_ERROR, "学生未填写邮箱，无法发送邮件");
        }
        // 尊重学生邮件偏好（关闭后只保留站内信通道）
        if (!Integer.valueOf(1).equals(student.getNotifyEmail())) {
            return Result.error(ResultCode.BUSINESS_ILLEGAL, "学生已关闭邮件通知，请改用站内信");
        }

        String paperTitle = null;
        if (StringUtils.hasText(contactDTO.getPaperId())) {
            PaperInfo paper = paperInfoMapper.selectById(Long.parseLong(contactDTO.getPaperId()));
            paperTitle = paper == null ? null : paper.getPaperTitle();
        }
        SysUser teacher = sysUserMapper.selectById(UserBusinessInfoUtils.getCurrentUserId());
        String teacherName = teacher != null && StringUtils.hasText(teacher.getRealName())
                ? teacher.getRealName() : "指导老师";

        boolean sent = notificationFacadeService.sendTeacherContactEmail(
                studentId, teacherName, paperTitle, contactDTO.getMessage());
        if (!sent) {
            log.warn("教师邮件联系学生失败: teacherId={}, studentId={}",
                    teacher == null ? null : teacher.getId(), studentId);
            return Result.error(ResultCode.SYSTEM_ERROR, "邮件发送失败，请检查学生邮箱或邮件服务配置");
        }

        log.info("教师邮件联系学生成功: studentId={}", studentId);
        result.put("channel", "email");
        result.put("sendTime", LocalDateTime.now().toString());
        return Result.success("邮件消息发送成功", result);
    }

    @Override
    public Result<PaperReviewHistoryDTO> getPaperReviewHistory(Long paperId) {
        try {
            Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || Integer.valueOf(1).equals(paperInfo.getIsDeleted())) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }
            if (!paperInfo.getTeacherId().equals(teacherId)) {
                return Result.error(ResultCode.PARAM_ERROR, "无权限访问此论文");
            }

            LambdaQueryWrapper<ReviewRecord> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ReviewRecord::getPaperId, paperId)
                    .eq(ReviewRecord::getIsDeleted, 0)
                    .orderByDesc(ReviewRecord::getReviewTime);
            List<ReviewRecord> reviewRecords = list(queryWrapper);

            PaperReviewHistoryDTO historyDTO = new PaperReviewHistoryDTO();
            historyDTO.setPaperId(paperId.toString());
            historyDTO.setPaperTitle(paperInfo.getPaperTitle());

            List<PaperReviewHistoryDTO.ReviewHistoryRecord> historyRecords = reviewRecords.stream()
                    .map(record -> {
                        PaperReviewHistoryDTO.ReviewHistoryRecord historyRecord = new PaperReviewHistoryDTO.ReviewHistoryRecord();
                        historyRecord.setReviewId(record.getId().toString());

                        SysUser reviewer = sysUserMapper.selectById(record.getTeacherId());
                        historyRecord.setReviewerName(reviewer != null ? reviewer.getRealName() : "未知教师");
                        historyRecord.setReviewStatus(record.getReviewStatus());
                        historyRecord.setReviewOpinion(record.getReviewOpinion());
                        historyRecord.setReviewTime(record.getReviewTime());
                        historyRecord.setVersion("V" + (reviewRecords.indexOf(record) + 1));
                        historyRecord.setReviewStatusDesc(getReviewStatusDesc(record.getReviewStatus()));
                        return historyRecord;
                    })
                    .collect(Collectors.toList());
            historyDTO.setHistory(historyRecords);

            return Result.success("获取审核历史成功", historyDTO);
        } catch (Exception e) {
            log.error("获取论文审核历史失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取审核历史失败: 系统异常，请稍后重试");
        }
    }

    private String getReviewStatusDesc(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        if (status == DictConstants.ReviewStatus.PASS || status == 1) {
            return "审核通过";
        }
        if (status == DictConstants.ReviewStatus.REJECT || status == 2) {
            return "审核不通过";
        }
        return "未知状态";
    }

//    /**
//     * 获取论文状态描述
//     */
//    private String getPaperStatusDesc(Integer status) {
//        return switch (status) {
//            case 0 -> "待查重";
//            case 1 -> "查重中";
//            case 2 -> "待审核";
//            case 3 -> "审核通过";
//            case 4 -> "审核不通过";
//            default -> "未知状态";
//        };
//    }
}
