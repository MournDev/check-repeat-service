package com.abin.checkrepeatservice.student.service.Impl;


import com.abin.checkrepeatservice.admin.mapper.CheckResultMapper;
import com.abin.checkrepeatservice.admin.mapper.CompareLibMapper;
import com.abin.checkrepeatservice.user.mapper.PaperStatusLogMapper;
import cn.hutool.core.date.DateTime;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.engine.CheckEngineManager;
import com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum;
import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.statemachine.CheckTaskStateMachine;
import com.abin.checkrepeatservice.common.utils.PdfReportGenerator;
import com.abin.checkrepeatservice.common.utils.TextSimilarityUtils;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.student.dto.CheckTaskResultDTO;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.abin.checkrepeatservice.student.event.CheckTaskCreatedEvent;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.CheckTaskService;
import com.abin.checkrepeatservice.student.service.CheckTaskValidationService;
import com.abin.checkrepeatservice.user.vo.CheckResultVO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.SAXException;

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
 * 查重任务服务实现类
 */
@Slf4j
@Service
public class CheckTaskServiceImpl extends ServiceImpl<CheckTaskMapper, CheckTask> implements CheckTaskService {

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private CheckReportMapper checkReportMapper;

    @Resource
    private PdfReportGenerator pdfReportGenerator;

    @Resource
    private CheckResultMapper checkResultMapper;

    @Resource
    private CompareLibMapper compareLibMapper;

    @Resource
    private PaperStatusLogMapper paperStatusLogMapper;

    @Resource
    private TextSimilarityUtils textSimilarityUtils;

    @Resource
    private CheckTaskStateMachine stateMachine;

    @Resource
    private CheckEngineManager checkEngineManager;

    @Value("${file.upload.base-path}")
    private String basePath;

    @Value("${admin.check-rule.default-max-count}")
    private int defaultMaxCount;

    @Value("${admin.check-rule.default-interval}")
    private Long checkInterval;

    // 最大并发任务数（从配置文件获取）
    @Value("${check.task.max-concurrent}")
    private int maxConcurrentTasks;

    // 任务超时时间（从配置文件获取）
    @Value("${check.task.timeout}")
    private long taskTimeout;

    @Value("${admin.check-rule.default-threshold}")
    private BigDecimal defaultThreshold;

    @Resource
    private CheckTaskValidationService validationService;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    // Apache Tika：提取文件文本内容
    private final Tika tika = new Tika();

    @Override
    public Result<CheckResultVO> createCheckTask(Long paperId) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        
        // 前置校验（在事务外执行）
        log.info("开始创建查重任务 - 论文 ID: {}, 用户 ID: {}", paperId, currentUserId);
        CheckTaskValidationService.ValidationResult validationResult = 
            validationService.validateCheckRequest(paperId, currentUserId);
        
        if (!validationResult.isSuccess()) {
            log.warn("查重前置校验失败 - 论文 ID: {}, 原因：{}", paperId, validationResult.getMessage());
            return Result.error(ResultCode.BUSINESS_NO_REPEAT, validationResult.getMessage());
        }
        
        PaperInfo paperInfo = validationResult.getPaper();
        
        // 小事务：只包含数据库操作
        try {
            return createCheckTaskInternal(paperId, paperInfo);
        } catch (Exception e) {
            log.error("创建查重任务失败 - 论文 ID: {}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查重任务创建失败：" + e.getMessage());
        }
    }
    
    /**
     * 内部方法：在小事务中创建查重任务
     */
    @Transactional(rollbackFor = Exception.class)
    protected Result<CheckResultVO> createCheckTaskInternal(Long paperId, PaperInfo paperInfo) {
        // 1. 创建 PENDING 状态的任务（不立即改为 CHECKING）
        CheckTask checkTask = new CheckTask();
        checkTask.setPaperId(paperId);
        checkTask.setFileId(paperInfo.getFileId());
        checkTask.setTaskNo(generateTaskNo());
        checkTask.setCheckStatus(DictConstants.CheckStatus.PENDING); // 保持待处理状态
        UserBusinessInfoUtils.setAuditField(checkTask, true);
        save(checkTask);

        // 2. 发布事件，完全解耦（异步执行查重）
        eventPublisher.publishEvent(new CheckTaskCreatedEvent(this, checkTask.getId(), paperId));

        // 3. 立即返回，提供预估信息
        CheckResultVO checkResultVO = buildCheckResultVO(checkTask);
        
        log.info("查重任务创建成功 - 任务 ID: {}, 论文 ID: {}, 预估时长：{}秒", 
                checkTask.getId(), paperId, estimateDuration(paperInfo.getWordCount()));
        
        return Result.success("查重任务创建成功，预计" + estimateDuration(paperInfo.getWordCount()) + 
                            "秒内完成（当前排队位置：#" + getQueuePosition() + "）", checkResultVO);
    }
    
    /**
     * 预估查重时长（基于字数）
     */
    private int estimateDuration(Integer wordCount) {
        if (wordCount == null || wordCount == 0) {
            return 60; // 默认 60 秒
        }
        // 每 1000 字约需 5 秒
        return Math.max(30, (int)(wordCount / 1000.0 * 5));
    }
    
    /**
     * 获取当前排队位置
     */
    private int getQueuePosition() {
        return (int)count(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.PENDING)
                .eq(CheckTask::getIsDeleted, 0)
        ) + 1;
    }
    
    /**
     * 构建返回结果 VO
     */
    private CheckResultVO buildCheckResultVO(CheckTask checkTask) {
        CheckResultVO vo = new CheckResultVO();
        vo.setTaskId(checkTask.getId());
        vo.setPaperId(checkTask.getPaperId());
        vo.setCheckStatus(checkTask.getCheckStatus());
        vo.setCreateTime(checkTask.getCreateTime());
        return vo;
    }

    @Override
    public Result<List<CheckTaskResultDTO>> getMyCheckTaskList(Long paperId, Integer checkStatus) {
        SysUser currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        LambdaQueryWrapper<CheckTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CheckTask::getIsDeleted, 0)
                .orderByDesc(CheckTask::getCreateTime);

        // 1. 按角色过滤数据
        if (UserBusinessInfoUtils.isStudent()) {
            // 学生：仅查询自己论文的任务（需关联论文表的student_id）
            queryWrapper.inSql(CheckTask::getPaperId,
                    "SELECT id FROM paper_info WHERE student_id = " + currentUser.getId() + " AND is_deleted = 0");
        } else if (UserBusinessInfoUtils.isTeacher()) {
            // 教师：仅查询自己指导论文的任务（需关联论文表的teacher_id）
            queryWrapper.inSql(CheckTask::getPaperId,
                    "SELECT id FROM paper_info WHERE teacher_id = " + currentUser.getId() + " AND is_deleted = 0");
        }
        // 管理员：无过滤

        // 2. 按论文ID过滤（可选）
        if (paperId != null) {
            queryWrapper.eq(CheckTask::getPaperId, paperId);
        }

        // 3. 按任务状态过滤（可选）
        if (checkStatus != null && (checkStatus >= 0 && checkStatus <= 3)) {
            queryWrapper.eq(CheckTask::getCheckStatus, checkStatus);
        }

        // 4. 查询任务并转换为DTO（含报告摘要）
        List<CheckTask> taskList = list(queryWrapper);
        List<CheckTaskResultDTO> resultDTOList = taskList.stream()
                .map(this::convertToTaskResultDTO)
                .collect(Collectors.toList());

        return Result.success("查重任务列表查询成功", resultDTOList);
    }

    @Override
    public Result<CheckTaskResultDTO> getCheckTaskDetail(Long paperId) {
        // 1. 查询任务信息
        LambdaQueryWrapper<CheckTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CheckTask::getPaperId, paperId)
                .eq(CheckTask::getIsDeleted, 0)
                .orderByDesc(CheckTask::getCreateTime);
        List<CheckTask> checkTasks = list(queryWrapper);
        CheckTask checkTask = checkTasks.isEmpty() ? null : checkTasks.get(0); // 替代 getFirst()
        if (checkTask == null || checkTask.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "查重任务不存在或已删除");
        }

        // 2. 权限校验（学生查自己的，教师查指导的，管理员查所有）
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        SysUser currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        boolean isStudentOwner = paperInfo.getStudentId().equals(currentUser.getId());
        boolean isAdmin = UserBusinessInfoUtils.isAdmin();
        if (!isStudentOwner && !isAdmin) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限查看该查重任务");
        }
        // 3. 转换为DTO（含完整报告信息）
        CheckTaskResultDTO resultDTO = convertToTaskResultDTO(checkTask, true);
        return Result.success("查重任务详情查询成功", resultDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> cancelCheckTask(Long taskId) {
        // 1. 查询任务信息
        CheckTask checkTask = getById(taskId);
        if (checkTask == null || checkTask.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "查重任务不存在或已删除");
        }

        // 2. 状态校验（仅“待执行”状态可取消）
        if (!checkTask.getCheckStatus().equals(DictConstants.CheckStatus.PENDING)) {
            return Result.error(ResultCode.PERMISSION_NOT_STATUS, "仅“待执行”状态的任务可取消");
        }

        // 3. 权限校验
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        SysUser currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        boolean isStudentOwner = paperInfo.getStudentId().equals(currentUser.getId());
        boolean isAdmin = UserBusinessInfoUtils.isAdmin();
        if (!isStudentOwner && !isAdmin) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限取消该任务");
        }

        // 4. 更新任务状态为“已取消”（自定义状态：4-已取消，需在枚举中补充）
        checkTask.setCheckStatus(DictConstants.CheckStatus.CANCELLED);
        checkTask.setFailReason("用户主动取消任务");
        UserBusinessInfoUtils.setAuditField(checkTask, false);
        updateById(checkTask);

       // 5. 恢复论文状态为"已分配"（论文已通过导师分配阶段，取消查重后回到待查重的已分配状态）
        PaperInfo updatePaper = new PaperInfo();
        updatePaper.setId(paperInfo.getId());
        updatePaper.setPaperStatus(DictConstants.PaperStatus.ASSIGNED); // 已分配，等待重新发起查重
        paperInfoMapper.updateById(updatePaper);
       
        return Result.success("查重任务取消成功");
    }

    @Override
    public CheckResultVO getCheckResult(Long paperId) {
        // 1. Find the latest completed check task for the given paper
        LambdaQueryWrapper<CheckTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CheckTask::getPaperId, paperId)
                .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                .eq(CheckTask::getIsDeleted, 0)
                .orderByDesc(CheckTask::getCreateTime)
                .last("LIMIT 1");

        CheckTask checkTask = getOne(queryWrapper);

        if (checkTask == null) {
            return null; // No completed check task found
        }

        // 2. Convert CheckTask to CheckResultVO
        CheckResultVO resultVO = new CheckResultVO();
        resultVO.setTaskId(checkTask.getId());
        resultVO.setPaperId(checkTask.getPaperId());
        resultVO.setCheckStatus(checkTask.getCheckStatus());
        resultVO.setCreateTime(checkTask.getCreateTime());

        // 3. Add report information if available
        if (checkTask.getReportId() != null) {
            CheckReport checkReport = checkReportMapper.selectById(checkTask.getReportId());
            if (checkReport != null) {
                resultVO.setReportId(checkReport.getId());
                resultVO.setReportNo(checkReport.getReportNo());
                resultVO.setReportPath(checkReport.getReportPath());
            }
        }

        return resultVO;
    }


    /**
     * 异步执行查重任务（@Async 需在启动类添加@EnableAsync）
     * 关键改进：
     * 1. 先更新任务状态为 CHECKING
     * 2. 再更新论文状态为 CHECKING
     * 3. 失败时回滚论文状态
     */
    @Async
    public void executeCheckTaskAsync(Long taskId, Long paperId) {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("开始异步执行查重任务 - 任务 ID: {}, 论文 ID: {}", taskId, paperId);
            
        CheckTask checkTask = getById(taskId);
        if (checkTask == null || checkTask.getIsDeleted() == 1) {
            log.error("查重任务不存在 - 任务 ID: {}", taskId);
            return;
        }
            
        PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
        if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
            log.error("论文不存在 - 论文 ID: {}", paperId);
            updateTaskStatus(taskId, DictConstants.CheckStatus.FAILURE, "论文不存在");
            return;
        }
        
        try {
            // 【关键改进 1】先更新任务状态为"执行中"
            boolean transitionSuccess = stateMachine.transitionStatus(
                taskId, 
                CheckTaskStatusEnum.CHECKING,
                "开始执行查重任务"
            );
                    
            if (!transitionSuccess) {
                log.warn("状态转换失败，任务 ID: {}", taskId);
                return;
            }
        
            // 【关键改进 2】校验任务超时（防止任务无限执行）
            if (Duration.between(startTime, LocalDateTime.now()).toMillis() > taskTimeout) {
                throw new BusinessException(ResultCode.SYSTEM_TIMEOUT,
                    "查重任务执行超时（超过" + taskTimeout / 1000 + "秒）");
            }
                
            if (paperInfo.getFilePath() == null || paperInfo.getFilePath().trim().isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "论文文件路径未设置");
            }
                    
            // 3. 提取论文文本内容（从文件路径读取）
            String paperText = extractTextFromFile(paperInfo.getFilePath());
            if (paperText.trim().isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_TYPE_ERROR,
                    "论文文本提取失败，文件可能为空或格式不支持");
            }
        
            // 4. 使用查重引擎执行真实查重（支持多引擎并行检测）
            log.info("开始执行查重比对 - 任务 ID: {}, 论文 ID: {}", taskId, paperInfo.getId());
                
            // 执行查重（默认使用 LOCAL 本地引擎）
            com.abin.checkrepeatservice.pojo.vo.CheckResult checkResult = 
                checkEngineManager.executeCheck(
                    paperText, 
                    paperInfo.getPaperTitle(),
                    Arrays.asList(com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum.LOCAL)
                );
                
            if (!checkResult.isSuccess()) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, 
                    "查重引擎执行失败：" + checkResult.getFailReason());
            }
                
            double maxSimilarity = checkResult.getSimilarity().doubleValue();
            List<Map<String, Object>> repeatDetails = parseRepeatDetailsFromCheckResult(checkResult);
        
            // 6. 生成查重报告（结构化数据+PDF 文件）
            CheckReport checkReport = generateCheckReport(checkTask, maxSimilarity, repeatDetails);
        
            // 7. 使用状态机更新任务状态为"执行成功"
            stateMachine.transitionStatus(
                taskId, 
                CheckTaskStatusEnum.COMPLETED, 
                "查重任务执行成功"
            );
        
            // 8. 更新任务相关信息
            checkTask.setCheckRate(BigDecimal.valueOf(maxSimilarity));
            checkTask.setEndTime(LocalDateTime.now());
            checkTask.setReportId(checkReport.getId());
            updateById(checkTask);
    
            // 【关键改进 3】成功后再更新论文状态
            updatePaperSuccess(paperInfo, maxSimilarity);
    
            // 9. 向 check_result 表写入详细查重结果记录
            com.abin.checkrepeatservice.pojo.entity.CheckResult dbCheckResult = 
                new com.abin.checkrepeatservice.pojo.entity.CheckResult();
            dbCheckResult.setTaskId(checkTask.getId());
            dbCheckResult.setPaperId(checkTask.getPaperId());
            dbCheckResult.setRepeatRate(BigDecimal.valueOf(maxSimilarity));
            dbCheckResult.setCheckSource("LOCAL");
            dbCheckResult.setCheckTime(LocalDateTime.now());
            dbCheckResult.setWordCount(paperInfo.getWordCount());
            dbCheckResult.setStatus(1);
            if (!repeatDetails.isEmpty()) {
                Map<String, Object> topDetail = repeatDetails.get(0);
                if (topDetail.get("source") != null) {
                    dbCheckResult.setMostSimilarPaper(topDetail.get("source").toString());
                }
            }
            UserBusinessInfoUtils.setAuditField(dbCheckResult, true);
            checkResultMapper.insert(dbCheckResult);
    
            // 10. 记录论文状态变更日志
            recordPaperStatusLog(paperInfo.getId(), 
                DictConstants.PaperStatus.CHECKING, 
                getFinalPaperStatus(maxSimilarity), 
                "查重完成，根据重复率自动更新状态");
        
            log.info("查重任务执行成功 - 任务 ID: {}, 相似度：{}%", taskId, maxSimilarity);
        
        } catch (Exception e) {
            log.error("查重任务执行失败（任务 ID：{}）", taskId, e);
                    
            // 异常处理：使用状态机更新任务状态为"执行失败"
            stateMachine.transitionStatus(
                taskId, 
                CheckTaskStatusEnum.FAILURE, 
                e.getMessage().length() > 500 ? e.getMessage().substring(0, 500) : e.getMessage()
            );
        
            // 【关键改进 4】失败时回滚论文状态
            PaperInfo updatePaper = new PaperInfo();
            updatePaper.setId(paperInfo.getId());
            updatePaper.setPaperStatus(DictConstants.PaperStatus.ASSIGNED);
            updatePaper.setCheckTime(null);
            paperInfoMapper.updateById(updatePaper);
    
            // 记录论文状态变更日志
            recordPaperStatusLog(paperInfo.getId(), 
                DictConstants.PaperStatus.CHECKING, 
                DictConstants.PaperStatus.ASSIGNED, 
                "查重任务执行失败，恢复至已分配状态");
                    
            log.warn("查重任务失败，已恢复论文状态 - 任务 ID: {}, 论文 ID: {}", taskId, paperId);
        }
    }
        
    /**
     * 更新论文状态为成功（独立事务）
     */
    private void updatePaperSuccess(PaperInfo paperInfo, double similarity) {
        boolean qualified = similarity <= defaultThreshold.doubleValue();
        PaperInfo updatePaper = new PaperInfo();
        updatePaper.setId(paperInfo.getId());
        updatePaper.setPaperStatus(qualified
                ? DictConstants.PaperStatus.AUDITING
                : DictConstants.PaperStatus.REJECTED);
        // 补充查重结果相关字段
        updatePaper.setSimilarityRate(BigDecimal.valueOf(similarity));
        updatePaper.setCheckCompleted(1);
        updatePaper.setCheckSource("LOCAL");
        updatePaper.setCheckEngineType("local");
        updatePaper.setCheckResult(qualified ? "合格" : "不合格");
        updatePaper.setCheckTime(LocalDateTime.now());
        paperInfoMapper.updateById(updatePaper);
            
        log.info("论文状态更新成功 - 论文 ID: {}, 相似度：{}%, 结果：{}", 
                paperInfo.getId(), similarity, qualified ? "合格" : "不合格");
    }
        
    /**
     * 获取最终论文状态
     */
    private String getFinalPaperStatus(double similarity) {
        return similarity <= defaultThreshold.doubleValue() 
            ? DictConstants.PaperStatus.AUDITING 
            : DictConstants.PaperStatus.REJECTED;
    }
        
    /**
     * 更新任务状态（辅助方法）
     */
    private void updateTaskStatus(Long taskId, String status, String failReason) {
        CheckTask updateTask = new CheckTask();
        updateTask.setId(taskId);
        updateTask.setCheckStatus(status);
        updateTask.setFailReason(failReason);
        updateById(updateTask);
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
            statusLog.setOldStatus(Integer.parseInt(oldStatus)); // 转换为整数（根据数据库定义）
            statusLog.setNewStatus(Integer.parseInt(newStatus));
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
     * 从查重结果中解析重复详情列表
     */
    private List<Map<String, Object>> parseRepeatDetailsFromCheckResult(com.abin.checkrepeatservice.pojo.vo.CheckResult checkResult) {
        List<Map<String, Object>> details = new ArrayList<>();
        
        try {
            // 从 CheckResult 的 extraInfo 或报告中提取详细信息
            String extraInfo = checkResult.getExtraInfo();
            if (extraInfo != null && !extraInfo.trim().isEmpty()) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("similarity", checkResult.getSimilarity().doubleValue());
                detail.put("source", "论文信息库（SimHash+ 余弦相似度）");
                detail.put("reportUrl", checkResult.getReportUrl());
                detail.put("extraInfo", extraInfo);
                details.add(detail);
            }
        } catch (Exception e) {
            log.error("解析查重结果详情失败", e);
        }
        
        return details;
    }
    
    /**
     * 生成唯一任务编号（格式：CHECK+ 年月日 +3 位序号，如 CHECK20251108001）
     */
    private String generateTaskNo() {
        String datePrefix = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seqPrefix = "CHECK" + datePrefix;
        // 查询当日最大序号
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
        // 序号补0（3位）
        return seqPrefix + String.format("%03d", seq);
    }

    /**
     * 从文件中提取文本内容（支持doc/docx/pdf）
     */
    private String extractTextFromFile(String filePath) throws IOException, TikaException, SAXException {
        String fullPath = basePath + filePath;
        File file = new File(fullPath);
        if (!file.exists() || !file.isFile()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,"论文文件不存在：" + filePath);
        }
        // 使用Tika提取文本（自动识别文件类型）
        try (InputStream inputStream = new FileInputStream(file)) {
            return tika.parseToString(inputStream);
        }
    }

    /**
     * 生成查重报告（含结构化数据与PDF文件）
     */
    private CheckReport generateCheckReport(CheckTask checkTask, double checkRate, List<Map<String, Object>> repeatDetails) {
        // 1. 生成报告编号（格式：REPORT+ 任务编号后缀，如 REPORT20251108001）
        String reportNo = "REPORT" + checkTask.getTaskNo().substring(5);
        
        // 2. 生成 PDF 报告文件路径（实际生产环境路径，由 pdfReportGenerator 生成真实 PDF 文件）
        String reportPath = "D:/data/report" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                + "/" + reportNo + ".pdf";

        // 3. 构建报告实体
        CheckReport checkReport = new CheckReport();
        checkReport.setTaskId(checkTask.getId());
        checkReport.setReportNo(reportNo);
        checkReport.setRepeatDetails(JSON.toJSONString(repeatDetails)); // 重复详情转JSON字符串
        checkReport.setReportPath(reportPath);
        checkReport.setReportType("pdf");
        UserBusinessInfoUtils.setAuditField(checkReport, true);
        checkReportMapper.insert(checkReport);

        // 4. 关键：实际生成PDF文件
        generateCheckReportPdf(checkTask, checkReport, checkRate, repeatDetails);
        return checkReport;
    }
    /**
     * 生成PDF报告文件
     */
    private void generateCheckReportPdf(CheckTask checkTask, CheckReport checkReport,
                                        double checkRate, List<Map<String, Object>> repeatDetails) {
        try {
            log.info("开始生成查重报告PDF - 任务ID: {}, 报告ID: {}", checkTask.getId(), checkReport.getId());

            // 构建报告预览数据
            ReportPreviewDTO previewDTO = buildReportPreviewDTO(checkTask, checkReport, checkRate, repeatDetails);

            // 调用PDF生成服务
            pdfReportGenerator.generatePdfToFile(previewDTO, checkReport.getReportPath());

            log.info("查重报告PDF生成成功 - 路径: {}", checkReport.getReportPath());

        } catch (Exception e) {
            log.error("查重报告PDF生成失败 - 任务ID: {}, 错误: ", checkTask.getId(), e);
            // 可以设置报告状态为生成失败，但不影响整体查重流程
            // 或者抛出异常让事务回滚
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "查重报告生成失败");
        }
    }
    /**
     * 构建报告预览DTO
     */
    private ReportPreviewDTO buildReportPreviewDTO(CheckTask checkTask, CheckReport checkReport,
                                                   double checkRate, List<Map<String, Object>> repeatDetails) {
        ReportPreviewDTO dto = new ReportPreviewDTO();
        ReportPreviewDTO.ReportBaseInfoDTO baseInfo = new ReportPreviewDTO.ReportBaseInfoDTO();
        // 设置查重任务信息
        baseInfo.setTaskId(checkTask.getId());
        baseInfo.setTaskNo(checkTask.getTaskNo());
        baseInfo.setReportId(checkReport.getId());
        baseInfo.setReportNo(checkReport.getReportNo());

        // 设置查重结果
        baseInfo.setSimilarityRate(BigDecimal.valueOf(checkRate));
        baseInfo.setCheckTime(LocalDateTime.now());
        baseInfo.setReportDetails(checkReport.getRepeatDetails());

        // 设置论文信息
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        if (paperInfo != null) {
            baseInfo.setPaperTitle(paperInfo.getPaperTitle());
            baseInfo.setAuthor(paperInfo.getAuthor());
            baseInfo.setStudentId(paperInfo.getStudentId());
        }

        // 设置用户信息
        SysUser currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        if (currentUser != null) {
            baseInfo.setUserName(currentUser.getUsername());
            baseInfo.setRealName(currentUser.getRealName());
        }
        // 将 baseInfo 设置到 dto
        dto.setBaseInfo(baseInfo);
        return dto;
    }

    /**
     * 转换CheckTask为CheckTaskResultDTO（含报告摘要）
     */
    private CheckTaskResultDTO convertToTaskResultDTO(CheckTask checkTask) {
        return convertToTaskResultDTO(checkTask, false);
    }

    /**
     * 转换CheckTask为CheckTaskResultDTO（支持是否返回完整报告）
     * @param withFullReport 是否返回完整报告信息
     */
    private CheckTaskResultDTO convertToTaskResultDTO(CheckTask checkTask, boolean withFullReport) {
        CheckTaskResultDTO dto = new CheckTaskResultDTO();
        dto.setTaskId(checkTask.getId());
        dto.setTaskNo(checkTask.getTaskNo());
        dto.setPaperId(checkTask.getPaperId());
        dto.setCheckStatus(checkTask.getCheckStatus());
        dto.setCheckRate(checkTask.getCheckRate() != null ? checkTask.getCheckRate().doubleValue() : null);
        dto.setStartTime(checkTask.getStartTime());
        dto.setEndTime(checkTask.getEndTime());
        dto.setFailReason(checkTask.getFailReason());

        // 补充论文标题
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        if (paperInfo != null) {
            dto.setPaperTitle(paperInfo.getPaperTitle());
        }

        // 补充报告摘要（仅任务成功且需要时）
        if (checkTask.getCheckStatus().equals(DictConstants.CheckStatus.COMPLETED) && checkTask.getReportId() != null && (withFullReport || true)) {
            CheckReport checkReport = checkReportMapper.selectById(checkTask.getReportId());
            if (checkReport != null) {
                CheckTaskResultDTO.CheckReportSummaryDTO reportSummary = new CheckTaskResultDTO.CheckReportSummaryDTO();
                reportSummary.setReportId(checkReport.getId());
                reportSummary.setReportNo(checkReport.getReportNo());
                // 解析重复段落数量（从JSON字符串中提取）
                List<Map<String, Object>> repeatDetails = JSON.parseObject(checkReport.getRepeatDetails(), new TypeReference<List<Map<String, Object>>>(){});
                reportSummary.setRepeatParagraphCount(repeatDetails.size());
                // 构建报告下载URL（前端拼接域名，如：/api/v1/student/reports/download?reportId=xxx）
                reportSummary.setReportDownloadUrl("/api/student/reports/download?reportId=" + checkReport.getId());
                dto.setReportSummary(reportSummary);
            }
        }

        return dto;
    }

    /**
     * 从数据库加载启用的比对库数据
     * @return 比对文本列表
     */
    private List<String> loadCompareLibraryData() {
        try {
            // 查询所有启用的本地比对库
            LambdaQueryWrapper<com.abin.checkrepeatservice.pojo.entity.CompareLib> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(com.abin.checkrepeatservice.pojo.entity.CompareLib::getIsEnabled, 1)
                    .eq(com.abin.checkrepeatservice.pojo.entity.CompareLib::getLibType, "LOCAL")
                    .eq(com.abin.checkrepeatservice.pojo.entity.CompareLib::getIsDeleted, 0);
            
            List<com.abin.checkrepeatservice.pojo.entity.CompareLib> compareLibs = compareLibMapper.selectList(wrapper);
            if (compareLibs.isEmpty()) {
                log.warn("未找到任何启用的本地比对库");
                return new ArrayList<>();
            }
            
            // 从每个比对库中加载文本内容
            List<String> allTexts = new ArrayList<>();
            for (com.abin.checkrepeatservice.pojo.entity.CompareLib lib : compareLibs) {
                try {
                    String libPath = lib.getLibUrl();
                    java.nio.file.Path path = java.nio.file.Paths.get(libPath);
                    
                    if (!java.nio.file.Files.exists(path)) {
                        log.warn("比对库路径不存在：{}", libPath);
                        continue;
                    }
                    
                    if (java.nio.file.Files.isDirectory(path)) {
                        // 如果是目录，递归读取所有 txt 文件
                        java.nio.file.Files.walk(path)
                                .filter(p -> p.toString().endsWith(".txt"))
                                .forEach(p -> {
                                    try {
                                        String content = java.nio.file.Files.readString(p);
                                        if (!content.trim().isEmpty()) {
                                            allTexts.add(content);
                                        }
                                    } catch (IOException e) {
                                        log.error("读取比对库文件失败：{}", p, e);
                                    }
                                });
                    } else {
                        // 如果是文件，直接读取
                        String content = java.nio.file.Files.readString(path);
                        if (!content.trim().isEmpty()) {
                            allTexts.add(content);
                        }
                    }
                    
                    log.info("从比对库 '{}' 加载了 {} 条文本", lib.getLibName(), allTexts.size());
                } catch (Exception e) {
                    log.error("加载比对库 '{}' 失败：{}", lib.getLibName(), e.getMessage());
                }
            }
            
            log.info("总共从比对库加载了 {} 条文本", allTexts.size());
            return allTexts;
        } catch (Exception e) {
            log.error("加载比对库数据失败", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 加载系统默认比对数据（当数据库中无比对库时使用）
     * @return 默认比对文本列表
     */
    private List<String> loadDefaultCompareLibrary() {
        List<String> data = new ArrayList<>();
        // 使用学术论文常用语句作为默认比对源
        data.add("Spring Boot是由Pivotal团队提供的全新框架，其设计目的是用来简化新Spring应用的初始搭建以及开发过程。该框架使用了特定的方式来进行配置，从而使开发人员不再需要定义样板化的配置。");
        data.add("基于Spring Boot的论文查重管理系统，通常采用前后端分离架构，后端使用Spring Boot框架实现RESTful API，前端使用Vue.js框架开发响应式界面。");
        data.add("文本相似度计算是论文查重系统的核心技术，常用的算法包括SimHash、余弦相似度、Jaccard相似度等，其中SimHash适合大规模文本快速去重，余弦相似度适合精细比对。");
        data.add("深度学习技术在自然语言处理领域取得了显著进展，特别是Transformer架构的提出，为文本理解、情感分析和机器翻译等任务带来了革命性的突破。");
        data.add("数据库设计是信息系统开发的基础，合理的数据库结构能够提高数据存储效率、减少冗余，并保证数据的一致性和完整性。");
        return data;
    }
}
