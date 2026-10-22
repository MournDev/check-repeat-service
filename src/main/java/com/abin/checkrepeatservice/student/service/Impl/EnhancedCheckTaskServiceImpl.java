package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.admin.service.CollegeQuotaService;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.engine.CheckEngineManager;
import com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum;
import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.SimilarityThresholdResolver;
import com.abin.checkrepeatservice.common.utils.PdfReportGenerator;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.student.dto.CheckTaskResultDTO;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.abin.checkrepeatservice.student.event.EnhancedCheckTaskExecuteEvent;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.EnhancedCheckTaskService;
import com.abin.checkrepeatservice.user.service.SysUserService;
import com.abin.checkrepeatservice.user.vo.CheckResultVO;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强版查重任务服务实现
 * 提供完整的任务生命周期管理
 */
@Service
@Slf4j
public class EnhancedCheckTaskServiceImpl extends ServiceImpl<CheckTaskMapper, CheckTask> 
        implements EnhancedCheckTaskService {
    
    @Resource
    private PaperInfoMapper paperInfoMapper;

    // M3.1 版本—报告绑定：任务落库时快照论文版本与最新提交记录
    @Resource
    private com.abin.checkrepeatservice.admin.mapper.PaperSubmitMapper paperSubmitMapper;

    // 架构修复（P0）：异步执行改为 AFTER_COMMIT 事件，修复本类 @Async 自调用失效
    @Resource
    private ApplicationEventPublisher eventPublisher;

    // 架构修复（P0）：比对库入库管道
    @Resource
    private com.abin.checkrepeatservice.common.service.PaperLibraryIngestService paperLibraryIngestService;

    @Resource
    private CheckReportMapper checkReportMapper;
    
    @Resource
    private CheckEngineManager checkEngineManager;
    
    @Resource
    private PdfReportGenerator pdfReportGenerator;
    
    @Resource
    private SysUserService sysUserService;
    
    @Value("${file.upload.base-path}")
    private String basePath;
    
    @Value("${check.task.timeout:3600000}")
    private long taskTimeout;
    
    // 合格判定阈值改为按学生所属学院/专业解析（专业 > 学院 > 全局 > yml 兜底）
    @Resource
    private SimilarityThresholdResolver similarityThresholdResolver;

    // P5 学院月度查重额度：新建任务落库前校验并扣减
    @Resource
    private CollegeQuotaService collegeQuotaService;
    
    private final Tika tika = new Tika();
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<CheckResultVO> createEnhancedCheckTask(Long paperId, List<String> engineTypes) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        
        // 1. 校验论文
        PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
        if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }
        
        // 2. 解析引擎类型
        List<CheckEngineTypeEnum> engines = parseEngineTypes(engineTypes);

        // P5 学院月度额度：任务真正落库前校验并扣减学院当月额度。
        // 重试路径复用已存在任务，不走本新建入口，不会重复扣减。
        collegeQuotaService.checkAndConsume(paperInfo.getCollegeId());

        // 3. 创建任务
        CheckTask checkTask = new CheckTask();
        checkTask.setPaperId(paperId);
        checkTask.setTaskNo(generateTaskNo());
        checkTask.setCheckStatus(CheckTaskStatusEnum.PENDING.getCode());
        // M3.1 版本—报告绑定：快照论文版本 + 最新一次提交记录
        checkTask.setPaperVersion(paperInfo.getVersion());
        checkTask.setSubmitId(resolveLatestSubmitId(paperId));
        UserBusinessInfoUtils.setAuditField(checkTask, true);
        save(checkTask);
        
        // 4. 更新论文状态
        paperInfo.setPaperStatus(com.abin.checkrepeatservice.common.constant.DictConstants.PaperStatus.CHECKING);
        paperInfo.setCheckTime(LocalDateTime.now());
        paperInfoMapper.updateById(paperInfo);
        
        // 5. 事务提交后异步执行查重（事件驱动，修复 @Async 自调用失效）
        eventPublisher.publishEvent(new EnhancedCheckTaskExecuteEvent(checkTask.getId(), engines));
        
        // 6. 返回结果
        CheckResultVO resultVO = buildCheckResultVO(checkTask);
        return Result.success("查重任务创建成功", resultVO);
    }
    
    @Override
    public Result<List<CheckTaskResultDTO>> getCheckTaskList(Long paperId, String status, 
                                                           Integer pageNum, Integer pageSize) {
        Page<CheckTask> page = new Page<>(pageNum, pageSize);
        
        LambdaQueryWrapper<CheckTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CheckTask::getIsDeleted, 0)
               .orderByDesc(CheckTask::getCreateTime);
        
        // 权限过滤
        applyPermissionFilter(wrapper);
        
        // 条件过滤
        if (paperId != null) {
            wrapper.eq(CheckTask::getPaperId, paperId);
        }
        if (status != null) {
            wrapper.eq(CheckTask::getCheckStatus, status);
        }
        
        Page<CheckTask> resultPage = page(page, wrapper);
        List<CheckTaskResultDTO> dtos = resultPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        return Result.success(dtos);
    }
    
    @Override
    public Result<CheckTaskResultDTO> getCheckTaskDetail(Long taskId) {
        CheckTask task = getById(taskId);
        if (task == null || task.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        
        // 权限校验
        if (!hasTaskAccess(task)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限访问该任务");
        }
        
        CheckTaskResultDTO dto = convertToDTO(task);
        return Result.success(dto);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> cancelCheckTask(Long taskId) {
        CheckTask task = getById(taskId);
        if (task == null || task.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        
        CheckTaskStatusEnum currentStatus = CheckTaskStatusEnum.fromCode(task.getCheckStatus());
        if (!currentStatus.canBeCancelled()) {
            return Result.error(ResultCode.PERMISSION_NOT_STATUS, 
                              "当前状态不允许取消：" + currentStatus.getDescription());
        }
        
        if (!hasTaskAccess(task)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限取消该任务");
        }
        
        boolean success = updateTaskStatus(taskId, CheckTaskStatusEnum.CANCELLED, "用户主动取消");
        return success ? Result.success("任务取消成功") : Result.error(ResultCode.SYSTEM_ERROR,"任务取消失败");
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> retryCheckTask(Long taskId) {
        CheckTask task = getById(taskId);
        if (task == null || task.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        
        CheckTaskStatusEnum currentStatus = CheckTaskStatusEnum.fromCode(task.getCheckStatus());
        if (currentStatus != CheckTaskStatusEnum.FAILURE) {
            return Result.error(ResultCode.PERMISSION_NOT_STATUS, "只有失败的任务才能重试");
        }
        
        // 重置任务状态
        task.setCheckStatus(CheckTaskStatusEnum.PENDING.getCode());
        task.setFailReason(null);
        task.setStartTime(null);
        task.setEndTime(null);
        task.setCheckRate(null);
        updateById(task);
        
        // 异步重新执行 - 使用Collections.singletonList
        // 架构修复（P0）：事务提交后异步重试，不再自调用本类 @Async 方法
        eventPublisher.publishEvent(new EnhancedCheckTaskExecuteEvent(
                taskId, Collections.singletonList(CheckEngineTypeEnum.LOCAL)));
        
        return Result.success("任务重试已启动");
    }
    
    @Override
    public Result<Object> getTaskStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 各状态任务数量统计
        Map<String, Long> statusCount = new HashMap<>();
        for (CheckTaskStatusEnum status : CheckTaskStatusEnum.values()) {
            LambdaQueryWrapper<CheckTask> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(CheckTask::getCheckStatus, status.getCode())
                   .eq(CheckTask::getIsDeleted, 0);
            applyPermissionFilter(wrapper);
            statusCount.put(status.getDescription(), count(wrapper));
        }
        stats.put("statusCount", statusCount);
        
        // 今日任务统计
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LambdaQueryWrapper<CheckTask> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(CheckTask::getCreateTime, todayStart)
                   .eq(CheckTask::getIsDeleted, 0);
        applyPermissionFilter(todayWrapper);
        stats.put("todayTaskCount", count(todayWrapper));
        
        return Result.success(stats);
    }
    
    @Override
    public boolean updateTaskStatus(Long taskId, CheckTaskStatusEnum newStatus, String reason) {
        CheckTask task = getById(taskId);
        if (task == null) return false;
        
        CheckTaskStatusEnum currentStatus = CheckTaskStatusEnum.fromCode(task.getCheckStatus());
        if (!currentStatus.canTransitionTo(newStatus)) {
            log.warn("非法状态流转：{} -> {}", currentStatus.getDescription(), newStatus.getDescription());
            return false;
        }
        
        task.setCheckStatus(newStatus.getCode());
        if (newStatus.isFinalStatus()) {
            task.setEndTime(LocalDateTime.now());
        }
        // M1.2：失败状态必须把原因一并落库，否则前端只能看到 failure 而看不到为何失败
        if (newStatus == CheckTaskStatusEnum.FAILURE) {
            task.setFailReason(reason == null ? "" : (reason.length() > 500 ? reason.substring(0, 500) : reason));
        }
        updateById(task);
        
        log.info("任务状态更新：ID={}, {} -> {}, 原因={}", 
                taskId, currentStatus.getDescription(), newStatus.getDescription(), reason);
        return true;
    }
    
    @Override
    public Result<Object> getTaskProgress(Long taskId) {
        CheckTask task = getById(taskId);
        if (task == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        
        Map<String, Object> progress = new HashMap<>();
        progress.put("taskId", taskId);
        progress.put("status", task.getCheckStatus());
        progress.put("statusDescription", CheckTaskStatusEnum.fromCode(task.getCheckStatus()).getDescription());
        
        if (task.getStartTime() != null && task.getEndTime() != null) {
            long duration = Duration.between(task.getStartTime(), task.getEndTime()).getSeconds();
            progress.put("duration", duration + "秒");
        }
        
        return Result.success(progress);
    }
    
    // ------------------------------ 任务执行（由 EnhancedCheckTaskExecuteListener 异步调用） ------------------------------

    /**
     * 执行查重任务。架构修复（P0）：不再标注 @Async，由
     * {@link com.abin.checkrepeatservice.student.listener.EnhancedCheckTaskExecuteListener}
     * 在任务创建/重试事务 AFTER_COMMIT 后经独立 bean 异步调用，保证代理生效、数据可见。
     */
    public void doExecuteCheckTask(Long taskId, List<CheckEngineTypeEnum> engineTypes) {
        CheckTask task = getById(taskId);
        if (task == null) return;
        
        // 更新状态为执行中
        if (!updateTaskStatus(taskId, CheckTaskStatusEnum.CHECKING, "开始执行查重")) {
            return;
        }
        
        try {
            PaperInfo paperInfo = paperInfoMapper.selectById(task.getPaperId());
            if (paperInfo == null || paperInfo.getFilePath() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "论文文件不存在");
            }
            
            // 提取文本
            String paperText = extractTextFromFile(paperInfo.getFilePath());
            task.setStartTime(LocalDateTime.now());
            updateById(task);

            // 架构修复（P0）：正文入本地比对库；引擎比对时排除论文自身
            paperLibraryIngestService.ingest(task.getPaperId(), paperText);

            // 执行查重
            com.abin.checkrepeatservice.pojo.vo.CheckResult checkResult =
                    checkEngineManager.executeCheck(paperText, paperInfo.getPaperTitle(), engineTypes,
                            task.getPaperId());
            
            // 生成报告
            CheckReport report = generateCheckReport(task, checkResult);
            
            // 更新任务状态
            task.setCheckRate(checkResult.getSimilarity());
            task.setReportId(report.getId());
            task.setReportPath(report.getReportPath());
            
            CheckTaskStatusEnum finalStatus = checkResult.isSuccess() ? 
                    CheckTaskStatusEnum.COMPLETED : CheckTaskStatusEnum.FAILURE;
            updateTaskStatus(taskId, finalStatus, checkResult.getFailReason());
            
            // 更新论文状态
            updatePaperStatus(paperInfo, checkResult.getSimilarity());
            
        } catch (Exception e) {
            log.error("查重任务执行失败：taskId={}", taskId, e);
            // M1.2：引擎全失败/比对故障落 failure + fail_reason（updateTaskStatus 负责写 fail_reason）
            String failReason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            updateTaskStatus(taskId, CheckTaskStatusEnum.FAILURE, failReason);
            
            // 恢复论文状态
            PaperInfo paperInfo = paperInfoMapper.selectById(task.getPaperId());
            if (paperInfo != null) {
                paperInfo.setPaperStatus(com.abin.checkrepeatservice.common.constant.DictConstants.PaperStatus.PENDING);
                paperInfo.setCheckTime(null);
                paperInfoMapper.updateById(paperInfo);
            }
        }
    }
    
    // ------------------------------ 私有辅助方法 ------------------------------
    
    private List<CheckEngineTypeEnum> parseEngineTypes(List<String> engineCodes) {
        if (engineCodes == null || engineCodes.isEmpty()) {
            return Arrays.asList(CheckEngineTypeEnum.LOCAL);
        }
        
        return engineCodes.stream()
                .map(CheckEngineTypeEnum::fromCode)
                .collect(Collectors.toList());
    }
    
    /**
     * M3.1：取该论文最新一次提交记录 ID（paper_submit.id）。
     * 无提交记录时返回 null（历史数据/未走提交流程的论文），不阻断任务创建。
     */
    private Long resolveLatestSubmitId(Long paperId) {
        PaperSubmit latest = paperSubmitMapper.selectOne(
                new LambdaQueryWrapper<PaperSubmit>()
                        .eq(PaperSubmit::getPaperId, paperId)
                        .orderByDesc(PaperSubmit::getSubmitVersion)
                        .last("LIMIT 1")
        );
        return latest != null ? latest.getId() : null;
    }

    private String generateTaskNo() {
        String datePrefix = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seqPrefix = "CHECK" + datePrefix;
        
        LambdaQueryWrapper<CheckTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(CheckTask::getTaskNo, seqPrefix)
               .eq(CheckTask::getIsDeleted, 0)
               .select(CheckTask::getTaskNo)
               .orderByDesc(CheckTask::getTaskNo)
               .last("LIMIT 1");
        
        CheckTask lastTask = getOne(wrapper);
        int seq = 1;
        if (lastTask != null) {
            String lastNo = lastTask.getTaskNo();
            seq = Integer.parseInt(lastNo.substring(lastNo.length() - 3)) + 1;
        }
        
        return seqPrefix + String.format("%03d", seq);
    }
    
    private String extractTextFromFile(String filePath) throws IOException, TikaException {
        String fullPath = basePath + filePath;
        File file = new File(fullPath);
        if (!file.exists()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件不存在：" + filePath);
        }
        
        try (InputStream inputStream = new FileInputStream(file)) {
            return tika.parseToString(inputStream);
        }
    }
    
    private CheckReport generateCheckReport(CheckTask task, com.abin.checkrepeatservice.pojo.vo.CheckResult checkResult) {
        String reportNo = "REPORT" + task.getTaskNo().substring(5);
        String reportPath = "/reports/" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                          + "/" + reportNo + ".pdf";
        
        CheckReport report = new CheckReport();
        report.setTaskId(task.getId());
        report.setReportNo(reportNo);
        // repeat_details 为 NOT NULL 无默认值列，必填；口径与 CheckTaskEventListener 落库保持一致
        java.util.Map<String, Object> detail = new java.util.HashMap<>();
        detail.put("similarity", checkResult.getSimilarity() != null ? checkResult.getSimilarity().doubleValue() : 0.0d);
        detail.put("source", "论文信息库（SimHash+ 余弦相似度）");
        detail.put("reportUrl", reportPath);
        detail.put("extraInfo", checkResult.getExtraInfo());
        report.setRepeatDetails(com.alibaba.fastjson2.JSON.toJSONString(java.util.Collections.singletonList(detail)));
        report.setReportPath(reportPath);
        report.setReportType("pdf");
        // 异步线程无登录上下文，调 setAuditField 必抛未登录异常；
        // createBy 继承任务创建人，时间/isDeleted 由 MyMetaObjectHandler 兜底填充
        report.setCreateBy(task.getCreateBy());
        checkReportMapper.insert(report);
        
        // 生成PDF报告
        generatePdfReport(task, report, checkResult);
        
        return report;
    }
    
    private void generatePdfReport(CheckTask task, CheckReport report, com.abin.checkrepeatservice.pojo.vo.CheckResult checkResult) {
        try {
            ReportPreviewDTO previewDTO = buildReportPreviewDTO(task, report, checkResult);
            pdfReportGenerator.generatePdfToFile(previewDTO, report.getReportPath());
        } catch (Exception e) {
            log.error("PDF报告生成失败：taskId={}", task.getId(), e);
        }
    }
    
    private ReportPreviewDTO buildReportPreviewDTO(CheckTask task, CheckReport report, 
                                                 com.abin.checkrepeatservice.pojo.vo.CheckResult checkResult) {
        ReportPreviewDTO dto = new ReportPreviewDTO();
        ReportPreviewDTO.ReportBaseInfoDTO baseInfo = new ReportPreviewDTO.ReportBaseInfoDTO();
        
        baseInfo.setTaskId(task.getId());
        baseInfo.setTaskNo(task.getTaskNo());
        // M3.1 版本—报告绑定：报告基础信息带出任务快照的论文版本
        baseInfo.setPaperVersion(task.getPaperVersion());
        baseInfo.setReportId(report.getId());
        baseInfo.setReportNo(report.getReportNo());
        baseInfo.setSimilarityRate(checkResult.getSimilarity());
        baseInfo.setCheckTime(LocalDateTime.now());
        
        PaperInfo paperInfo = paperInfoMapper.selectById(task.getPaperId());
        if (paperInfo != null) {
            baseInfo.setPaperTitle(paperInfo.getPaperTitle());
            baseInfo.setAuthor(paperInfo.getAuthor());
        }
        
        dto.setBaseInfo(baseInfo);
        return dto;
    }
    
    private void updatePaperStatus(PaperInfo paperInfo, BigDecimal similarity) {
        BigDecimal threshold = similarityThresholdResolver.resolveForPaper(paperInfo);
        String newStatus = similarity.compareTo(threshold) <= 0 ?
                com.abin.checkrepeatservice.common.constant.DictConstants.PaperStatus.AUDITING :
                com.abin.checkrepeatservice.common.constant.DictConstants.PaperStatus.REJECTED;
        
        paperInfo.setPaperStatus(newStatus);
        paperInfoMapper.updateById(paperInfo);
    }
    
    private CheckResultVO buildCheckResultVO(CheckTask task) {
        CheckResultVO vo = new CheckResultVO();
        vo.setTaskId(task.getId());
        vo.setPaperId(task.getPaperId());
        vo.setCheckStatus(task.getCheckStatus());
        vo.setCreateTime(task.getCreateTime());
        return vo;
    }
    
    private CheckTaskResultDTO convertToDTO(CheckTask task) {
        CheckTaskResultDTO dto = new CheckTaskResultDTO();
        dto.setTaskId(task.getId());
        dto.setTaskNo(task.getTaskNo());
        dto.setPaperId(task.getPaperId());
        // M3.1 版本—报告绑定：任务上快照的版本与提交记录（存量任务为 null）
        dto.setPaperVersion(task.getPaperVersion());
        dto.setSubmitId(task.getSubmitId());
        dto.setCheckStatus(task.getCheckStatus());
        dto.setCheckRate(task.getCheckRate() != null ? task.getCheckRate().doubleValue() : null);
        // P5 新指标：存量任务为 null，前端按「—」展示
        dto.setMaxPerSource(task.getMaxPerSource() != null ? task.getMaxPerSource().doubleValue() : null);
        dto.setCitationRate(task.getCitationRate() != null ? task.getCitationRate().doubleValue() : null);
        dto.setStartTime(task.getStartTime());
        dto.setEndTime(task.getEndTime());
        dto.setFailReason(task.getFailReason());
        
        // 补充论文信息
        PaperInfo paperInfo = paperInfoMapper.selectById(task.getPaperId());
        if (paperInfo != null) {
            dto.setPaperTitle(paperInfo.getPaperTitle());
        }
        
        return dto;
    }
    
    private void applyPermissionFilter(LambdaQueryWrapper<CheckTask> wrapper) {
        // S11 安全加固：inSql 字符串拼接改为 apply {0} 参数绑定（统一模式，防复制扩散）
        if (UserBusinessInfoUtils.isStudent()) {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            wrapper.apply("paper_id IN (SELECT id FROM paper_info WHERE student_id = {0} AND is_deleted = 0)", studentId);
        } else if (UserBusinessInfoUtils.isTeacher()) {
            Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
            wrapper.apply("paper_id IN (SELECT id FROM paper_info WHERE teacher_id = {0} AND is_deleted = 0)", teacherId);
        }
        // 管理员无过滤
    }
    
    private boolean hasTaskAccess(CheckTask task) {
        if (UserBusinessInfoUtils.isAdmin()) return true;
        
        PaperInfo paperInfo = paperInfoMapper.selectById(task.getPaperId());
        if (paperInfo == null) return false;
        
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        if (UserBusinessInfoUtils.isStudent()) {
            return paperInfo.getStudentId().equals(currentUserId);
        } else if (UserBusinessInfoUtils.isTeacher()) {
            return paperInfo.getTeacherId().equals(currentUserId);
        }
        
        return false;
    }
}