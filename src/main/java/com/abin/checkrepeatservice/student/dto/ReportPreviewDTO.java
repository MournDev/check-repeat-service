package com.abin.checkrepeatservice.student.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 查重报告预览DTO：用于在线预览（含标红段落）
 */
@Data
public class ReportPreviewDTO {
    /**
     * 报告基础信息
     */
    private ReportBaseInfoDTO baseInfo;

    /**
     * 重复率统计（饼图数据）
     */
    private ReportRateStatDTO rateStat;

    /**
     * 分段内容（含标红标记）
     */
    private List<ReportParagraphDTO> paragraphs;

    /**
     * 相似来源列表
     */
    private List<ReportSimilarSourceDTO> similarSources;

    // ------------------------------ 内部DTO ------------------------------
    /**
     * 报告基础信息
     */
    @Data
    public static class ReportBaseInfoDTO {
        private String userName;
        private String realName;
        private Long taskId;
        private String taskNo;
        private String reportNo;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long reportId;
        private Long paperId;
        private String paperTitle;
        private String author;
        private BigDecimal similarityRate;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long studentId;
        private String studentName;
        private String teacherName;
        private String checkRuleName;
        private LocalDateTime checkTime;
        private String reportDetails;
        private String generateTime; // 格式：yyyy-MM-dd HH:mm:ss
    }

    /**
     * 重复率统计（用于前端饼图）
     */
    @Data
    public static class ReportRateStatDTO {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long reportId;
        private String reportNo;
        private BigDecimal repeatRate; // 总重复率（如15.32）
        private Double originalRate; // 原创率（100 - 总重复率）
        private Integer repeatParagraphCount; // 重复段落数
        private Integer totalParagraphCount; // 总段落数
    }

    /**
     * 分段内容（含标红标记）
     */
    @Data
    public static class ReportParagraphDTO {
        private String paperTitle;
        private Integer paragraphNo; // 段落序号（1,2,3...）
        private String content; // 段落内容（标红部分用<span style="color:red">包裹）
        private Double similarity; // 该段落重复率（如0.0=原创，95.5=高度重复）
        private Boolean isRepeat; // 是否为重复段落（similarity≥5.0为true）
        private List<Long> sourceIds; // 相似来源ID（关联similarSources的sourceId）
    }

    /**
     * 相似来源信息
     */
    @Data
    public static class ReportSimilarSourceDTO {
        private Long sourceId; // 来源唯一ID
        private String sourceName; // 来源名称（如“知网期刊-《软件工程》2024年第5期”）
        private String sourceType; // 来源类型（期刊/学位论文/互联网资源）
        private String matchedParagraphs;// 匹配的段落
        private String sourceUrl; // 来源链接（可选，如知网链接）
        private Double maxSimilarity; // 与该来源的最大相似度
    }
}
