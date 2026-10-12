package com.abin.checkrepeatservice.teacher.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 教师端指导看板响应（论文全生命周期 P5，GET /api/teacher/dashboard/guidance-progress）。
 *
 * 结构约定：
 *  - statusSummary：状态分布 [{paperStatus, count}]，Java 内存分组产出
 *    （规避 DB 端 GROUP BY 在 only_full_group_by 模式下的口径问题）；
 *  - total：当前教师名下未删除论文总数（全量口径，不受 papers 截断影响）；
 *  - papers：最近 50 条论文明细（create_time desc）。
 *
 * 状态取值域见 DictConstants.PaperStatus（pending/assigned/checking/auditing/completed/
 * reviewing/mid_defense/final_defense/defense_passed/finalized/archived/rejected/withdrawn），
 * 历史脏数据可能出现未知值，原样透出由前端字典翻译。
 */
@Data
public class GuidanceProgressVO {

    /**
     * 我指导的论文总数（is_deleted=0 全量口径）
     */
    private Long total;

    /**
     * 论文状态分布（按状态码排序，保证输出稳定）
     */
    private List<StatusSummaryItem> statusSummary;

    /**
     * 最近论文明细（最近 50 条，create_time desc）
     */
    private List<PaperItem> papers;

    /**
     * 状态分布条目
     */
    @Data
    public static class StatusSummaryItem {

        /**
         * 论文状态码（字典 paper_status）
         */
        private String paperStatus;

        /**
         * 该状态下论文数
         */
        private Long count;
    }

    /**
     * 论文明细条目
     */
    @Data
    public static class PaperItem {

        /**
         * 论文ID（雪花ID，序列化为字符串防前端精度丢失）
         */
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long paperId;

        /**
         * 论文标题
         */
        private String paperTitle;

        /**
         * 学生姓名（realName，空则回落 username）
         */
        private String studentName;

        /**
         * 论文状态码（字典 paper_status）
         */
        private String paperStatus;

        /**
         * 最近一次查重总相似比（paper_info.similarity_rate，可能为空=未查重）
         */
        private BigDecimal similarityRate;

        /**
         * 总评成绩（加权合成回写，可能为空=未合成）
         */
        private BigDecimal finalScore;
    }
}
