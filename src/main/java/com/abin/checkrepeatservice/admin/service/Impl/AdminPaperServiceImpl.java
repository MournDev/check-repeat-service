package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.service.AdminPaperService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.component.PaperStageGate;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.user.service.PaperStatusLogService;
import com.abin.checkrepeatservice.common.enums.CheckStatusFilterEnum;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
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
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
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

    @Resource
    private PaperStatusLogService paperStatusLogService;

    @Resource
    private DictService dictService;

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
    public Result<String> auditPaper(Long paperId, String auditResult, String auditComment) {
        try {
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
            }

            // 确定目标状态
            String targetStatus;
            if ("approved".equals(auditResult)) {
                targetStatus = DictConstants.PaperStatus.COMPLETED;
            } else if ("rejected".equals(auditResult)) {
                targetStatus = DictConstants.PaperStatus.REJECTED;
            } else {
                return Result.error(ResultCode.PARAM_ERROR, "无效的审核结果");
            }

            // S12：状态流转统一走阶段门裁决（非法流转抛 40305）；
            // 归一化兼容未跑段 3 迁移的库上残留的历史数字状态码
            String fromStatus = paperStageGate.normalizeLegacy(paperInfo.getPaperStatus());
            paperStageGate.assertTransition(fromStatus, targetStatus);

            // S12：审核意见独立存储到 audit_comment，不再覆盖 check_result（查重结果）
            paperInfo.setPaperStatus(targetStatus);
            paperInfo.setAuditComment(auditComment);
            paperInfo.setUpdateTime(LocalDateTime.now());

            paperInfoMapper.updateById(paperInfo);

            // S12：补写状态日志，保证学生端时间线完整
            paperStatusLogService.recordStatusLog(paperId, fromStatus, targetStatus,
                    buildAuditReason(auditResult, auditComment),
                    UserBusinessInfoUtils.getCurrentUserId(), null);

            log.info("论文审核成功: paperId={}, result={}, fromStatus={}", paperId, auditResult, fromStatus);
            return Result.success("论文审核成功");
        } catch (BusinessException e) {
            // S12：阶段门 40305 等业务异常透传原错误码，不降级为 500
            log.warn("论文审核被拦截: paperId={}, code={}, msg={}", paperId, e.getResultCode().getCode(), e.getMessage());
            return Result.error(e.getResultCode(), e.getMessage());
        } catch (Exception e) {
            log.error("论文审核失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "论文审核失败: " + "系统异常，请稍后重试");
        }
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
    public Result<String> batchAuditPapers(List<Long> paperIds, String auditResult, String auditComment) {
        try {
            int successCount = 0;
            int failCount = 0;
            
            for (Long paperId : paperIds) {
                try {
                    Result<String> result = auditPaper(paperId, auditResult, auditComment);
                    if (result.getCode() == 200) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    log.error("批量审核单个论文失败: paperId={}", paperId, e);
                    failCount++;
                }
            }
            
            String message = String.format("批量审核完成: 成功%d个, 失败%d个", successCount, failCount);
            log.info(message);
            
            if (successCount > 0) {
                return Result.success(message);
            } else {
                return Result.error(ResultCode.SYSTEM_ERROR, "批量审核全部失败");
            }
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
            List<Map<String, Object>> rows =
                    paperInfoMapper.selectPaperStatusStats(todayStart, null, null);

            long totalPapers = 0L;
            long todayPapers = 0L;
            Map<String, Long> countByStatus = new HashMap<>();
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
            Map<String, String> statusLabels = dictService.getDictValueMap(DictConstants.DICT_PAPER_STATUS);
            List<PaperExportExcelVO> rows = papers.stream().map(p -> {
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

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = "论文列表_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
            EasyExcel.write(response.getOutputStream(), PaperExportExcelVO.class)
                    .sheet("论文列表")
                    .doWrite(rows);
            log.info("导出论文列表成功: {}行", rows.size());
        } catch (Exception e) {
            log.error("导出论文列表失败", e);
            writeExportError(response, "导出失败：" + e.getMessage());
        }
    }

    /**
     * 导出失败时按既有范式写 JSON 错误响应（response 可能已部分写入，只能尽力提示）。
     */
    private void writeExportError(HttpServletResponse response, String message) {
        try {
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":500,\"message\":\"" + message + "\"}");
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
    public Result<String> downloadPaper(Long paperId) {
        try {
            // 获取论文信息
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
                return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
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
            
            // 更新论文查重标记
            updatePaperCheckFlags(paperInfo, "school", "local");
            
            // 执行内部查重检测
            Result<SimilarityDetectionResult> detectionResult = detectionService.detectPaperSimilarity(paperId, null);
            
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
                        
                    // 更新论文查重标记
                    updatePaperCheckFlags(paperInfo, "school", "local");
                        
                    // 执行内部查重检测
                    Result<SimilarityDetectionResult> detectionResult = detectionService.detectPaperSimilarity(paperId, null);
                        
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
}