package com.abin.checkrepeatservice.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端导师评价统计视图对象（论文全生命周期 P6a）。
 *
 * 构成：完成率（可评论文数 vs 已评份数）+ 导师排行（含低分预警标记）
 * + 学院对比 + 低分预警名单（样本≥3 且总体均分 <3.0 的导师）。
 */
@Data
public class AdvisorEvalStatsVO {

    /**
     * 可评论文数（paper_status ∈ defense_passed/finalized 且已分配导师）
     */
    private Long eligiblePapers;

    /**
     * 已提交评价份数（= 已评论文数，uk_paper_id 保证一一对应）
     */
    private Long totalEvaluations;

    /**
     * 评价完成率（百分比，两位小数；eligiblePapers=0 时为 0）
     */
    private BigDecimal completionRate;

    /**
     * 导师评价排行（按导师评价分降序）
     */
    private List<TeacherRankItem> teacherRank;

    /**
     * 学院对比（按总体均分降序）
     */
    private List<CollegeCompareItem> collegeCompare;

    /**
     * 低分预警导师（evalCount≥3 且 avgOverall<3.0，从 teacherRank 过滤）
     */
    private List<TeacherRankItem> lowScoreTeachers;

    /**
     * 导师排行项
     */
    @Data
    public static class TeacherRankItem {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long teacherId;

        /**
         * 导师姓名（XML 只出 teacherId，Java 侧批量回填）
         */
        private String teacherName;

        private Long evalCount;

        /**
         * 总体满意度均分（1-5，两位小数）
         */
        private BigDecimal avgOverall;

        /**
         * 导师评价分（百分制 = avgOverall/5×100，一位小数）
         */
        private BigDecimal advisorScore;

        /**
         * 低分预警：evalCount≥3 且 avgOverall<3.0
         */
        private Boolean lowScore;
    }

    /**
     * 学院对比项
     */
    @Data
    public static class CollegeCompareItem {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long collegeId;

        private String collegeName;

        private Long evalCount;

        /**
         * 总体满意度均分（1-5，两位小数）
         */
        private BigDecimal avgOverall;
    }
}
