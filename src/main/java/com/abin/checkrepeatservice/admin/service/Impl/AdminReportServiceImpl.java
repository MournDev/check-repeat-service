package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.AdminReportMapper;
import com.abin.checkrepeatservice.admin.service.AdminReportService;
import com.abin.checkrepeatservice.admin.vo.AdminReportDetailVO;
import com.abin.checkrepeatservice.admin.vo.AdminReportListItemVO;
import com.abin.checkrepeatservice.admin.vo.AdminReportStatsVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.component.ReportPathResolver;
import com.abin.checkrepeatservice.common.datascope.DataScopeResolver;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.PdfReportGenerator;
import com.abin.checkrepeatservice.common.utils.ReportContentProcessor;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 管理员报告管理服务实现
 */
@Service
@Slf4j
public class AdminReportServiceImpl implements AdminReportService {

    @Resource
    private AdminReportMapper adminReportMapper;

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private ReportContentProcessor reportContentProcessor;

    @Resource
    private PdfReportGenerator pdfReportGenerator;

    // 治理项：数据范围统一裁决（报告列表/统计/详情/导出按角色范围收窄）
    @Resource
    private DataScopeResolver dataScopeResolver;

    /** 文件名中的非法字符（避免报告编号污染文件名/ZIP条目） */
    private static final String ILLEGAL_FILENAME_CHARS = "[\\\\/:*?\"<>|\\r\\n]";

    /** 单次批量导出的报告数量上限（防止接口被打爆） */
    private static final int MAX_EXPORT_COUNT = 100;

    /** 报告路径解析：库里存相对路径，按配置根目录解析到绝对路径 */
    @Resource
    private ReportPathResolver reportPathResolver;

    @Override
    public Result<Page<AdminReportListItemVO>> getReportList(
            Integer page,
            Integer size,
            String paperTitle,
            String studentName,
            String checkStatus,
            BigDecimal minSimilarity,
            BigDecimal maxSimilarity) {
        try {
            Page<AdminReportListItemVO> pageParam = new Page<>(page, size);
            // 分页拦截器会就地填充 pageParam 的 total 与 records
            adminReportMapper.selectReportPage(pageParam, paperTitle, studentName,
                    checkStatus, minSimilarity, maxSimilarity, dataScopeResolver.currentFilter());
            return Result.success("报告列表查询成功", pageParam);
        } catch (Exception e) {
            log.error("查询报告列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询报告列表失败");
        }
    }

    @Override
    public Result<AdminReportStatsVO> getReportStats(
            String paperTitle,
            String studentName,
            String checkStatus,
            BigDecimal minSimilarity,
            BigDecimal maxSimilarity) {
        try {
            AdminReportStatsVO stats = adminReportMapper.selectReportStats(
                    paperTitle, studentName, checkStatus, minSimilarity, maxSimilarity,
                    dataScopeResolver.currentFilter());
            return Result.success("报告统计查询成功", stats);
        } catch (Exception e) {
            log.error("查询报告统计失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询报告统计失败");
        }
    }

    @Override
    public Result<AdminReportDetailVO> getReportDetail(Long reportId) {
        if (reportId == null) {
            return Result.error(ResultCode.PARAM_ERROR, "报告ID不能为空");
        }

        CheckReport report = adminReportMapper.selectById(reportId);
        if (report == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "报告不存在");
        }

        CheckTask task = checkTaskMapper.selectById(report.getTaskId());
        if (task == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "报告关联的查重任务不存在");
        }

        PaperInfo paper = paperInfoMapper.selectById(task.getPaperId());
        if (paper == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "报告关联的论文不存在");
        }
        if (!dataScopeResolver.canAccessPaper(paper)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权查看该报告");
        }

        // 基础信息直接取自实体，保证即使论文原文文件缺失也能展示报告元数据
        AdminReportDetailVO detail = new AdminReportDetailVO();
        detail.setReportNo(report.getReportNo());
        detail.setPaperTitle(paper.getPaperTitle());
        detail.setStudentId(paper.getStudentId());
        detail.setWordCount(paper.getWordCount());
        detail.setStatus(task.getCheckStatus());
        detail.setCheckTime(task.getEndTime() != null ? task.getEndTime() : report.getCreateTime());
        detail.setSimilarity(task.getCheckRate() != null ? task.getCheckRate() : paper.getSimilarityRate());

        // paper_info.teacher_name 是冗余字段，直接取它，避免导师行缺失时拿不到姓名
        detail.setTeacherName(paper.getTeacherName());

        // 学生姓名与段落明细分别依赖 sys_user 与论文原文文件，任一取不到时降级为“无明细”，
        // 不影响上面的报告元数据展示（ReportContentProcessor 内部也会查 sys_user，
        // 导师行缺失时会 NPE，故一并纳入这里兜底）
        try {
            if (paper.getStudentId() != null) {
                SysUser student = sysUserMapper.selectById(paper.getStudentId());
                detail.setStudentName(student == null ? null : student.getRealName());
            }
            enrichFromPreview(detail, report, task);
        } catch (Exception e) {
            log.warn("报告明细解析失败，仅返回基础信息: reportId={}, 原因={}", reportId, e.getMessage());
            detail.setSimilarSources(0);
            detail.setSimilarSourceList(new ArrayList<>());
        }

        return Result.success("报告详情查询成功", detail);
    }

    /**
     * 用 ReportContentProcessor 的预览数据（含标红段落）填充详情明细
     */
    private void enrichFromPreview(AdminReportDetailVO detail, CheckReport report, CheckTask task) {
        ReportPreviewDTO preview = reportContentProcessor.buildReportPreviewDTO(report, task);

        List<ReportPreviewDTO.ReportParagraphDTO> paragraphs =
                preview.getParagraphs() == null ? new ArrayList<>() : preview.getParagraphs();
        List<ReportPreviewDTO.ReportSimilarSourceDTO> sources =
                preview.getSimilarSources() == null ? new ArrayList<>() : preview.getSimilarSources();

        // 段落序号 -> 重复原文片段，用于填充 matchedParagraphs.sourceText
        Map<Integer, String> repeatFragmentByParaNo = parseRepeatFragments(report.getRepeatDetails());

        List<AdminReportDetailVO.SimilarSource> sourceList = new ArrayList<>();
        for (ReportPreviewDTO.ReportSimilarSourceDTO source : sources) {
            AdminReportDetailVO.SimilarSource item = new AdminReportDetailVO.SimilarSource();
            item.setTitle(source.getSourceName());
            // 重复详情JSON中没有作者字段，保持为空，前端留空展示
            item.setAuthor(null);
            item.setSimilarity(source.getMaxSimilarity());

            List<AdminReportDetailVO.MatchedParagraph> matched = new ArrayList<>();
            for (ReportPreviewDTO.ReportParagraphDTO paragraph : paragraphs) {
                List<Long> sourceIds = paragraph.getSourceIds();
                if (sourceIds == null || !sourceIds.contains(source.getSourceId())) {
                    continue;
                }
                AdminReportDetailVO.MatchedParagraph mp = new AdminReportDetailVO.MatchedParagraph();
                mp.setSourceText(repeatFragmentByParaNo.get(paragraph.getParagraphNo()));
                mp.setPaperText(paragraph.getContent());
                mp.setSimilarity(paragraph.getSimilarity());
                matched.add(mp);
            }
            item.setMatchedParagraphs(matched);
            sourceList.add(item);
        }

        detail.setSimilarSources(sourceList.size());
        detail.setSimilarSourceList(sourceList);
        detail.setSimilarWords(estimateSimilarWords(paragraphs));
    }

    /**
     * 解析 repeat_details JSON，取段落序号 -> 重复原文片段
     */
    private Map<Integer, String> parseRepeatFragments(String repeatDetailsJson) {
        Map<Integer, String> result = new HashMap<>();
        if (repeatDetailsJson == null || repeatDetailsJson.isEmpty()) {
            return result;
        }
        List<Map<String, Object>> details = JSON.parseObject(
                repeatDetailsJson,
                new TypeReference<List<Map<String, Object>>>() {
                });
        if (details == null) {
            return result;
        }
        for (Map<String, Object> item : details) {
            Object paraNo = item.get("paragraphNo");
            Object fragment = item.get("repeatFragment");
            if (paraNo instanceof Integer && fragment instanceof String) {
                result.putIfAbsent((Integer) paraNo, (String) fragment);
            }
        }
        return result;
    }

    /**
     * 估算相似字数：累加重复段落（isRepeat）的纯文本长度
     * 注：重复详情JSON中无“相似字数”字段；且段落内容已被 ReportContentProcessor
     * 按 max-paragraph-length 截断，故该值为下界估算，非精确值
     */
    private Integer estimateSimilarWords(List<ReportPreviewDTO.ReportParagraphDTO> paragraphs) {
        int total = 0;
        for (ReportPreviewDTO.ReportParagraphDTO paragraph : paragraphs) {
            if (Boolean.TRUE.equals(paragraph.getIsRepeat()) && paragraph.getContent() != null) {
                total += paragraph.getContent().replaceAll("<[^>]+>", "").length();
            }
        }
        return total;
    }

    @Override
    public void batchExportReports(List<Long> ids, HttpServletResponse response) {
        if (ids == null || ids.isEmpty()) {
            // 注意：BusinessException 没有 (ResultCode, String) 构造器，两参形式会把自定义
            // 文案当成 extraData 丢掉，必须用三参形式显式传 null
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要导出的报告", null);
        }
        if (ids.size() > MAX_EXPORT_COUNT) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "单次最多导出 " + MAX_EXPORT_COUNT + " 份报告，当前选择 " + ids.size() + " 份", null);
        }

        // 去重：同一 reportId 重复出现会让 ZipEntry 同名冲突
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(ids));

        // 前端根据选中数量固定下载文件名（单个 .pdf / 多个 .zip），因此这里必须
        // 按数量返回对应容器类型，否则单份导出会得到被改名为 .pdf 的 ZIP
        if (distinctIds.size() == 1) {
            exportSinglePdf(distinctIds.get(0), response);
        } else {
            exportZip(distinctIds, response);
        }
    }

    /**
     * 渲染报告PDF字节
     * 业务异常（论文原文文件缺失等）原样抛出，由全局异常处理器转成可读提示；
     * 其他意外异常统一包装，避免把底层堆栈暴露给调用方
     */
    private byte[] renderPdf(CheckReport report, CheckTask task) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            pdfReportGenerator.generatePdf(
                    reportContentProcessor.buildReportPreviewDTO(report, task), buffer);
            return buffer.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成报告PDF失败: reportId={}", report.getId(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "报告内容解析失败，无法导出", null);
        }
    }

    /**
     * 导出单份报告为 PDF
     */
    private void exportSinglePdf(Long reportId, HttpServletResponse response) {
        CheckReport report = adminReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "报告不存在", null);
        }
        CheckTask task = checkTaskMapper.selectById(report.getTaskId());
        if (task == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "报告关联的查重任务不存在", null);
        }
        // 治理项：按 ID 直达的导出入口同样校验数据范围
        PaperInfo exportPaper = paperInfoMapper.selectById(task.getPaperId());
        if (!dataScopeResolver.canAccessPaper(exportPaper)) {
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权导出该报告", null);
        }

        // 先生成内容再写响应头，避免生成失败时留下残缺的响应
        byte[] pdf = renderPdf(report, task);
        try {
            String fileName = safeFileName(report.getReportNo()) + ".pdf";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            response.setContentLength(pdf.length);
            response.getOutputStream().write(pdf);
            response.flushBuffer();
        } catch (IOException e) {
            log.error("导出报告PDF失败: reportId={}", reportId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "导出报告失败", null);
        }
    }

    /** ZIP 条目：只保留“磁盘路径 + 条目名”，不持有 PDF 字节 */
    private record ZipItem(Path path, String entryName) {
    }

    /**
     *
     * <p>旧实现把每份 PDF 重新渲染成 byte[] 并全部攒在 List 里，100 份报告就是
     * 几十~上百 MB 堆内存。现在分两遍：</p>
     * <ol>
     *   <li><b>校验遍</b>：只查元数据 + 判断磁盘文件是否存在，收集“路径 + 条目名”；</li>
     *   <li><b>输出遍</b>：设置响应头后，用 {@link Files#copy(Path, java.io.OutputStream)}
     *       把磁盘上已有的 PDF 流式写入 ZIP，全程不把文件内容读进堆。</li>
     * </ol>
     * 校验遍为空时在写响应头之前就抛错，避免给前端留下残缺 ZIP。
     * 部分失败时跳过该份、其余照常打包，并记 WARN 日志。
     */
    private void exportZip(List<Long> ids, HttpServletResponse response) {
        List<ZipItem> items = new ArrayList<>();
        Set<String> usedEntryNames = new HashSet<>();

        // 第一遍：只做元数据校验与路径解析，不读取 PDF 内容
        for (Long reportId : ids) {
            try {
                CheckReport report = adminReportMapper.selectById(reportId);
                if (report == null) {
                    log.warn("报告不存在，跳过导出: reportId={}", reportId);
                    continue;
                }
                CheckTask task = checkTaskMapper.selectById(report.getTaskId());
                if (task == null) {
                    log.warn("报告关联任务不存在，跳过导出: reportId={}", reportId);
                    continue;
                }
                // 治理项：越权报告逐份跳过，不影响其余报告导出
                PaperInfo exportPaper = paperInfoMapper.selectById(task.getPaperId());
                if (!dataScopeResolver.canAccessPaper(exportPaper)) {
                    log.warn("报告超出当前数据范围，跳过导出: reportId={}", reportId);
                    continue;
                }
                File pdfFile = reportPathResolver.toFile(report.getReportPath());
                if (pdfFile == null || !pdfFile.isFile()) {
                    log.warn("报告文件不存在，跳过导出: reportId={}, path={}",
                            reportId, report.getReportPath());
                    continue;
                }
                String entryName = uniqueEntryName(
                        safeFileName(report.getReportNo()) + ".pdf", usedEntryNames);
                items.add(new ZipItem(pdfFile.toPath(), entryName));
            } catch (Exception e) {
                log.warn("报告导出失败，已跳过: reportId={}, 原因={}", reportId, e.getMessage());
            }
        }

        if (items.isEmpty()) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "所选报告均无法导出", null);
        }

        try {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"reports.zip\"");
            try (ZipOutputStream zipOut = new ZipOutputStream(
                    new BufferedOutputStream(response.getOutputStream()))) {
                for (ZipItem item : items) {
                    zipOut.putNextEntry(new ZipEntry(item.entryName()));
                    // 流式拷贝：Files.copy 不会关闭 zipOut，也不会把文件读进内存
                    Files.copy(item.path(), zipOut);
                    zipOut.closeEntry();
                }
            }
            response.flushBuffer();
            if (items.size() < ids.size()) {
                log.warn("部分报告导出失败已跳过: 成功={}, 请求总数={}", items.size(), ids.size());
            }
        } catch (IOException e) {
            log.error("批量导出报告失败: ids={}", ids, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "批量导出报告失败", null);
        }
    }

    /**
     * 生成不重复的 ZIP 条目名（重复 reportNo 时追加 _2、_3…，避免 ZipException）。
     */
    private String uniqueEntryName(String baseName, Set<String> usedNames) {
        String candidate = baseName;
        int sequence = 2;
        while (!usedNames.add(candidate)) {
            String prefix = baseName.endsWith(".pdf")
                    ? baseName.substring(0, baseName.length() - 4) : baseName;
            candidate = prefix + "_" + sequence++ + ".pdf";
        }
        return candidate;
    }

    /**
     * 清理文件名中的非法字符
     */
    private String safeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "report";
        }
        return name.replaceAll(ILLEGAL_FILENAME_CHARS, "_");
    }
}
