package com.abin.checkrepeatservice.common.utils;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.mapper.FileInfoMapper;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.*;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;

import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报告内容处理工具类：分段、标红、来源匹配
 */
@Component
@Slf4j
public class ReportContentProcessor {

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Resource
    private SysUserMapper sysUserMapper;



    // 在线预览单段最大长度（从配置文件获取）
    @Value("${report.preview.max-paragraph-length}")
    private int maxParagraphLength;

    // 文件上传基础路径
    @Value("${file.upload.base-path}")
    private String uploadBasePath;

    // 段落分割符（适配中文文本：句号/问号/感叹号后换行）
    private static final String PARAGRAPH_SEPARATOR = "[。？！；]";

    /**
     * 构建报告预览DTO（从报告与任务数据生成）
     * @param checkReport 查重报告实体
     * @param checkTask 关联的查重任务
     * @return 报告预览DTO
     */
    public ReportPreviewDTO buildReportPreviewDTO(CheckReport checkReport, CheckTask checkTask) {
        ReportPreviewDTO previewDTO = new ReportPreviewDTO();

        // 1. 构建基础信息
        previewDTO.setBaseInfo(buildBaseInfoDTO(checkReport, checkTask));

        // 2. 构建重复率统计
        previewDTO.setRateStat(buildRateStatDTO(checkReport, checkTask));

        // 3. 构建分段内容（含标红）
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        String paperText = extractPaperText(paperInfo.getFileId()); // 从论文文件提取文本
        List<ReportPreviewDTO.ReportParagraphDTO> paragraphs = buildParagraphs(
                paperText,
                checkReport.getRepeatDetails()
        );
        previewDTO.setParagraphs(paragraphs);

        // 4. 构建相似来源列表
        previewDTO.setSimilarSources(buildSimilarSources(checkReport.getRepeatDetails()));

        return previewDTO;
    }

    /**
     * 从论文文件提取纯文本（适配 doc/docx/pdf）
     */
    private String extractPaperText(Long fileId) {
        // 根据 fileId 查询文件信息
        FileInfo fileInfo = fileInfoMapper.selectById(fileId);
        if (fileInfo == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
            
        // 使用存储路径构建完整文件路径
        String filePath = Paths.get(uploadBasePath, fileInfo.getStoragePath()).toString();
            
        // 复用之前开发的文本提取逻辑（Apache Tika）
        try {
            return TikaTextExtractor.extractTextFromFile(filePath); // 之前开发的工具类，此处简化
        } catch (Exception e) {
            log.error("论文文本提取失败（路径：{}）：", filePath, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR,"报告预览失败，无法提取论文内容");
        }
    }

    /**
     * 构建报告基础信息DTO
     */
    private ReportPreviewDTO.ReportBaseInfoDTO buildBaseInfoDTO(CheckReport checkReport, CheckTask checkTask) {
        ReportPreviewDTO.ReportBaseInfoDTO baseInfo = new ReportPreviewDTO.ReportBaseInfoDTO();
        baseInfo.setReportId(checkReport.getId());
        baseInfo.setReportNo(checkReport.getReportNo());
        baseInfo.setTaskId(checkTask.getId());
        baseInfo.setTaskNo(checkTask.getTaskNo());
        // M3.1 版本—报告绑定：报告基础信息带出任务快照的论文版本
        baseInfo.setPaperVersion(checkTask.getPaperVersion());
        baseInfo.setPaperId(checkTask.getPaperId());

        // M1.1 检测边界明示：报告必须声明实际检测范围
        baseInfo.setCheckScope(com.abin.checkrepeatservice.common.engine.CheckEngineManager.CHECK_SCOPE);
        // 补充论文与用户信息
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        SysUser student = sysUserMapper.selectById(paperInfo.getStudentId());
        SysUser teacher = sysUserMapper.selectById(paperInfo.getTeacherId());

        baseInfo.setPaperTitle(paperInfo.getPaperTitle());
        baseInfo.setStudentName(student.getRealName());
        baseInfo.setTeacherName(teacher.getRealName());
        baseInfo.setGenerateTime(checkReport.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        return baseInfo;
    }

    /**
     * 构建重复率统计DTO
     */
    private ReportPreviewDTO.ReportRateStatDTO buildRateStatDTO(CheckReport checkReport, CheckTask checkTask) {
        ReportPreviewDTO.ReportRateStatDTO rateStat = new ReportPreviewDTO.ReportRateStatDTO();
        // 总重复率（从任务中获取）
        double repeatRate = checkTask.getCheckRate() != null ? checkTask.getCheckRate().doubleValue() : 0.0;
        rateStat.setRepeatRate(BigDecimal.valueOf(repeatRate));
        rateStat.setOriginalRate(100.0 - repeatRate);

        // 解析重复详情，统计重复段落数（详情中可能只有整体相似记录、没有段落序号，此时按 0 计）
        List<Integer> repeatParaNos = parseRepeatDetails(checkReport.getRepeatDetails()).stream()
                .map(detail -> detail.get("paragraphNo"))
                .filter(Number.class::isInstance)
                .map(no -> ((Number) no).intValue())
                .distinct()
                .collect(Collectors.toList());
        rateStat.setRepeatParagraphCount(repeatParaNos.size());

        return rateStat;
    }

    /**
     * 构建分段内容（含标红标记）
     */
    private List<ReportPreviewDTO.ReportParagraphDTO> buildParagraphs(
            String paperText,
            String repeatDetailsJson) {
        // 1. 文本分段（按句号/问号/感叹号分割）
        List<String> rawParagraphs = splitTextToParagraphs(paperText);
        // 2. 解析重复详情
        List<Map<String, Object>> repeatDetails = parseRepeatDetails(repeatDetailsJson);
        // 3. 按段落序号分组重复详情（只有整体相似记录、没有段落序号的详情不参与段落标红）
        Map<Integer, List<Map<String, Object>>> repeatByParaNo = repeatDetails.stream()
                .filter(detail -> detail.get("paragraphNo") instanceof Number)
                .collect(Collectors.groupingBy(detail -> ((Number) detail.get("paragraphNo")).intValue()));

        // 4. 构建分段DTO（含标红）
        List<ReportPreviewDTO.ReportParagraphDTO> paragraphDTOs = new ArrayList<>();
        for (int i = 0; i < rawParagraphs.size(); i++) {
            int paraNo = i + 1; // 段落序号从1开始
            String rawContent = rawParagraphs.get(i);
            // 截取超长段落（避免预览页面过长）
            String content = rawContent.length() > maxParagraphLength
                    ? rawContent.substring(0, maxParagraphLength) + "..."
                    : rawContent;

            ReportPreviewDTO.ReportParagraphDTO paraDTO = new ReportPreviewDTO.ReportParagraphDTO();
            paraDTO.setParagraphNo(paraNo);
            paraDTO.setContent(content);

            // 处理重复段落标红
            if (repeatByParaNo.containsKey(paraNo)) {
                List<Map<String, Object>> paraRepeatDetails = repeatByParaNo.get(paraNo);
                // 计算该段落最大相似度
                double maxSimilarity = paraRepeatDetails.stream()
                        .map(detail -> detail.get("similarity"))
                        .filter(Number.class::isInstance)
                        .mapToDouble(similarity -> ((Number) similarity).doubleValue())
                        .max()
                        .orElse(0.0);
                paraDTO.setSimilarity(maxSimilarity);
                paraDTO.setIsRepeat(maxSimilarity >= 5.0); // 相似度≥5%判定为重复

                // 标红重复片段（简化逻辑：标红相似度最高的片段）
                Map<String, Object> maxSimilarDetail = paraRepeatDetails.stream()
                        .filter(detail -> detail.get("similarity") instanceof Number)
                        .max(Comparator.comparingDouble(detail -> ((Number) detail.get("similarity")).doubleValue()))
                        .orElse(null);
                if (maxSimilarDetail != null) {
                    Object fragment = maxSimilarDetail.get("repeatFragment");
                    if (fragment instanceof String repeatFragment
                            && !repeatFragment.isEmpty() && content.contains(repeatFragment)) {
                        // 用<span>标签包裹标红片段
                        String highlightedContent = content.replace(
                                repeatFragment,
                                "<span style=\"color:red\">" + repeatFragment + "</span>"
                        );
                        paraDTO.setContent(highlightedContent);
                    }
                }

                // 关联相似来源ID
                List<Long> sourceIds = paraRepeatDetails.stream()
                        .map(detail -> detail.get("sourceId"))
                        .filter(Number.class::isInstance)
                        .map(sourceId -> ((Number) sourceId).longValue())
                        .distinct()
                        .collect(Collectors.toList());
                paraDTO.setSourceIds(sourceIds);
            } else {
                // 非重复段落
                paraDTO.setSimilarity(0.0);
                paraDTO.setIsRepeat(false);
                paraDTO.setSourceIds(new ArrayList<>());
            }

            paragraphDTOs.add(paraDTO);
        }

        return paragraphDTOs;
    }

    /**
     * 构建相似来源列表
     */
    private List<ReportPreviewDTO.ReportSimilarSourceDTO> buildSimilarSources(String repeatDetailsJson) {
        List<Map<String, Object>> repeatDetails = parseRepeatDetails(repeatDetailsJson);
        // 去重相似来源：优先按 sourceId 分组；只有整体相似记录时按「来源名称 + 链接」兜底
        Map<String, Map<String, Object>> sourceMap = new LinkedHashMap<>();
        for (Map<String, Object> detail : repeatDetails) {
            Object sourceId = detail.get("sourceId");
            Object sourceName = firstNonNull(detail.get("sourceName"), detail.get("source"));
            Object sourceUrl = firstNonNull(detail.get("sourceUrl"), detail.get("reportUrl"));
            String key = sourceId != null
                    ? "id:" + sourceId
                    : "name:" + sourceName + "|url:" + sourceUrl;
            Map<String, Object> existing = sourceMap.get(key);
            if (existing == null || toDouble(detail.get("similarity")) > toDouble(existing.get("similarity"))) {
                // 保留相似度更高的一条
                sourceMap.put(key, detail);
            }
        }

        // 转换为DTO
        return sourceMap.values().stream()
                .map(detail -> {
                    ReportPreviewDTO.ReportSimilarSourceDTO sourceDTO = new ReportPreviewDTO.ReportSimilarSourceDTO();
                    Object sourceId = detail.get("sourceId");
                    sourceDTO.setSourceId(sourceId instanceof Number ? ((Number) sourceId).longValue() : null);
                    Object sourceName = firstNonNull(detail.get("sourceName"), detail.get("source"));
                    sourceDTO.setSourceName(sourceName instanceof String ? (String) sourceName : null);
                    Object sourceType = detail.get("sourceType");
                    sourceDTO.setSourceType(sourceType instanceof String ? (String) sourceType : null);
                    Object sourceUrl = firstNonNull(detail.get("sourceUrl"), detail.get("reportUrl"));
                    sourceDTO.setSourceUrl(sourceUrl instanceof String ? (String) sourceUrl : null);
                    sourceDTO.setMaxSimilarity(toDouble(detail.get("similarity")));
                    return sourceDTO;
                })
                .sorted((s1, s2) -> Double.compare(s2.getMaxSimilarity(), s1.getMaxSimilarity())) // 按相似度降序
                .collect(Collectors.toList());
    }

    /**
     * 解析 repeat_details JSON：兼容空串、非法 JSON 与「整体相似记录」两种形态，
     * 解析失败时按空详情处理，避免预览接口整体 500。
     */
    private List<Map<String, Object>> parseRepeatDetails(String repeatDetailsJson) {
        if (repeatDetailsJson == null || repeatDetailsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> details = JSON.parseObject(
                    repeatDetailsJson,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            return details == null ? new ArrayList<>() : details;
        } catch (Exception e) {
            log.warn("重复详情 JSON 解析失败，按空详情处理：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 重复详情里的数值可能是 BigDecimal/Double/Integer，统一按 double 取值。
     */
    private double toDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    /**
     * 取第一个非空的来源字段（兼容 source/sourceName、reportUrl/sourceUrl 两套键名）
     */
    private Object firstNonNull(Object primary, Object fallback) {
        return primary != null ? primary : fallback;
    }

    /**
     * 文本分段（按中文标点分割，保留分割符）
     */
    private List<String> splitTextToParagraphs(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        // 分割文本（保留分割符）
        String[] parts = text.split("(" + PARAGRAPH_SEPARATOR + ")");
        List<String> paragraphs = new ArrayList<>();
        StringBuilder currentPara = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            currentPara.append(parts[i]);
            // 每两个元素合并为一个段落（parts[i]为内容，parts[i+1]为分割符）
            if (i % 2 == 1 || i == parts.length - 1) {
                String para = currentPara.toString().trim();
                if (!para.isEmpty()) {
                    paragraphs.add(para);
                }
                currentPara.setLength(0);
            }
        }
        return paragraphs;
    }
}
