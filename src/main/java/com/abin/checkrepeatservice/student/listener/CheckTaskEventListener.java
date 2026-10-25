package com.abin.checkrepeatservice.student.listener;

import com.abin.checkrepeatservice.admin.mapper.CheckResultMapper;
import com.abin.checkrepeatservice.common.component.PaperStageGate;
import com.abin.checkrepeatservice.common.component.ReportPathResolver;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.constant.PaperNoticeConstants;
import com.abin.checkrepeatservice.common.engine.CheckEngineManager;
import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.common.service.CheckPolicyResolver;
import com.abin.checkrepeatservice.common.service.PaperLibraryIngestService;
import com.abin.checkrepeatservice.common.statemachine.CheckTaskStateMachine;
import com.abin.checkrepeatservice.common.utils.PdfReportGenerator;
import com.abin.checkrepeatservice.common.utils.ReportHashUtils;
import com.abin.checkrepeatservice.common.utils.RiskLevelUtils;
import com.abin.checkrepeatservice.detection.dto.SentenceHit;
import com.abin.checkrepeatservice.notification.service.NotificationService;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.pojo.entity.CheckSentenceHit;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.abin.checkrepeatservice.student.event.CheckProgressEvent;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckSentenceHitMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.CheckTaskFailureRecoveryService;
import com.abin.checkrepeatservice.student.service.websocket.CheckProgressWebSocketHandler;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 查重任务事件监听器
 * 异步处理查重任务的执行
 * 合格判定阈值优先走检测策略集 check_policy（按论文 degree_type 命中），
 * 未配置/未命中时回落 similarity_threshold 既有解析链路，判定行为不变。
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
    private CheckEngineManager checkEngineManager;

    @Resource
    private CheckResultMapper checkResultMapper;

    @Resource
    private PdfReportGenerator pdfReportGenerator;

    // 报告路径统一解析：库里存相对路径，生成/下载/预览/删除都按配置根目录解析
    @Resource
    private ReportPathResolver reportPathResolver;

    @Resource
    private CheckProgressWebSocketHandler webSocketHandler;

    @Resource
    private NotificationService notificationService;

    // 任务失败统一恢复：failure -> 退额度 -> 回滚论文状态 -> 通知学生
    @Resource
    private CheckTaskFailureRecoveryService checkTaskFailureRecoveryService;

    // 任务完成态与论文状态必须同事务落库，避免“任务已完成、论文仍 checking”
    @Resource
    private TransactionTemplate transactionTemplate;

    // 论文状态流转统一校验（默认影子模式，只告警不阻断）
    @Resource
    private PaperStageGate paperStageGate;

    // 比对库入库管道，正文提取成功后写入 MinIO 并回填 content_path
    @Resource
    private PaperLibraryIngestService paperLibraryIngestService;

    @Value("${file.upload.base-path}")
    private String basePath;

    // 合格判定阈值优先取检测策略集（按论文 degree_type 命中 check_policy），
    // 未命中时由 CheckPolicyResolver 内部回落学院/专业既有解析链路（专业 > 学院 > 全局 > yml 兜底）
    @Resource
    private CheckPolicyResolver checkPolicyResolver;

    @Resource
    private CheckSentenceHitMapper checkSentenceHitMapper;

    /**
     * 报告验真哈希盐：与 ReportVerifyService 共用同一环境变量，
     */
    // 安全修复生产由环境变量 REPORT_HASH_SALT 注入（ProductionSecretValidator 强制校验）
    @Value("${REPORT_HASH_SALT:dev-only-report-hash-salt-do-not-use-in-prod-0123456789}")
    private String reportHashSalt;

    @Value("${check.task.timeout:3600000}")
    private long taskTimeout;

    private final Tika tika = new Tika();

    /**
     * 执行一个「已被抢占」的查重任务。
     *
     * <p>抢占动作已由 {@code CheckTaskDispatchScheduler} 通过
     * {@link CheckTaskStateMachine#claimForExecution}
     * 完成（PENDING -> CHECKING 条件更新），本方法只负责真正的解析/比对/报告生成。
     * 重复投递与并发执行由条件更新保证：只有抢到状态的派发方会调用到这里。</p>
     *
     * @param taskId  已处于 checking 的任务 ID
     * @param paperId 论文 ID
     */
    public void executeClaimedTask(Long taskId, Long paperId) {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("开始执行已抢占的查重任务 - 任务 ID: {}, 论文 ID: {}", taskId, paperId);

        // 1. 推送进度：开始处理
        publishProgress(taskId, paperId, "FILE_PARSING", 10, "正在解析论文文件...", 5);

        CheckTask checkTask = checkTaskMapper.selectById(taskId);
        if (checkTask == null) {
            log.error("查重任务不存在（可能已被清理）- 任务 ID: {}", taskId);
            return;
        }
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

            // 4.1 架构修复（P0）：正文入本地比对库（MinIO + content_path 回填），
            // 否则库内无可比论文，引擎只能空转返回 0%
            paperLibraryIngestService.ingest(paperId, paperText);
            // 刷新内存对象的路径，保证后续更新不回退
            paperInfo = paperInfoMapper.selectById(paperId);

            // 5. 推送进度：开始比对
            publishProgress(taskId, paperId, "TEXT_COMPARING", 30, "正在进行文本相似度比对...", 30);

            // 5.1 推送进度：句级分析（P5：引擎在比对阶段同步完成句级标注与三项指标）
            publishProgress(taskId, paperId, "SENTENCE_ANALYZING", 60, "正在进行句级相似度标注与引文排除...", 20);

            // 6. 执行查重引擎（excludePaperId 排除刚入库的自己，避免自我命中 100%）
            log.info("开始执行查重比对 - 任务 ID: {}", taskId);
            com.abin.checkrepeatservice.pojo.vo.CheckResult checkResult =
                checkEngineManager.executeCheck(
                    paperText,
                    paperInfo.getPaperTitle(),
                    Arrays.asList(com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum.LOCAL),
                    paperId
                );

            if (!checkResult.isSuccess()) {
                throw new RuntimeException("查重引擎执行失败：" + checkResult.getFailReason());
            }

            // P5：落库与阈值判定以「总相似比」新口径为准（非引用命中句字数/正文字数），
            // 句级分析不可用时引擎已回落整篇余弦旧口径，此处统一取值并固定两位小数，
            // 该规范值同时进入 check_task.check_rate 与报告验真哈希，保证两端字节一致
            double effectiveRate = checkResult.getTotalSimilarityRate() != null
                    ? checkResult.getTotalSimilarityRate().doubleValue()
                    : checkResult.getSimilarity().doubleValue();
            BigDecimal effectiveRateValue = BigDecimal.valueOf(effectiveRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal maxPerSource = checkResult.getMaxPerSource();
            BigDecimal citationRate = checkResult.getCitationRate();
            List<SentenceHit> sentenceHits = checkResult.getSentenceHits() != null
                    ? checkResult.getSentenceHits() : new ArrayList<>();
            List<Map<String, Object>> repeatDetails = parseRepeatDetailsFromCheckResult(checkResult);

            // 7. 推送进度：生成报告
            publishProgress(taskId, paperId, "REPORT_GENERATING", 80, "正在生成查重报告...", 10);

            // 8. 构建报告记录（仅内存组装，report_no/相对路径/验真哈希在此算好，不落库）
            CheckReport checkReport = buildCheckReportRecord(checkTask, effectiveRateValue,
                    repeatDetails, maxPerSource, citationRate);

            // 9-11. 报告落库、任务完成态、任务业务字段、论文状态必须同一事务：
            //     任一步失败都整体回滚，任务保持 checking，由外层 catch 统一转 failure 并退款，
            //     避免“任务已完成、论文仍 checking”的假成功状态，
            //     也避免报告先落库、任务却失败而在 check_report 里留下孤儿报告。
            final CheckReport finalReport = checkReport;
            final PaperInfo paperForUpdate = paperInfo;
            transactionTemplate.executeWithoutResult(status -> {
                boolean completed = stateMachine.transitionStatus(taskId,
                        CheckTaskStatusEnum.CHECKING,
                        CheckTaskStatusEnum.COMPLETED,
                        "查重任务执行成功");
                if (!completed) {
                    throw new IllegalStateException("任务成功态流转失败（可能已被超时扫描改写）");
                }

                checkReportMapper.insert(finalReport);

                // 业务字段单独更新（P0-1）：禁止再用执行前查出的 checkTask 全量回写状态
                checkTaskMapper.update(null, new LambdaUpdateWrapper<CheckTask>()
                        .eq(CheckTask::getId, taskId)
                        .eq(CheckTask::getCheckStatus,
                                CheckTaskStatusEnum.COMPLETED.getCode())
                        .set(CheckTask::getCheckRate, effectiveRateValue)
                        .set(CheckTask::getMaxPerSource, maxPerSource)
                        .set(CheckTask::getCitationRate, citationRate)
                        .set(CheckTask::getEndTime, LocalDateTime.now())
                        .set(CheckTask::getReportId, finalReport.getId()));

                updatePaperSuccess(paperForUpdate, effectiveRate);
            });

            // 11.1 事务提交后再落盘 PDF：文件生成失败不回滚已完成的查重结论，
            //      下载/预览端在文件缺失时会按库中数据重新生成
            generateReportFile(checkTask, finalReport, effectiveRateValue);

            // 12. 写入详细查重结果（含 P5 句级命中明细批量落库）
            writeCheckResultDetail(taskId, paperId, effectiveRateValue, maxPerSource, citationRate,
                    sentenceHits, repeatDetails, paperInfo);

            // 13. 推送进度：完成
            publishProgress(taskId, paperId, "COMPLETED", 100, "查重完成！", 0);

            // 14. 告知学生结果（相似度 + 风险等级）
            // 放在报告与明细都落库之后，学生点进通知能看到数据。
            if (paperInfo.getStudentId() != null) {
                notificationService.sendSimilarityCheckResultNotification(
                        paperInfo.getStudentId(),
                        paperId,
                        paperInfo.getPaperTitle(),
                        effectiveRate,
                        RiskLevelUtils.of(effectiveRate));
            }

            // 15. 告知导师：查重完成待审核
            if (paperInfo.getTeacherId() != null) {
                String studentName = paperInfo.getAuthor() == null || paperInfo.getAuthor().isBlank()
                        ? "该学生" : paperInfo.getAuthor();
                notificationService.sendSystemNotice(
                        paperInfo.getTeacherId(),
                        "论文查重完成待审核",
                        String.format("您指导的学生 %s 已完成查重，请及时审核。论文：《%s》，总相似比：%.2f%%。",
                                studentName, paperInfo.getPaperTitle(), effectiveRate),
                        PaperNoticeConstants.NOTICE_TYPE_CHECK_SUCCESS,
                        paperId);
            }

            log.info("查重任务执行成功 - 任务 ID: {}, 总相似比：{}%, 单篇最大相似比：{}%, 引用率：{}%, 命中句数：{}",
                    taskId, effectiveRate, maxPerSource, citationRate, sentenceHits.size());

        } catch (Exception e) {
            log.error("查重任务执行失败 - 任务 ID: {}", taskId, e);

            // M1.2：引擎全失败/比对故障必须落 failure + fail_reason，且兜底 message 为 null 的情况
            String failReason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            // 统一恢复：failure -> 退额度 -> 回滚论文状态 -> 通知学生
            boolean recovered = checkTaskFailureRecoveryService.failTaskAndRestorePaper(taskId, failReason);
            if (!recovered) {
                log.warn("任务失败恢复未执行（任务可能已是终态或已被并发改写），任务 ID: {}", taskId);
                return;
            }

            // 推送失败消息
            publishProgress(taskId, paperId, "FAILED", 0, "查重失败：" + failReason, 0);
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
     * 构建查重报告记录（只在内存中组装，落库由任务完成事务统一负责）。
     */
    private CheckReport buildCheckReportRecord(CheckTask checkTask,
                                              BigDecimal checkRate, List<Map<String, Object>> repeatDetails,
                                              BigDecimal maxPerSource, BigDecimal citationRate) {
        // 1. 生成报告编号：基于任务ID（雪花唯一），不再用毫秒时间戳
        //    —— 批量查重并发完成时同毫秒会撞 uk_report_no，直接导致报告落库失败
        String reportNo = "REPORT" + checkTask.getId();

        // 2. 生成报告相对路径：只存 yyyy/MM/dd/<reportNo>.pdf，
        //    绝对路径在生成/下载/预览时由 ReportPathResolver 按配置解析
        String reportPath = reportPathResolver.buildRelativePath(reportNo);

        // 3. 构建报告实体
        CheckReport checkReport = new CheckReport();
        checkReport.setTaskId(checkTask.getId());
        checkReport.setReportNo(reportNo);
        checkReport.setRepeatDetails(com.alibaba.fastjson2.JSON.toJSONString(repeatDetails));
        checkReport.setReportPath(reportPath);
        checkReport.setReportType("pdf");
        // P5 报告验真码：关键要素（任务/编号/三项指标/论文）JSON + 盐 → SHA-256，
        // 验真接口用 DB 中同样的要素重算比对；checkRate 必须与 check_task.check_rate
        // 是同一个规范值（两位小数），否则验真必失败
        String hashSource = ReportHashUtils.buildHashSource(checkTask.getId(), reportNo,
                checkRate, maxPerSource, citationRate, checkTask.getPaperId(), reportHashSalt);
        checkReport.setContentHash(ReportHashUtils.sha256Hex(hashSource));
        // 异步线程无登录上下文，不能调 UserBusinessInfoUtils.setAuditField（必抛未登录异常）；
        // createBy 直接继承任务创建人（即发起查重的学生），时间/isDeleted 由 MyMetaObjectHandler 兜底填充
        checkReport.setCreateBy(checkTask.getCreateBy());
        return checkReport;
    }

    /**
     * 落盘 PDF 报告文件。
     * 报告记录已随任务完成态提交，落盘失败不回滚结论，下载/预览端可按库中数据重新生成。
     */
    private void generateReportFile(CheckTask checkTask, CheckReport checkReport, BigDecimal checkRate) {
        try {
            String absolutePath = reportPathResolver.toAbsolute(checkReport.getReportPath());
            reportPathResolver.ensureParentDirectory(absolutePath);
            pdfReportGenerator.generatePdfToFile(
                    buildReportPreviewDTO(checkTask, checkReport, checkRate), absolutePath);
        } catch (Exception e) {
            log.error("查重报告文件生成失败（报告记录已入库，下载时可重新生成）- 任务 ID: {}, 报告 ID: {}",
                    checkTask.getId(), checkReport.getId(), e);
        }
    }

    /**
     * 构建报告预览数据（PDF 生成入参）。
     * 只使用当前异步线程可获取的数据，不依赖 SecurityContext。
     */
    private ReportPreviewDTO buildReportPreviewDTO(CheckTask checkTask, CheckReport checkReport,
                                                   BigDecimal checkRate) {
        ReportPreviewDTO dto = new ReportPreviewDTO();
        ReportPreviewDTO.ReportBaseInfoDTO baseInfo = new ReportPreviewDTO.ReportBaseInfoDTO();
        baseInfo.setTaskId(checkTask.getId());
        baseInfo.setTaskNo(checkTask.getTaskNo());
        baseInfo.setPaperVersion(checkTask.getPaperVersion());
        baseInfo.setReportId(checkReport.getId());
        baseInfo.setReportNo(checkReport.getReportNo());
        baseInfo.setSimilarityRate(checkRate);
        baseInfo.setCheckTime(LocalDateTime.now());
        baseInfo.setCheckScope(CheckEngineManager.CHECK_SCOPE);
        baseInfo.setReportDetails(checkReport.getRepeatDetails());

        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        if (paperInfo != null) {
            baseInfo.setPaperTitle(paperInfo.getPaperTitle());
            baseInfo.setAuthor(paperInfo.getAuthor());
            baseInfo.setStudentId(paperInfo.getStudentId());
        }
        dto.setBaseInfo(baseInfo);
        return dto;
    }

    /**
     * 更新论文状态为成功
     */
    private void updatePaperSuccess(PaperInfo paperInfo, double similarity) {
        BigDecimal threshold = checkPolicyResolver.resolveForPaper(paperInfo);
        boolean qualified = similarity <= threshold.doubleValue();
        String newStatus = qualified
                ? DictConstants.PaperStatus.AUDITING
                : DictConstants.PaperStatus.REJECTED;
        paperStageGate.guard(paperInfo.getPaperStatus(), newStatus, "checkTaskListenerSuccess");

        // 条件更新：只有论文仍处于 checking 才允许写入查重结论。
        // 撤回、重新提交或其它并发流转后，状态已不是 checking，这里必须失败而不是覆盖。
        int rows = paperInfoMapper.update(null, new LambdaUpdateWrapper<PaperInfo>()
                .eq(PaperInfo::getId, paperInfo.getId())
                .eq(PaperInfo::getPaperStatus, DictConstants.PaperStatus.CHECKING)
                .eq(PaperInfo::getIsDeleted, 0)
                .set(PaperInfo::getPaperStatus, newStatus)
                .set(PaperInfo::getSimilarityRate, BigDecimal.valueOf(similarity))
                .set(PaperInfo::getCheckCompleted, 1)
                .set(PaperInfo::getCheckSource, "LOCAL")
                .set(PaperInfo::getCheckEngineType, "local")
                .set(PaperInfo::getCheckResult, qualified ? "合格" : "不合格")
                .set(PaperInfo::getCheckTime, LocalDateTime.now()));
        if (rows == 0) {
            // 交给外层事务回滚：任务不能先落 completed，否则会出现“任务成功、论文未更新”的死状态
            throw new IllegalStateException("论文状态已不是 checking，拒绝写入查重结论 - paperId="
                    + paperInfo.getId());
        }
    }

    /**
     * 写入详细查重结果
     * P5 扩展：补写单篇最大相似比/引用率，句级命中明细批量落库 check_sentence_hit
     */
    private void writeCheckResultDetail(Long taskId, Long paperId, BigDecimal checkRate,
                                       BigDecimal maxPerSource, BigDecimal citationRate,
                                       List<SentenceHit> sentenceHits,
                                       List<Map<String, Object>> repeatDetails,
                                       PaperInfo paperInfo) {
        try {
            com.abin.checkrepeatservice.pojo.entity.CheckResult dbCheckResult =
                new com.abin.checkrepeatservice.pojo.entity.CheckResult();
            dbCheckResult.setTaskId(taskId);
            dbCheckResult.setPaperId(paperId);
            dbCheckResult.setRepeatRate(checkRate);
            dbCheckResult.setMaxPerSource(maxPerSource);
            dbCheckResult.setCitationRate(citationRate);
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

            // P5：check_details 存指标汇总摘要（句级明细在 check_sentence_hit 表，避免 JSON 膨胀）
            Map<String, Object> detailSummary = new LinkedHashMap<>();
            detailSummary.put("checkRate", checkRate == null ? null : checkRate.toPlainString());
            detailSummary.put("maxPerSource", maxPerSource == null ? null : maxPerSource.toPlainString());
            detailSummary.put("citationRate", citationRate == null ? null : citationRate.toPlainString());
            detailSummary.put("hitSentenceCount", sentenceHits.size());
            dbCheckResult.setCheckDetails(com.alibaba.fastjson2.JSON.toJSONString(detailSummary));

            // 同上报原因：异步线程手工归属到学生本人，其余审计字段交 MyMetaObjectHandler
            dbCheckResult.setCreateBy(paperInfo.getStudentId());
            checkResultMapper.insert(dbCheckResult);

            // P5：句级命中明细落库（单任务通常几十~几百行，逐条 insert 复用 MP 审计填充）
            for (int i = 0; i < sentenceHits.size(); i++) {
                SentenceHit hit = sentenceHits.get(i);
                CheckSentenceHit row = new CheckSentenceHit();
                row.setTaskId(taskId);
                row.setSentenceIndex(i);
                // SentenceLevelAnalyzer 已截断到 1000 字符，此处兜底再截一次
                String text = hit.getSentenceText();
                row.setSentenceText(text != null && text.length() > 1000 ? text.substring(0, 1000) : text);
                row.setStartOffset(hit.getStartIndex());
                row.setSimilarity(hit.getSimilarity());
                row.setIsCitation(Boolean.TRUE.equals(hit.getIsCitation()) ? 1 : 0);
                row.setHitPaperId(hit.getHitPaperId());
                row.setHitPaperTitle(hit.getHitPaperTitle());
                row.setCreateBy(paperInfo.getStudentId());
                checkSentenceHitMapper.insert(row);
            }

            log.info("详细查重结果写入成功 - 任务 ID: {}, 总相似比：{}%, 句级命中：{} 条",
                    taskId, checkRate, sentenceHits.size());
        } catch (Exception e) {
            log.error("写入详细查重结果失败", e);
            // 不影响主流程
        }
    }
}
