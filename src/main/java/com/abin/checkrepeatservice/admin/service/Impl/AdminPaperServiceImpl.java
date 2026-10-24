package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.service.AdminPaperService;
import com.abin.checkrepeatservice.admin.dto.OverdueRemindReq;
import com.abin.checkrepeatservice.admin.mapper.PaperReminderLogMapper;
import com.abin.checkrepeatservice.admin.vo.OverduePaperPageVO;
import com.abin.checkrepeatservice.admin.vo.OverduePaperVO;
import com.abin.checkrepeatservice.admin.vo.OverdueRemindResultVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.component.PaperStageGate;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.SemesterUtils;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.notification.service.NotificationService;
import com.abin.checkrepeatservice.user.service.Impl.EmailService;
import com.abin.checkrepeatservice.user.mapper.PaperStatusLogMapper;
import com.abin.checkrepeatservice.user.service.PaperStatusLogService;
import com.abin.checkrepeatservice.common.enums.CheckStatusFilterEnum;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperReminderLog;
import com.abin.checkrepeatservice.pojo.entity.ReviewRecord;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.teacher.mapper.ReviewRecordMapper;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.abin.checkrepeatservice.detection.service.EnhancedSimilarityDetectionService;
import com.abin.checkrepeatservice.detection.dto.SimilarityDetectionResult;
import com.abin.checkrepeatservice.admin.vo.PaperExportExcelVO;
import com.abin.checkrepeatservice.common.service.Impl.DictService;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理员论文管理服务实现类
 */
@Service
@Slf4j
public class AdminPaperServiceImpl implements AdminPaperService {

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;
    
    @Resource
    private FileService fileService;
    
    @Resource
    private EnhancedSimilarityDetectionService detectionService;

    @Resource
    private PaperStageGate paperStageGate;

    // 治理项：数据范围统一裁决（管理员角色可按学院/专业/教师/本人收窄）
    @Resource
    private com.abin.checkrepeatservice.common.datascope.DataScopeResolver dataScopeResolver;

    @Resource
    private PaperStatusLogService paperStatusLogService;

    @Resource
    private DictService dictService;

    @Resource
    private ReviewRecordMapper reviewRecordMapper;

    @Resource
    private CheckTaskMapper checkTaskMapper;

    // Wave 3：超期催办留痕与站内信/邮件通道
    @Resource
    private PaperReminderLogMapper paperReminderLogMapper;

    @Resource
    private PaperStatusLogMapper paperStatusLogMapper;

    @Resource
    private NotificationService notificationService;

    @Resource
    private EmailService emailService;

    /** Wave 3：超期判定 SLA（小时），默认 72h */
    @Value("${review.overdue.sla-hours:72}")
    private int overdueSlaHours;

    /** 审核来源：导师审核 */
    private static final String AUDIT_SOURCE_TEACHER = "TEACHER";
    /** 审核来源：管理员代审 */
    private static final String AUDIT_SOURCE_ADMIN_PROXY = "ADMIN_PROXY";
    /** 代审原因最大长度（与 review_record.proxy_reason 列宽一致） */
    private static final int PROXY_REASON_MAX_LENGTH = 200;

    /** 导出时间列格式（与 AdminAdvisorEvalServiceImpl 同口径） */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 论文类型中英文映射（init.sql paper_type 注释口径）；未知值回退原码 */
    private static final Map<String, String> PAPER_TYPE_LABELS = Map.of(
            "graduation", "毕业论文",
            "course", "课程论文",
            "other", "其他");

    /** 导出单次最大行数，防止全表导出打爆内存 */
    private static final int EXPORT_MAX_ROWS = 10000;

    @Override
    public Result<Page<PaperInfo>> getPaperList(Integer page, Integer size, String paperStatus, 
                                              String paperType, String keyword, String startDate, String endDate,
                                              String majorName, String grade, String checkStatus,
                                              Double minSimilarity, Double maxSimilarity) {
        try {
            Page<PaperInfo> paperPage = new Page<>(page, size);
            LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
            
            // 【新增】默认排除已撤回的论文（除非明确指定要包含）
            wrapper.ne(PaperInfo::getPaperStatus, DictConstants.PaperStatus.WITHDRAWN);
            
            // 状态筛选
            if (paperStatus != null && !paperStatus.isEmpty()) {
                wrapper.eq(PaperInfo::getPaperStatus, paperStatus);
            }
            
            // 类型筛选
            if (paperType != null && !paperType.isEmpty()) {
                wrapper.eq(PaperInfo::getPaperType, paperType);
            }
            
            // 关键词搜索（标题、作者、论文关键词等）
            // S11 安全修复：inSql 字符串拼接存在 SQL 注入，改用 apply {0} 参数绑定
            if (keyword != null && !keyword.isEmpty()) {
                wrapper.and(w -> w.like(PaperInfo::getPaperTitle, keyword)
                                .or()
                                .like(PaperInfo::getAuthor, keyword)
                                .or()
                                .like(PaperInfo::getPaperAbstract, keyword)
                                .or()
                                .apply("student_id IN (SELECT id FROM sys_user WHERE real_name LIKE CONCAT('%', {0}, '%') AND is_deleted = 0)", keyword));
            }
            
            // 时间范围筛选
            if (startDate != null && !startDate.isEmpty()) {
                wrapper.ge(PaperInfo::getCreateTime, LocalDateTime.parse(startDate));
            }
            if (endDate != null && !endDate.isEmpty()) {
                wrapper.le(PaperInfo::getCreateTime, LocalDateTime.parse(endDate));
            }
            
            // 专业筛选（通过专业名称关联查询）
            // S11 安全修复：inSql 字符串拼接存在 SQL 注入，改用 apply {0} 参数绑定
            if (majorName != null && !majorName.isEmpty()) {
                wrapper.apply("major_id IN (SELECT id FROM major WHERE major_name LIKE CONCAT('%', {0}, '%') AND is_deleted = 0)", majorName);
            }
            
            // 年级筛选（需要关联sys_user表）
            // S11 安全修复：inSql 字符串拼接存在 SQL 注入，改用 apply {0} 参数绑定
            if (grade != null && !grade.isEmpty()) {
                wrapper.apply("student_id IN (SELECT id FROM sys_user WHERE grade = {0} AND is_deleted = 0)", grade);
            }
            
            // 查重状态筛选
            if (checkStatus != null && !checkStatus.isEmpty()) {
                addCheckStatusCondition(wrapper, checkStatus);
            }
            
            // 相似度范围筛选（数值范围）
            if (minSimilarity != null || maxSimilarity != null) {
                addSimilarityRangeByValue(wrapper, minSimilarity, maxSimilarity);
            }
            
            // 排除已删除的论文
            wrapper.eq(PaperInfo::getIsDeleted, 0);
            // 数据范围：按当前角色 sys_role.data_scope 收窄（默认 ALL 不改变行为）
            dataScopeResolver.applyPaperScope(wrapper);
            wrapper.orderByDesc(PaperInfo::getCreateTime);
            
            Page<PaperInfo> resultPage = paperInfoMapper.selectPage(paperPage, wrapper);
            
            // 补充关联信息
            enhancePaperList(resultPage.getRecords());
            
            log.info("管理员获取论文列表成功: 总数={}", resultPage.getTotal());
            return Result.success("论文列表获取成功", resultPage);
        } catch (Exception e) {
            log.error("获取论文列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文列表失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<PaperInfo> getPaperDetail(Long paperId) {
        try {
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }
            if (!dataScopeResolver.canAccessPaper(paperInfo)) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权查看该论文");
            }
            
            // 补充详细信息
            enhancePaperDetail(paperInfo);
            
            log.info("获取论文详情成功: paperId={}", paperId);
            return Result.success("论文详情获取成功", paperInfo);
        } catch (Exception e) {
            log.error("获取论文详情失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文详情失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Map<String, Object>> auditPaper(Long paperId, String auditResult,
                                                  String auditComment, String proxyReason) {
        try {
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }
            if (!dataScopeResolver.canAccessPaper(paperInfo)) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权审核该论文");
            }

            // 确定目标状态
            String targetStatus;
            Integer reviewStatus;
            if ("approved".equals(auditResult)) {
                targetStatus = DictConstants.PaperStatus.COMPLETED;
                reviewStatus = DictConstants.ReviewStatus.PASS;
            } else if ("rejected".equals(auditResult)) {
                targetStatus = DictConstants.PaperStatus.REJECTED;
                reviewStatus = DictConstants.ReviewStatus.REJECT;
            } else {
                return Result.error(ResultCode.PARAM_ERROR, "无效的审核结果");
            }

            // 审核来源不由客户端决定：管理端入口统一按管理员代审落库
            String auditSource = AUDIT_SOURCE_ADMIN_PROXY;
            String normalizedProxyReason = normalizeProxyReason(proxyReason);
            if (AUDIT_SOURCE_ADMIN_PROXY.equals(auditSource)) {
                String proxyError = validateProxyReason(normalizedProxyReason);
                if (proxyError != null) {
                    return Result.error(ResultCode.PARAM_ERROR, proxyError);
                }
            }

            // S12：状态流转统一走阶段门裁决（非法流转抛 40305）；
            // 归一化兼容未跑段 3 迁移的库上残留的历史数字状态码
            String fromStatus = paperStageGate.normalizeLegacy(paperInfo.getPaperStatus());
            paperStageGate.assertTransition(fromStatus, targetStatus);

            Long operatorId = UserBusinessInfoUtils.getCurrentUserId();
            String operatorRole = UserBusinessInfoUtils.getCurrentUserRoleCode();

            // S12：审核意见独立存储到 audit_comment，不再覆盖 check_result（查重结果）
            paperInfo.setPaperStatus(targetStatus);
            paperInfo.setAuditComment(auditComment);
            paperInfo.setUpdateTime(LocalDateTime.now());

            paperInfoMapper.updateById(paperInfo);

            // 代审留痕：管理员路径同样写 review_record，审计侧按 audit_source 区分责任主体，
            // signoff_id 留空即表示该结论未经导师查重签核（可反查绕过签核）
            ReviewRecord reviewRecord = new ReviewRecord();
            reviewRecord.setPaperId(paperId);
            reviewRecord.setTaskId(findLatestCompletedTaskId(paperId));
            // teacher_id 保持论文指导教师，避免污染教师端审核统计；
            // 实际出结论人单独落 auditor_id/auditor_role
            reviewRecord.setTeacherId(paperInfo.getTeacherId() != null ? paperInfo.getTeacherId() : operatorId);
            reviewRecord.setReviewStatus(reviewStatus);
            reviewRecord.setReviewOpinion(auditComment);
            reviewRecord.setReviewTime(LocalDateTime.now());
            reviewRecord.setAuditSource(auditSource);
            reviewRecord.setProxyReason(AUDIT_SOURCE_ADMIN_PROXY.equals(auditSource) ? normalizedProxyReason : null);
            reviewRecord.setAuditorId(operatorId);
            reviewRecord.setAuditorRole(operatorRole);
            reviewRecord.setSignoffId(null);
            UserBusinessInfoUtils.setAuditField(reviewRecord, true);
            reviewRecordMapper.insert(reviewRecord);

            // S12：补写状态日志，保证学生端时间线完整
            paperStatusLogService.recordStatusLog(paperId, fromStatus, targetStatus,
                    buildAuditReason(auditResult, auditComment),
                    operatorId, null);

            Map<String, Object> data = new HashMap<>();
            data.put("reviewRecordId", reviewRecord.getId());
            data.put("paperStatus", targetStatus);
            data.put("auditSource", auditSource);

            log.info("论文代审成功: paperId={}, result={}, fromStatus={}, reviewRecordId={}, operatorId={}",
                    paperId, auditResult, fromStatus, reviewRecord.getId(), operatorId);
            return Result.success("论文审核成功", data);
        } catch (BusinessException e) {
            // S12：阶段门 40305 等业务异常透传原错误码，不降级为 500
            log.warn("论文审核被拦截: paperId={}, code={}, msg={}", paperId, e.getResultCode().getCode(), e.getMessage());
            return Result.error(e.getResultCode(), e.getMessage());
        } catch (Exception e) {
            log.error("论文审核失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "论文审核失败: " + "系统异常，请稍后重试");
        }
    }

    /** 代审原因入库前归一化：去首尾空白，空串按 null 处理 */
    private String normalizeProxyReason(String proxyReason) {
        if (proxyReason == null) {
            return null;
        }
        String trimmed = proxyReason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 代审原因服务端强校验；返回 null 表示通过 */
    private String validateProxyReason(String proxyReason) {
        if (proxyReason == null) {
            return "代审原因不能为空";
        }
        if (proxyReason.length() > PROXY_REASON_MAX_LENGTH) {
            return "代审原因长度不能超过" + PROXY_REASON_MAX_LENGTH + "字符";
        }
        return null;
    }

    /** 取论文最新一次已完成查重任务ID；无任务时返回 null（review_record.task_id 已允许为空） */
    private Long findLatestCompletedTaskId(Long paperId) {
        CheckTask latestTask = checkTaskMapper.selectOne(new LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getPaperId, paperId)
                .eq(CheckTask::getCheckStatus, DictConstants.CheckStatus.COMPLETED)
                .orderByDesc(CheckTask::getCreateTime)
                .orderByDesc(CheckTask::getId)
                .last("LIMIT 1"));
        return latestTask == null ? null : latestTask.getId();
    }

    /**
     * 构造审核动作的状态日志原因（S12）。
     * 意见拼入原因便于学生端时间线直接可读；总长控制在 paper_status_log.status_reason 的 500 字符内。
     */
    private String buildAuditReason(String auditResult, String auditComment) {
        StringBuilder reason = new StringBuilder("管理员初审").append("approved".equals(auditResult) ? "通过" : "退回");
        if (auditComment != null && !auditComment.isBlank()) {
            reason.append("：").append(auditComment.trim());
        }
        String result = reason.toString();
        if (result.length() > 500) {
            result = result.substring(0, 497) + "...";
        }
        return result;
    }

    @Override
    public Result<Map<String, Object>> batchAuditPapers(List<Long> paperIds, String auditResult,
                                                        String auditComment, String proxyReason) {
        try {
            int successCount = 0;
            List<String> skipReasons = new ArrayList<>();
            List<Map<String, Object>> results = new ArrayList<>();

            for (Long paperId : paperIds) {
                Map<String, Object> item = new HashMap<>();
                item.put("paperId", paperId);
                try {
                    Result<Map<String, Object>> result =
                            auditPaper(paperId, auditResult, auditComment, proxyReason);
                    if (result.getCode() == 200) {
                        successCount++;
                        item.put("success", true);
                        item.put("paperStatus", result.getData().get("paperStatus"));
                        item.put("auditSource", result.getData().get("auditSource"));
                        item.put("reviewRecordId", result.getData().get("reviewRecordId"));
                    } else {
                        // 并发窗口：管理员填意见期间论文可能已被教师先审完（40305）等，
                        // 逐篇跳过并记录原因，不再让单篇业务失败拖垮整批
                        String reason = "论文" + paperId + "：" + result.getMessage();
                        skipReasons.add(reason);
                        item.put("success", false);
                        item.put("message", result.getMessage());
                    }
                } catch (Exception e) {
                    log.error("批量审核单个论文失败: paperId={}", paperId, e);
                    String reason = "论文" + paperId + "：系统异常，请稍后重试";
                    skipReasons.add(reason);
                    item.put("success", false);
                    item.put("message", "系统异常，请稍后重试");
                }
                results.add(item);
            }

            String message = String.format("批量审核完成: 成功%d篇, 跳过%d篇", successCount, skipReasons.size());
            log.info("{} (auditResult={})", message, auditResult);

            // 对齐答辩批量先例：返回 {total, success, skipped, skipReasons[], results[]} 明细，
            // results 让前端能逐条标记（成功项带 reviewRecordId 与落库后的 auditSource）
            Map<String, Object> data = new HashMap<>();
            data.put("total", paperIds.size());
            data.put("success", successCount);
            data.put("skipped", skipReasons.size());
            data.put("skipReasons", skipReasons);
            data.put("results", results);
            return Result.success(message, data);
        } catch (Exception e) {
            log.error("批量审核论文失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量审核论文失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<String> deletePaper(Long paperId) {
        try {
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }
            if (!dataScopeResolver.canAccessPaper(paperInfo)) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权删除该论文");
            }
            
            // 软删除
            paperInfo.setIsDeleted(1);
            paperInfo.setUpdateTime(LocalDateTime.now());
            paperInfoMapper.updateById(paperInfo);
            
            log.info("论文删除成功: paperId={}", paperId);
            return Result.success("论文删除成功");
        } catch (Exception e) {
            log.error("论文删除失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "论文删除失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public Result<Map<String, Object>> getPaperStatistics() {
        try {
            // S14：原 总数+6 个状态+今日新增共 8 次 selectCount，合并为一条 GROUP BY 聚合
            LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            long totalPapers = 0L;
            long todayPapers = 0L;
            Map<String, Long> countByStatus = new HashMap<>();

            if (com.abin.checkrepeatservice.common.datascope.DataScope.ALL
                    .equals(dataScopeResolver.resolveScope())) {
                // 本分支仅 ALL 角色进入，scope 传 null 即全量口径
                List<Map<String, Object>> rows =
                        paperInfoMapper.selectPaperStatusStats(todayStart, null, null, null);
                for (Map<String, Object> row : rows) {
                    long cnt = row.get("cnt") == null ? 0L : ((Number) row.get("cnt")).longValue();
                    totalPapers += cnt;
                    if (row.get("todayCnt") != null) {
                        todayPapers += ((Number) row.get("todayCnt")).longValue();
                    }
                    Object status = row.get("status");
                    if (status != null) {
                        countByStatus.put(status.toString(), cnt);
                    }
                }
            } else {
                // 收窄范围（学院/专业/教师/本人）：用统一裁决组件逐状态计数，
                // 聚合 SQL 不带范围参数，不能复用于非 ALL 角色
                LambdaQueryWrapper<PaperInfo> totalWrapper = new LambdaQueryWrapper<>();
                totalWrapper.eq(PaperInfo::getIsDeleted, 0);
                dataScopeResolver.applyPaperScope(totalWrapper);
                totalPapers = paperInfoMapper.selectCount(totalWrapper);

                LambdaQueryWrapper<PaperInfo> todayWrapper = new LambdaQueryWrapper<>();
                todayWrapper.eq(PaperInfo::getIsDeleted, 0)
                        .ge(PaperInfo::getCreateTime, todayStart);
                dataScopeResolver.applyPaperScope(todayWrapper);
                todayPapers = paperInfoMapper.selectCount(todayWrapper);

                for (String status : List.of(DictConstants.PaperStatus.PENDING,
                        DictConstants.PaperStatus.ASSIGNED, DictConstants.PaperStatus.CHECKING,
                        DictConstants.PaperStatus.AUDITING, DictConstants.PaperStatus.COMPLETED,
                        DictConstants.PaperStatus.REJECTED)) {
                    LambdaQueryWrapper<PaperInfo> statusWrapper = new LambdaQueryWrapper<>();
                    statusWrapper.eq(PaperInfo::getIsDeleted, 0)
                            .eq(PaperInfo::getPaperStatus, status);
                    dataScopeResolver.applyPaperScope(statusWrapper);
                    countByStatus.put(status, paperInfoMapper.selectCount(statusWrapper));
                }
            }

            Map<String, Long> statusStats = new HashMap<>();
            statusStats.put("pending", countByStatus.getOrDefault(DictConstants.PaperStatus.PENDING, 0L));
            statusStats.put("assigned", countByStatus.getOrDefault(DictConstants.PaperStatus.ASSIGNED, 0L));
            statusStats.put("checking", countByStatus.getOrDefault(DictConstants.PaperStatus.CHECKING, 0L));
            statusStats.put("auditing", countByStatus.getOrDefault(DictConstants.PaperStatus.AUDITING, 0L));
            statusStats.put("completed", countByStatus.getOrDefault(DictConstants.PaperStatus.COMPLETED, 0L));
            statusStats.put("rejected", countByStatus.getOrDefault(DictConstants.PaperStatus.REJECTED, 0L));

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalPapers", totalPapers);
            stats.put("statusStats", statusStats);
            stats.put("todayPapers", todayPapers);

            log.info("获取论文统计信息成功");
            return Result.success("论文统计信息获取成功", stats);
        } catch (Exception e) {
            log.error("获取论文统计信息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文统计信息失败: " + "系统异常，请稍后重试");
        }
    }

    @Override
    public void exportPaperList(Map<String, Object> params, HttpServletResponse response) {
        try {
            // S12：导出假实现替换为 EasyExcel 流式真实导出（沿用 AdminAdvisorEvalServiceImpl.export 范式）。
            // 筛选口径：keyword/grade/checkStatus 与列表页同款；majorId 为精确匹配（前端导出入口传的是 ID）。
            LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.ne(PaperInfo::getPaperStatus, DictConstants.PaperStatus.WITHDRAWN);

            // Wave 3：学期/时间范围过滤（startDate/endDate 优先于 semester）
            SemesterUtils.Range range = SemesterUtils.resolve(
                    strParam(params.get("semester")),
                    strParam(params.get("startDate")),
                    strParam(params.get("endDate")));
            if (range.start() != null) {
                wrapper.ge(PaperInfo::getSubmitTime, range.start());
            }
            if (range.endExclusive() != null) {
                wrapper.lt(PaperInfo::getSubmitTime, range.endExclusive());
            }

            String paperStatus = strParam(params.get("paperStatus"));
            if (hasText(paperStatus)) {
                wrapper.eq(PaperInfo::getPaperStatus, paperStatus);
            }

            String keyword = strParam(params.get("keyword"));
            if (hasText(keyword)) {
                wrapper.and(w -> w.like(PaperInfo::getPaperTitle, keyword)
                                .or()
                                .like(PaperInfo::getAuthor, keyword)
                                .or()
                                .like(PaperInfo::getPaperAbstract, keyword)
                                .or()
                                .apply("student_id IN (SELECT id FROM sys_user WHERE real_name LIKE CONCAT('%', {0}, '%') AND is_deleted = 0)", keyword));
            }

            String majorId = strParam(params.get("majorId"));
            if (hasText(majorId)) {
                wrapper.eq(PaperInfo::getMajorId, Long.valueOf(majorId));
            }

            String grade = strParam(params.get("grade"));
            if (hasText(grade)) {
                wrapper.apply("student_id IN (SELECT id FROM sys_user WHERE grade = {0} AND is_deleted = 0)", grade);
            }

            String checkStatus = strParam(params.get("checkStatus"));
            if (hasText(checkStatus) && CheckStatusFilterEnum.isValidCode(checkStatus)) {
                addCheckStatusCondition(wrapper, checkStatus);
            }

            wrapper.eq(PaperInfo::getIsDeleted, 0);
            wrapper.orderByDesc(PaperInfo::getCreateTime);
            wrapper.last(" LIMIT " + EXPORT_MAX_ROWS);

            List<PaperInfo> papers = paperInfoMapper.selectList(wrapper);
            enhancePaperList(papers);

            // 状态列字典中文化，缺失回退原码；论文类型走固定映射
            List<PaperExportExcelVO> rows = toPaperExcelRows(papers);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String semesterSuffix = hasText(range.semester()) ? "_" + range.semester() : "";
            String fileName = "论文清单" + semesterSuffix + "_"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            String encodedFileName = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setHeader("Content-disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            EasyExcel.write(response.getOutputStream(), PaperExportExcelVO.class)
                    .sheet("论文列表")
                    .doWrite(rows);
            log.info("导出论文列表成功: {}行", rows.size());
        } catch (BusinessException e) {
            // 参数校验失败发生在写流之前：按 Result 错误码返回 JSON，不写文件头
            log.warn("导出论文列表参数校验失败: {}", e.getMessage());
            writeExportError(response, e.getResultCode().getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("导出论文列表失败", e);
            writeExportError(response, 500, "导出失败：" + e.getMessage());
        }
    }

    /**
     * 导出失败时按既有范式写 JSON 错误响应（response 可能已部分写入，只能尽力提示）。
     */
    private void writeExportError(HttpServletResponse response, int code, String message) {
        try {
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\"}");
        } catch (Exception ex) {
            log.error("写导出错误响应失败", ex);
        }
    }

    /** Map 入参取 String（导出入口为 query string，值均为字符串） */
    private static String strParam(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public List<PaperExportExcelVO> buildPaperExportRows(String semester, String startDate, String endDate) {
        SemesterUtils.Range range = SemesterUtils.resolve(semester, startDate, endDate);
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(PaperInfo::getPaperStatus, DictConstants.PaperStatus.WITHDRAWN)
                .eq(PaperInfo::getIsDeleted, 0);
        if (range.start() != null) {
            wrapper.ge(PaperInfo::getSubmitTime, range.start());
        }
        if (range.endExclusive() != null) {
            wrapper.lt(PaperInfo::getSubmitTime, range.endExclusive());
        }
        wrapper.orderByDesc(PaperInfo::getCreateTime).last(" LIMIT " + EXPORT_MAX_ROWS);

        List<PaperInfo> papers = paperInfoMapper.selectList(wrapper);
        enhancePaperList(papers);
        return toPaperExcelRows(papers);
    }

    /**
     * 论文实体 -> 导出行（管理员导出与学期包 ZIP 共用，避免两套列映射漂移）。
     */
    private List<PaperExportExcelVO> toPaperExcelRows(List<PaperInfo> papers) {
        // 状态列字典中文化，缺失回退原码；论文类型走固定映射
        Map<String, String> statusLabels = dictService.getDictValueMap(DictConstants.DICT_PAPER_STATUS);
        return papers.stream().map(p -> {
            PaperExportExcelVO row = new PaperExportExcelVO();
            row.setPaperTitle(p.getPaperTitle());
            row.setStudentName(p.getStudentName());
            row.setStudentUsername(p.getStudentUsername());
            row.setStudentMajor(p.getStudentMajor());
            row.setStudentGrade(p.getStudentGrade());
            row.setTeacherName(p.getTeacherRealName());
            row.setPaperType(p.getPaperType() == null ? null
                    : PAPER_TYPE_LABELS.getOrDefault(p.getPaperType(), p.getPaperType()));
            row.setPaperStatus(p.getPaperStatus() == null ? null
                    : statusLabels.getOrDefault(p.getPaperStatus(), p.getPaperStatus()));
            row.setSimilarityRate(p.getSimilarityRate() == null ? null : p.getSimilarityRate().toPlainString());
            row.setCheckResult(p.getCheckResult());
            row.setAuditComment(p.getAuditComment());
            row.setSubmitTime(p.getSubmitTime() == null ? null : p.getSubmitTime().format(EXPORT_TIME_FORMATTER));
            return row;
        }).toList();
    }
    
    @Override
    public Result<String> downloadPaper(Long paperId) {
        try {
            // 获取论文信息
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }
            if (!dataScopeResolver.canAccessPaper(paperInfo)) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权下载该论文");
            }
            
            // 检查是否有文件
            if (paperInfo.getFileId() == null) {
                return Result.error(ResultCode.PARAM_ERROR, "论文文件不存在");
            }
            
            // 获取文件信息
            FileInfo fileInfo = fileService.getById(paperInfo.getFileId());
            if (fileInfo == null) {
                return Result.error(ResultCode.PARAM_ERROR, "文件信息不存在");
            }
            
            // 构造文件下载链接
            String downloadUrl = "/api/file/download/" + paperInfo.getFileId();
            
            log.info("论文文件下载准备就绪: paperId={}, fileName={}", paperId, fileInfo.getOriginalFilename());
            return Result.success("论文文件下载链接生成成功", downloadUrl);
            
        } catch (Exception e) {
            log.error("论文文件下载失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "论文文件下载失败: " + "系统异常，请稍后重试");
        }
    }
    
    @Override
    public Result<String> schoolInternalCheckPaper(Long paperId) {
        try {
            // 获取论文信息
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }
            if (!dataScopeResolver.canAccessPaper(paperInfo)) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权对该论文发起查重");
            }
            
            // 更新论文查重标记
            updatePaperCheckFlags(paperInfo, "school", "local");
            
            // 执行内部查重检测（管理端已做数据范围校验，走管理入口）
            Result<SimilarityDetectionResult> detectionResult =
                    detectionService.detectPaperSimilarityForAdmin(paperId, null);
            
            if (detectionResult.getCode() == 200) {
                SimilarityDetectionResult result = detectionResult.getData();
                
                // 更新查重完成状态和相似度
                updatePaperCheckCompletion(paperInfo, result.getOverallSimilarity(), true);
                
                log.info("校内查重检测完成: paperId={}, similarity={}%, segments={}", 
                        paperId, result.getOverallSimilarity(), result.getSimilarSegments().size());
                return Result.success("校内查重检测完成", "查重相似度: " + result.getOverallSimilarity() + "%");
            } else {
                // 查重失败，更新失败状态
                updatePaperCheckCompletion(paperInfo, null, false);
                return Result.error(detectionResult.getCode(), "查重检测失败: " + detectionResult.getMessage());
            }
            
        } catch (Exception e) {
            log.error("校内查重检测失败：paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "校内查重检测失败：" + "系统异常，请稍后重试");
        }
    }
        
    @Override
    public Result<String> batchSchoolInternalCheckPaper(List<Long> paperIds) {
        try {
            if (paperIds == null || paperIds.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "论文 ID 列表不能为空");
            }
                
            if (paperIds.size() > 20) {
                return Result.error(ResultCode.PARAM_ERROR, "单次批量查重最多支持 20 篇论文");
            }
                
            int successCount = 0;
            int failCount = 0;
            List<String> failReasons = new ArrayList<>();
                
            for (Long paperId : paperIds) {
                try {
                    PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
                    if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
                        failCount++;
                        failReasons.add("论文 ID " + paperId + " 不存在");
                        continue;
                    }
                    if (!dataScopeResolver.canAccessPaper(paperInfo)) {
                        failCount++;
                        failReasons.add("论文 ID " + paperId + " 超出当前数据范围");
                        continue;
                    }
                        
                    // 更新论文查重标记
                    updatePaperCheckFlags(paperInfo, "school", "local");
                        
                    // 执行内部查重检测（管理端已做数据范围校验，走管理入口）
                    Result<SimilarityDetectionResult> detectionResult =
                            detectionService.detectPaperSimilarityForAdmin(paperId, null);
                        
                    if (detectionResult.getCode() == 200) {
                        SimilarityDetectionResult result = detectionResult.getData();
                        updatePaperCheckCompletion(paperInfo, result.getOverallSimilarity(), true);
                        successCount++;
                        log.info("批量查重 - 论文 ID: {}, 相似度：{}%", paperId, result.getOverallSimilarity());
                    } else {
                        failCount++;
                        failReasons.add("论文 ID " + paperId + ": " + detectionResult.getMessage());
                        updatePaperCheckCompletion(paperInfo, null, false);
                    }
                } catch (Exception e) {
                    failCount++;
                    failReasons.add("论文 ID " + paperId + ": " + e.getMessage());
                    log.error("批量查重失败：paperId={}", paperId, e);
                }
            }
                
            String message = String.format("批量查重完成：成功%d篇，失败%d篇", successCount, failCount);
            if (!failReasons.isEmpty()) {
                message += "。失败详情：" + String.join("; ", failReasons);
            }
                
            return Result.success(message);
                
        } catch (Exception e) {
            log.error("批量校内查重失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量校内查重失败：" + "系统异常，请稍后重试");
        }
    }
        
    @Override
    public Result<String> batchThirdPartyCheckPaper(List<Long> paperIds) {
        try {
            if (paperIds == null || paperIds.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "论文 ID 列表不能为空");
            }
                
            if (paperIds.size() > 20) {
                return Result.error(ResultCode.PARAM_ERROR, "单次批量查重最多支持 20 篇论文");
            }
                
            // TODO: 实现第三方查重逻辑（需要对接知网、维普等 API）
            // 当前返回提示错误
            return Result.error(ResultCode.SYSTEM_ERROR, "第三方查重功能暂未实现，请联系管理员配置");
                
        } catch (Exception e) {
            log.error("批量第三方查重失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量第三方查重失败：" + "系统异常，请稍后重试");
        }
    }

    /**
     * 补充论文列表信息
     *
     * <p>S10 性能修复：原实现逐篇调用 {@link #enhancePaperDetail}，每篇最多触发
     * 2 次 selectById（学生+教师），分页列表形成 N+1。现按本页全部学生/教师 ID
     * 各做一次 selectBatchIds，再于内存拼装。</p>
     */
    private void enhancePaperList(List<PaperInfo> papers) {
        if (papers == null || papers.isEmpty()) {
            return;
        }
        // 收集本页所有学生、教师 ID
        List<Long> studentIds = papers.stream()
                .map(PaperInfo::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<Long> teacherIds = papers.stream()
                .map(PaperInfo::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, SysUser> studentMap = studentIds.isEmpty() ? new HashMap<>()
                : sysUserMapper.selectBatchIds(studentIds).stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, SysUser> teacherMap = teacherIds.isEmpty() ? new HashMap<>()
                : sysUserMapper.selectBatchIds(teacherIds).stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u));

        for (PaperInfo paper : papers) {
            if (paper.getStudentId() != null) {
                SysUser student = studentMap.get(paper.getStudentId());
                if (student != null) {
                    paper.setStudentName(student.getRealName());
                    paper.setStudentUsername(student.getUsername());
                    paper.setStudentGrade(student.getGrade());
                    paper.setStudentMajor(student.getMajor());
                    paper.setStudentCollege(student.getCollegeName());
                }
            }
            if (paper.getTeacherId() != null) {
                SysUser teacher = teacherMap.get(paper.getTeacherId());
                if (teacher != null) {
                    paper.setTeacherRealName(teacher.getRealName());
                }
            }
        }

        // 审核留痕：取每篇论文最新一条 review_record，回传 auditSource/proxyReason/auditorName/auditTime。
        // 前端据此用 row.auditSource === 'ADMIN_PROXY' 判定代审，不再依赖会话内 Set 或意见前缀。
        List<Long> paperIds = papers.stream()
                .map(PaperInfo::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ReviewRecord> latestReviewMap = loadLatestReviewRecords(paperIds);
        List<Long> auditorIds = latestReviewMap.values().stream()
                .map(r -> r.getAuditorId() != null ? r.getAuditorId() : r.getTeacherId())
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> auditorMap = auditorIds.isEmpty() ? new HashMap<>()
                : sysUserMapper.selectBatchIds(auditorIds).stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u));
        for (PaperInfo paper : papers) {
            fillAuditInfo(paper, latestReviewMap.get(paper.getId()), auditorMap);
        }
    }

    /**
     * 补充论文详细信息
     */
    private void enhancePaperDetail(PaperInfo paper) {
        // 补充学生信息
        if (paper.getStudentId() != null) {
            SysUser student = sysUserMapper.selectById(paper.getStudentId());
            if (student != null) {
                paper.setStudentName(student.getRealName());
                paper.setStudentUsername(student.getUsername());
                paper.setStudentGrade(student.getGrade());
                paper.setStudentMajor(student.getMajor());
                paper.setStudentCollege(student.getCollegeName());
            }
        }
        
        // 补充导师信息
        if (paper.getTeacherId() != null) {
            SysUser teacher = sysUserMapper.selectById(paper.getTeacherId());
            if (teacher != null) {
                paper.setTeacherRealName(teacher.getRealName());
            }
        }

        // 审核留痕字段（与列表同口径）
        Map<Long, ReviewRecord> latestReviewMap =
                loadLatestReviewRecords(Collections.singletonList(paper.getId()));
        ReviewRecord latestReview = latestReviewMap.get(paper.getId());
        Long auditorId = latestReview == null ? null
                : (latestReview.getAuditorId() != null ? latestReview.getAuditorId() : latestReview.getTeacherId());
        SysUser auditor = auditorId == null ? null : sysUserMapper.selectById(auditorId);
        Map<Long, SysUser> auditorMap = auditor == null ? new HashMap<>() : Map.of(auditorId, auditor);
        fillAuditInfo(paper, latestReview, auditorMap);
    }

    /**
     * 批量取每篇论文最新一条审核记录（reviewTime + id 倒序，同秒写入也能稳定取到最新）。
     */
    private Map<Long, ReviewRecord> loadLatestReviewRecords(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return new HashMap<>();
        }
        List<ReviewRecord> records = reviewRecordMapper.selectList(new LambdaQueryWrapper<ReviewRecord>()
                .in(ReviewRecord::getPaperId, paperIds)
                .orderByDesc(ReviewRecord::getReviewTime)
                .orderByDesc(ReviewRecord::getId));
        Map<Long, ReviewRecord> latestMap = new HashMap<>();
        for (ReviewRecord record : records) {
            latestMap.putIfAbsent(record.getPaperId(), record);
        }
        return latestMap;
    }

    /**
     * 把审核记录填充到论文展示字段；历史记录无 audit_source 时按 TEACHER 兜底。
     */
    private void fillAuditInfo(PaperInfo paper, ReviewRecord review, Map<Long, SysUser> auditorMap) {
        if (review == null) {
            return;
        }
        paper.setAuditSource(review.getAuditSource() == null ? AUDIT_SOURCE_TEACHER : review.getAuditSource());
        paper.setProxyReason(review.getProxyReason());
        paper.setAuditTime(review.getReviewTime());
        // 审核意见真值源：教师审核路径只写 review_record，不回写 paper_info.audit_comment；
        // 列表回传时用审核记录兜底，保证 auditComment/feedback 都是意见原文。
        if ((paper.getAuditComment() == null || paper.getAuditComment().isBlank())
                && review.getReviewOpinion() != null) {
            paper.setAuditComment(review.getReviewOpinion());
        }
        Long auditorId = review.getAuditorId() != null ? review.getAuditorId() : review.getTeacherId();
        SysUser auditor = auditorId == null || auditorMap == null ? null : auditorMap.get(auditorId);
        if (auditor != null) {
            paper.setAuditorName(auditor.getRealName());
        }
    }
    
    /**
     * 更新论文查重标记
     */
    private void updatePaperCheckFlags(PaperInfo paperInfo, String engineType, String source) {
        try {
            PaperInfo updateInfo = new PaperInfo();
            updateInfo.setId(paperInfo.getId());
            updateInfo.setCheckEngineType(engineType);
            updateInfo.setCheckSource(source);
            updateInfo.setCheckTime(LocalDateTime.now());
            // 初始化查重完成状态为 0（未开始），实际完成时由 updatePaperCheckCompletion 设置为 1
            if (paperInfo.getCheckCompleted() == null) {
                updateInfo.setCheckCompleted(0);
            }
            updateInfo.setUpdateTime(LocalDateTime.now());
                
            paperInfoMapper.updateById(updateInfo);
                
            log.info("论文查重标记更新成功：paperId={}, engineType={}, source={}", 
                    paperInfo.getId(), engineType, source);
        } catch (Exception e) {
            log.error("更新论文查重标记失败：paperId={}", paperInfo.getId(), e);
        }
    }
    
    /**
     * 更新论文查重完成状态
     */
    private void updatePaperCheckCompletion(PaperInfo paperInfo, Double similarity, boolean success) {
        try {
            PaperInfo updateInfo = new PaperInfo();
            updateInfo.setId(paperInfo.getId());
            updateInfo.setCheckCompleted(success ? 1 : 0);
                
            if (similarity != null) {
                updateInfo.setSimilarityRate(BigDecimal.valueOf(similarity));
                // 设置查重结果描述
                updateInfo.setCheckResult("查重完成，相似度：" + similarity + "%");
            } else {
                updateInfo.setCheckResult(success ? "查重完成" : "查重失败");
            }
                
            // 确保查重引擎类型和来源被正确设置
            if (paperInfo.getCheckEngineType() == null) {
                updateInfo.setCheckEngineType("LOCAL");
            }
            if (paperInfo.getCheckSource() == null) {
                updateInfo.setCheckSource("LOCAL");
            }
                
            // 设置查重时间（如果是第一次完成）
            if (success && paperInfo.getCheckTime() == null) {
                updateInfo.setCheckTime(LocalDateTime.now());
            }
                
            updateInfo.setUpdateTime(LocalDateTime.now());
                
            paperInfoMapper.updateById(updateInfo);
                
            log.info("论文查重完成状态更新：paperId={}, success={}, similarity={}", 
                    paperInfo.getId(), success, similarity);
        } catch (Exception e) {
            log.error("更新论文查重完成状态失败：paperId={}", paperInfo.getId(), e);
        }
    }
    
    /**
     * 添加相似度范围筛选条件
     */
    private void addSimilarityRangeCondition(LambdaQueryWrapper<PaperInfo> wrapper, String similarityRange) {
        switch (similarityRange.toLowerCase()) {
            case "<20%":
            case "lt20":
                wrapper.lt(PaperInfo::getSimilarityRate, 20);
                break;
            case "20%-50%":
            case "20to50":
                wrapper.between(PaperInfo::getSimilarityRate, 20, 50);
                break;
            case ">50%":
            case "gt50":
                wrapper.gt(PaperInfo::getSimilarityRate, 50);
                break;
            default:
                // 不支持的范围，不添加筛选条件
                log.warn("不支持的相似度范围: {}", similarityRange);
                break;
        }
    }
    
    /**
     * 添加查重状态筛选条件
     */
    private void addCheckStatusCondition(LambdaQueryWrapper<PaperInfo> wrapper, String checkStatus) {
        CheckStatusFilterEnum status = CheckStatusFilterEnum.fromCode(checkStatus);
        
        switch (status) {
            case NOT_CHECKED:
                // 未查重：check_completed = 0 或 check_engine_type IS NULL
                wrapper.and(w -> w.eq(PaperInfo::getCheckCompleted, 0)
                                .or()
                                .isNull(PaperInfo::getCheckEngineType));
                break;
                
            case SCHOOL_CHECK:
                // 校内查重：check_engine_type = 'school' 且 check_completed = 1
                wrapper.eq(PaperInfo::getCheckEngineType, "school")
                       .eq(PaperInfo::getCheckCompleted, 1);
                break;
                
            case THIRD_PARTY_CHECK:
                // 第三方查重：check_engine_type = 'third_party' 且 check_completed = 1
                wrapper.eq(PaperInfo::getCheckEngineType, "third_party")
                       .eq(PaperInfo::getCheckCompleted, 1);
                break;
                
            case COMPLETED:
                // 已完成：check_completed = 1（无论使用哪种引擎）
                wrapper.eq(PaperInfo::getCheckCompleted, 1);
                break;
                
            default:
                log.warn("不支持的查重状态筛选: {}", checkStatus);
                break;
        }
    }
    
    /**
     * 根据数值范围添加相似度筛选条件
     */
    private void addSimilarityRangeByValue(LambdaQueryWrapper<PaperInfo> wrapper, Double minSimilarity, Double maxSimilarity) {
        if (minSimilarity != null && maxSimilarity != null) {
            // 同时指定了最小值和最大值 - 范围查询
            wrapper.between(PaperInfo::getSimilarityRate, minSimilarity, maxSimilarity);
        } else if (minSimilarity != null) {
            // 只指定了最小值 - 大于等于
            wrapper.ge(PaperInfo::getSimilarityRate, minSimilarity);
        } else if (maxSimilarity != null) {
            // 只指定了最大值 - 小于等于
            wrapper.le(PaperInfo::getSimilarityRate, maxSimilarity);
        }
        
        log.debug("添加相似度范围筛选条件: min={}, max={}", minSimilarity, maxSimilarity);
    }

    // ==================== Wave 3：超期预警 / 催办 / 生命周期漏斗 ====================

    @Override
    public Result<OverduePaperPageVO> getOverduePapers(String bucket, Integer page, Integer size, String keyword) {
        try {
            int current = (page == null || page < 1) ? 1 : page;
            int pageSize = (size == null || size < 1) ? 10 : Math.min(size, 100);
            String normalizedBucket = normalizeOverdueBucket(bucket);
            String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

            Page<OverduePaperVO> result = paperInfoMapper.selectOverduePapers(
                    new Page<>(current, pageSize), overdueSlaHours, normalizedBucket, normalizedKeyword);

            Map<String, Long> bucketCounts = new LinkedHashMap<>();
            bucketCounts.put("1", 0L);
            bucketCounts.put("3", 0L);
            bucketCounts.put("7", 0L);
            for (Map<String, Object> row : paperInfoMapper.countOverdueBuckets(overdueSlaHours)) {
                // JDBC 返回的列标签大小写因驱动/容器而异，按不区分大小写读取
                String key = null;
                Object count = null;
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if ("bucketKey".equalsIgnoreCase(entry.getKey())) {
                        key = row.get(entry.getKey()) == null ? null : String.valueOf(row.get(entry.getKey()));
                    } else if ("cnt".equalsIgnoreCase(entry.getKey())) {
                        count = row.get(entry.getKey());
                    }
                }
                if (key != null && count instanceof Number) {
                    bucketCounts.put(key, ((Number) count).longValue());
                }
            }

            OverduePaperPageVO vo = new OverduePaperPageVO();
            vo.setRecords(result.getRecords());
            vo.setTotal(result.getTotal());
            vo.setBucketCounts(bucketCounts);
            return Result.success("查询成功", vo);
        } catch (Exception e) {
            log.error("查询超期论文失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询超期论文失败：系统异常，请稍后重试");
        }
    }

    @Override
    public Result<OverdueRemindResultVO> remindOverduePapers(OverdueRemindReq req) {
        if (req == null || req.getPaperIds() == null || req.getPaperIds().isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "paperIds 不能为空");
        }
        List<Long> paperIds = req.getPaperIds().stream().filter(Objects::nonNull).distinct().toList();
        if (paperIds.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "paperIds 不能为空");
        }
        if (paperIds.size() > 100) {
            return Result.error(ResultCode.PARAM_ERROR,
                    "单次最多催办100篇，当前选择" + paperIds.size() + "篇");
        }

        String channel = req.getChannel() == null || req.getChannel().isBlank()
                ? "IN_APP" : req.getChannel().trim().toUpperCase();
        if (!List.of("IN_APP", "EMAIL", "BOTH").contains(channel)) {
            return Result.error(ResultCode.PARAM_ERROR, "channel 仅支持 IN_APP / EMAIL / BOTH");
        }
        String customMessage = req.getMessage() == null ? null : req.getMessage().trim();
        if (customMessage != null && customMessage.length() > 200) {
            return Result.error(ResultCode.PARAM_ERROR, "催办文案不能超过200字");
        }

        Long operatorId = UserBusinessInfoUtils.getCurrentUserId();
        String operatorRole = UserBusinessInfoUtils.getCurrentUserRoleCode();
        LocalDateTime now = LocalDateTime.now();
        List<String> failReasons = new ArrayList<>();
        List<OverdueRemindResultVO.RemindRecord> records = new ArrayList<>();
        int successCount = 0;

        for (Long paperId : paperIds) {
            try {
                PaperInfo paper = paperInfoMapper.selectById(paperId);
                if (paper == null || Integer.valueOf(1).equals(paper.getIsDeleted())) {
                    failReasons.add("论文ID：" + paperId + "，原因：论文不存在或已删除");
                    continue;
                }
                Long teacherId = paper.getTeacherId();
                if (teacherId == null) {
                    failReasons.add("论文ID：" + paperId + "，原因：该论文当前无指导教师");
                    continue;
                }
                SysUser teacher = sysUserMapper.selectById(teacherId);
                if (teacher == null) {
                    failReasons.add("论文ID：" + paperId + "，原因：指导教师账号不存在");
                    continue;
                }

                int overdueDays = computeOverdueDays(paper.getSubmitTime());
                String content = buildRemindMessage(customMessage, paper.getPaperTitle(), overdueDays);

                boolean inAppOk = true;
                boolean emailOk = true;
                if ("IN_APP".equals(channel) || "BOTH".equals(channel)) {
                    Result<Boolean> noticeResult = notificationService.sendSystemNotice(
                            teacherId, "论文审核超期提醒", content, "PAPER_OVERDUE", paperId);
                    inAppOk = noticeResult != null && noticeResult.isSuccess()
                            && Boolean.TRUE.equals(noticeResult.getData());
                }
                if ("EMAIL".equals(channel) || "BOTH".equals(channel)) {
                    emailOk = teacher.getEmail() != null && !teacher.getEmail().isBlank()
                            && emailService.sendNoticeEmail(teacher.getEmail(),
                                    "论文审核超期提醒", content, "PAPER_OVERDUE", paperId, "PAPER");
                }

                if (!inAppOk || !emailOk) {
                    failReasons.add("论文ID：" + paperId + "，原因：通知发送失败（"
                            + (!inAppOk ? "站内信" : "") + (!emailOk ? (inAppOk ? "" : "、") + "邮件" : "")
                            + "）");
                    OverdueRemindResultVO.RemindRecord failed = new OverdueRemindResultVO.RemindRecord();
                    failed.setPaperId(paperId);
                    failed.setTeacherId(teacherId);
                    failed.setSuccess(false);
                    records.add(failed);
                    continue;
                }

                PaperReminderLog reminderLog = new PaperReminderLog();
                reminderLog.setPaperId(paperId);
                reminderLog.setTeacherId(teacherId);
                reminderLog.setOperatorId(operatorId);
                reminderLog.setOperatorRole(operatorRole);
                reminderLog.setRemindChannel(channel);
                reminderLog.setRemindMessage(content.length() > 500 ? content.substring(0, 500) : content);
                reminderLog.setOverdueDays(overdueDays);
                reminderLog.setCreateTime(now);
                paperReminderLogMapper.insert(reminderLog);
                successCount++;

                OverdueRemindResultVO.RemindRecord successRecord = new OverdueRemindResultVO.RemindRecord();
                successRecord.setPaperId(paperId);
                successRecord.setTeacherId(teacherId);
                successRecord.setSuccess(true);
                successRecord.setReminderLogId(reminderLog.getId());
                records.add(successRecord);
            } catch (Exception e) {
                log.error("催办论文失败: paperId={}", paperId, e);
                failReasons.add("论文ID：" + paperId + "，原因：系统异常");
            }
        }

        OverdueRemindResultVO vo = new OverdueRemindResultVO();
        vo.setTotal(paperIds.size());
        vo.setSuccess(successCount);
        vo.setFailed(paperIds.size() - successCount);
        vo.setFailReasons(failReasons);
        vo.setRemindedAt(now);
        vo.setRecords(records);
        return Result.success("催办完成", vo);
    }

    @Override
    public Result<Map<String, Object>> getLifecycleFunnel(String semester) {
        try {
            SemesterUtils.Range range = SemesterUtils.resolve(semester, null, null);

            long submitted = paperInfoMapper.selectCount(buildPaperRangeWrapper(range));

            String[][] stages = {
                    {"checking", "查重阶段", DictConstants.PaperStatus.CHECKING},
                    {"auditing", "导师审核", DictConstants.PaperStatus.AUDITING},
                    {"mid", "中期答辩", DictConstants.PaperStatus.MID_DEFENSE},
                    {"final", "终期答辩", DictConstants.PaperStatus.FINAL_DEFENSE},
                    {"finalized", "定稿", DictConstants.PaperStatus.FINALIZED},
                    {"archived", "归档", DictConstants.PaperStatus.ARCHIVED}
            };

            long[] counts = new long[stages.length];
            boolean logHasData = false;
            for (int i = 0; i < stages.length; i++) {
                Long reached = paperStatusLogMapper.countPapersReachedStatus(
                        stages[i][2], range.start(), range.endExclusive());
                counts[i] = reached == null ? 0L : reached;
                if (counts[i] > 0) {
                    logHasData = true;
                }
            }

            // 状态日志为空（历史/未启用阶段门）时，回退为当前状态快照计数
            if (!logHasData) {
                for (int i = 0; i < stages.length; i++) {
                    LambdaQueryWrapper<PaperInfo> wrapper = buildPaperRangeWrapper(range)
                            .eq(PaperInfo::getPaperStatus, stages[i][2]);
                    counts[i] = paperInfoMapper.selectCount(wrapper);
                }
            }

            List<Map<String, Object>> stageList = new ArrayList<>();
            Map<String, Object> submittedStage = new LinkedHashMap<>();
            submittedStage.put("key", "submitted");
            submittedStage.put("label", "提交论文");
            submittedStage.put("status", "");
            submittedStage.put("count", submitted);
            submittedStage.put("conversionRate", submitted > 0 ? 100.0 : 0.0);
            stageList.add(submittedStage);

            long previous = submitted;
            for (int i = 0; i < stages.length; i++) {
                Map<String, Object> stage = new LinkedHashMap<>();
                stage.put("key", stages[i][0]);
                stage.put("label", stages[i][1]);
                stage.put("status", stages[i][2]);
                stage.put("count", counts[i]);
                double rate = previous <= 0 ? 0.0 : round1(counts[i] * 100.0 / previous);
                stage.put("conversionRate", rate);
                stageList.add(stage);
                previous = counts[i];
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("semester", semester);
            data.put("stages", stageList);
            return Result.success("查询成功", data);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询生命周期漏斗失败: semester={}", semester, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询生命周期漏斗失败：系统异常，请稍后重试");
        }
    }

    private LambdaQueryWrapper<PaperInfo> buildPaperRangeWrapper(SemesterUtils.Range range) {
        LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<PaperInfo>()
                .eq(PaperInfo::getIsDeleted, 0);
        if (range.start() != null) {
            wrapper.ge(PaperInfo::getSubmitTime, range.start());
        }
        if (range.endExclusive() != null) {
            wrapper.lt(PaperInfo::getSubmitTime, range.endExclusive());
        }
        return wrapper;
    }

    private String normalizeOverdueBucket(String bucket) {
        if (bucket == null || bucket.isBlank() || "all".equalsIgnoreCase(bucket)) {
            return null;
        }
        String value = bucket.trim();
        return switch (value) {
            case "1", "3", "7" -> value;
            default -> null;
        };
    }

    private int computeOverdueDays(LocalDateTime submitTime) {
        if (submitTime == null) {
            return 0;
        }
        long days = Duration.between(submitTime.plusHours(overdueSlaHours), LocalDateTime.now()).toDays();
        return (int) Math.max(0L, days);
    }

    private String buildRemindMessage(String customMessage, String paperTitle, int overdueDays) {
        if (customMessage != null && !customMessage.isBlank()) {
            return customMessage;
        }
        String title = paperTitle == null || paperTitle.isBlank() ? "未命名论文" : paperTitle;
        String days = overdueDays > 0 ? "已超期" + overdueDays + "天，" : "";
        return "您的论文《" + title + "》审核" + days + "请尽快处理。";
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}