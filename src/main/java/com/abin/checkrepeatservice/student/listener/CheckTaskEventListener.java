package com.abin.checkrepeatservice.student.listener;

import com.abin.checkrepeatservice.admin.mapper.CheckResultMapper;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.statemachine.CheckTaskStateMachine;
import com.abin.checkrepeatservice.common.utils.RiskLevelUtils;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.notification.service.NotificationService;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.abin.checkrepeatservice.student.event.CheckProgressEvent;
import com.abin.checkrepeatservice.student.event.CheckTaskCreatedEvent;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.websocket.CheckProgressWebSocketHandler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 查重任务事件监听器
 * 异步处理查重任务的执行
 */
@Component
@Slf4j
public class CheckTaskEventListener {

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private CheckReportMapper checkReportMapper;

    @Resource
    private CheckTaskStateMachine stateMachine;

    @Resource
    private com.abin.checkrepeatservice.common.engine.CheckEngineManager checkEngineManager;

    @Resource
    private CheckResultMapper checkResultMapper;

    @Resource
    private com.abin.checkrepeatservice.common.utils.PdfReportGenerator pdfReportGenerator;

    @Resource
    private CheckProgressWebSocketHandler webSocketHandler;

    @Resource
    private NotificationService notificationService;

    @Value("${file.upload.base-path}")
    private String basePath;

    @Value("${admin.check-rule.default-threshold}")
    private BigDecimal defaultThreshold;

    @Value("${check.task.timeout:3600000}")
    private long taskTimeout;

    private final Tika tika = new Tika();

    /**
     * 监听查重任务创建事件，异步执行查重
     */
    @Async("checkTaskExecutor")
    @EventListener(CheckTaskCreatedEvent.class)
    public void handleCheckTaskCreated(CheckTaskCreatedEvent event) {
        Long taskId = event.getTaskId();
        Long paperId = event.getPaperId();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("接收到查重任务创建事件 - 任务 ID: {}, 论文 ID: {}", taskId, paperId);

        // 1. 先更新任务状态为 CHECKING
        boolean transitionSuccess = stateMachine.transitionStatus(
            taskId,
            com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum.CHECKING,
            "开始执行查重任务"
        );

        if (!transitionSuccess) {
            log.warn("状态转换失败，任务 ID: {}", taskId);
            return;
        }

        // 2. 推送进度：开始处理
        publishProgress(taskId, paperId, "FILE_PARSING", 10, "正在解析论文文件...", 5);

        com.abin.checkrepeatservice.pojo.entity.CheckTask checkTask = checkTaskMapper.selectById(taskId);
        PaperInfo paperInfo = paperInfoMapper.selectById(paperId);

        try {
            // 3. 校验超时
            if (Duration.between(startTime, LocalDateTime.now()).toMillis() > taskTimeout) {
                throw new RuntimeException("查重任务执行超时");
            }

            if (paperInfo == null || paperInfo.getFilePath() == null || paperInfo.getFilePath().trim().isEmpty()) {
                throw new RuntimeException("论文文件路径未设置");
            }

            // 4. 提取论文文本内容
            log.info("开始提取论文文本 - 任务 ID: {}", taskId);
            String paperText = extractTextFromFile(paperInfo.getFilePath());
            if (paperText.trim().isEmpty()) {
                throw new RuntimeException("论文文本提取失败");
            }

            // 5. 推送进度：开始比对
            publishProgress(taskId, paperId, "TEXT_COMPARING", 30, "正在进行文本相似度比对...", 30);

            // 6. 执行查重引擎
            log.info("开始执行查重比对 - 任务 ID: {}", taskId);
            com.abin.checkrepeatservice.pojo.vo.CheckResult checkResult =
                checkEngineManager.executeCheck(
                    paperText,
                    paperInfo.getPaperTitle(),
                    Arrays.asList(com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum.LOCAL)
                );

            if (!checkResult.isSuccess()) {
                throw new RuntimeException("查重引擎执行失败：" + checkResult.getFailReason());
            }

            double maxSimilarity = checkResult.getSimilarity().doubleValue();
            List<Map<String, Object>> repeatDetails = parseRepeatDetailsFromCheckResult(checkResult);

            // 7. 推送进度：生成报告
            publishProgress(taskId, paperId, "REPORT_GENERATING", 80, "正在生成查重报告...", 10);

            // 8. 生成查重报告
            CheckReport checkReport = generateCheckReport(checkTask, maxSimilarity, repeatDetails);

            // 9. 更新任务状态为成功
            stateMachine.transitionStatus(taskId,
                com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum.COMPLETED,
                "查重任务执行成功");

            // 10. 更新任务信息
            checkTask.setCheckRate(BigDecimal.valueOf(maxSimilarity));
            checkTask.setEndTime(LocalDateTime.now());
            checkTask.setReportId(checkReport.getId());
            checkTaskMapper.updateById(checkTask);

            // 11. 更新论文状态（成功后）
            updatePaperSuccess(paperInfo, maxSimilarity);

            // 12. 写入详细查重结果
            writeCheckResultDetail(taskId, paperId, maxSimilarity, repeatDetails, paperInfo);

            // 13. 推送进度：完成
            publishProgress(taskId, paperId, "COMPLETED", 100, "查重完成！", 0);

            // 14. 告知学生结果（相似度 + 风险等级）
            // 放在报告与明细都落库之后，学生点进通知能看到数据。
            // sendSimilarityCheckResultNotification 自带 catch 兜底、从不抛异常，
            // 所以它失败不会把这次成功的查重翻成 FAILURE。
            if (paperInfo.getStudentId() != null) {
                notificationService.sendSimilarityCheckResultNotification(
                        paperInfo.getStudentId(),
                        paperId,
                        paperInfo.getPaperTitle(),
                        maxSimilarity,
                        RiskLevelUtils.of(maxSimilarity));
            }

            log.info("查重任务执行成功 - 任务 ID: {}, 相似度：{}%", taskId, maxSimilarity);

        } catch (Exception e) {
            log.error("查重任务执行失败 - 任务 ID: {}", taskId, e);

            // 异常处理：更新任务状态为失败
            stateMachine.transitionStatus(taskId,
                com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum.FAILURE,
                e.getMessage().length() > 500 ? e.getMessage().substring(0, 500) : e.getMessage());

            // 回滚论文状态
            PaperInfo updatePaper = new PaperInfo();
            updatePaper.setId(paperInfo.getId());
            updatePaper.setPaperStatus(DictConstants.PaperStatus.ASSIGNED);
            updatePaper.setCheckTime(null);
            paperInfoMapper.updateById(updatePaper);

            // 推送失败消息
            publishProgress(taskId, paperId, "FAILED", 0, "查重失败：" + e.getMessage(), 0);
        }
    }

    /**
     * 推送进度到 WebSocket
     */
    private void publishProgress(Long taskId, Long paperId, String stage,
                                int percent, String message, int estimatedSeconds) {
        try {
            CheckProgressEvent progressEvent = CheckProgressEvent.builder()
                .source(this)
                .taskId(taskId)
                .paperId(paperId)
                .stage(stage)
                .percent(percent)
                .message(message)
                .estimatedRemainingSeconds(estimatedSeconds)
                .build();
            webSocketHandler.sendProgress(progressEvent);
        } catch (Exception e) {
            log.warn("WebSocket 进度推送失败 - 任务 ID: {}", taskId, e);
        }
    }

    /**
     * 从文件中提取文本内容
     */
    private String extractTextFromFile(String filePath) throws Exception {
        String fullPath = basePath + filePath;
        File file = new File(fullPath);
        if (!file.exists() || !file.isFile()) {
            throw new RuntimeException("论文文件不存在：" + filePath);
        }
        try (InputStream inputStream = new FileInputStream(file)) {
            return tika.parseToString(inputStream);
        }
    }

    /**
     * 解析查重结果详情
     */
    private List<Map<String, Object>> parseRepeatDetailsFromCheckResult(
            com.abin.checkrepeatservice.pojo.vo.CheckResult checkResult) {
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
     * 生成查重报告
     */
    private CheckReport generateCheckReport(com.abin.checkrepeatservice.pojo.entity.CheckTask checkTask,
                                           double checkRate, List<Map<String, Object>> repeatDetails) {
        // 使用 CheckTaskServiceImpl 中的方法
        try {
            // 反射调用或重新实现
            return createCheckReport(checkTask, checkRate, repeatDetails);
        } catch (Exception e) {
            log.error("生成查重报告失败", e);
            throw new RuntimeException("生成查重报告失败：" + e.getMessage());
        }
    }
    
    /**
     * 创建查重报告（简化版）
     */
    private CheckReport createCheckReport(com.abin.checkrepeatservice.pojo.entity.CheckTask checkTask,
                                         double checkRate, List<Map<String, Object>> repeatDetails) {
        // 1. 生成报告编号
        String reportNo = "REPORT" + System.currentTimeMillis();
        
        // 2. 生成报告路径
        String reportPath = "D:/data/report/" + LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/" + reportNo + ".pdf";
        
        // 3. 构建报告实体
        CheckReport checkReport = new CheckReport();
        checkReport.setTaskId(checkTask.getId());
        checkReport.setReportNo(reportNo);
        checkReport.setRepeatDetails(com.alibaba.fastjson.JSON.toJSONString(repeatDetails));
        checkReport.setReportPath(reportPath);
        checkReport.setReportType("pdf");
        UserBusinessInfoUtils.setAuditField(checkReport, true);
        checkReportMapper.insert(checkReport);
        
        return checkReport;
    }

    /**
     * 更新论文状态为成功
     */
    private void updatePaperSuccess(PaperInfo paperInfo, double similarity) {
        boolean qualified = similarity <= defaultThreshold.doubleValue();
        PaperInfo updatePaper = new PaperInfo();
        updatePaper.setId(paperInfo.getId());
        updatePaper.setPaperStatus(qualified
                ? DictConstants.PaperStatus.AUDITING
                : DictConstants.PaperStatus.REJECTED);
        updatePaper.setSimilarityRate(BigDecimal.valueOf(similarity));
        updatePaper.setCheckCompleted(1);
        updatePaper.setCheckSource("LOCAL");
        updatePaper.setCheckEngineType("local");
        updatePaper.setCheckResult(qualified ? "合格" : "不合格");
        updatePaper.setCheckTime(LocalDateTime.now());
        paperInfoMapper.updateById(updatePaper);
    }

    /**
     * 写入详细查重结果
     */
    private void writeCheckResultDetail(Long taskId, Long paperId, double maxSimilarity,
                                       List<Map<String, Object>> repeatDetails,
                                       PaperInfo paperInfo) {
        try {
            com.abin.checkrepeatservice.pojo.entity.CheckResult dbCheckResult = 
                new com.abin.checkrepeatservice.pojo.entity.CheckResult();
            dbCheckResult.setTaskId(taskId);
            dbCheckResult.setPaperId(paperId);
            dbCheckResult.setRepeatRate(BigDecimal.valueOf(maxSimilarity));
            dbCheckResult.setCheckSource("LOCAL");
            dbCheckResult.setCheckTime(LocalDateTime.now());
            dbCheckResult.setWordCount(paperInfo.getWordCount());
            dbCheckResult.setStatus(1);
            
            // 设置最相似论文
            if (!repeatDetails.isEmpty()) {
                Map<String, Object> topDetail = repeatDetails.get(0);
                if (topDetail.get("source") != null) {
                    dbCheckResult.setMostSimilarPaper(topDetail.get("source").toString());
                }
            }
            
            UserBusinessInfoUtils.setAuditField(dbCheckResult, true);
            checkResultMapper.insert(dbCheckResult);
            
            log.info("详细查重结果写入成功 - 任务 ID: {}, 相似度：{}%", taskId, maxSimilarity);
        } catch (Exception e) {
            log.error("写入详细查重结果失败", e);
            // 不影响主流程
        }
    }
}
