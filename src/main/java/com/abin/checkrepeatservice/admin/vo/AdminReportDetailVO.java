package com.abin.checkrepeatservice.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员报告详情VO（扁平结构，对齐前端 ReportManagement.vue 详情弹窗的字段契约）
 * <p>
 * 不改造 student/dto/ReportPreviewDTO：学生端/教师端的报告预览页依赖其嵌套结构
 * （baseInfo/rateStat/paragraphs/similarSources），此处只做一次扁平映射。
 */
@Data
public class AdminReportDetailVO {

    /**
     * 报告编号
     */
    private String reportNo;

    /**
     * 论文标题
     */
    private String paperTitle;

    /**
     * 学生姓名
     */
    private String studentName;

    /**
     * 学生ID（雪花ID，序列化为字符串）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long studentId;

    /**
     * 指导教师姓名
     */
    private String teacherName;

    /**
     * 查重时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkTime;

    /**
     * 查重状态（pending/checking/completed/failure）
     */
    private String status;

    /**
     * 总相似度（百分比）
     */
    private BigDecimal similarity;

    /**
     * 论文总字数
     */
    private Integer wordCount;

    /**
     * 相似字数
     * 注：重复详情JSON中无对应字段，此处按重复段落的文本长度累加估算，可能为 null
     */
    private Integer similarWords;

    /**
     * 相似来源数量
     */
    private Integer similarSources;

    /**
     * 相似来源明细
     */
    private List<SimilarSource> similarSourceList;

    /**
     * 相似来源明细项
     */
    @Data
    public static class SimilarSource {
        /**
         * 来源名称
         */
        private String title;

        /**
         * 作者
         * 注：重复详情JSON中无作者字段，恒为 null（前端留空展示）
         */
        private String author;

        /**
         * 与该来源的最大相似度
         */
        private Double similarity;

        /**
         * 匹配到的段落
         */
        private List<MatchedParagraph> matchedParagraphs;
    }

    /**
     * 匹配段落
     */
    @Data
    public static class MatchedParagraph {
        /**
         * 来源原文片段
         */
        private String sourceText;

        /**
         * 本文对应段落内容
         */
        private String paperText;

        /**
         * 该段落匹配度
         */
        private Double similarity;
    }
}
