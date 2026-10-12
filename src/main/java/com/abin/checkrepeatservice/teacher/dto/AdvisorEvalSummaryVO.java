package com.abin.checkrepeatservice.teacher.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 教师端导师评价汇总视图对象（论文全生命周期 P6a）。
 *
 * 匿名保护：评价份数 < 3 时 masked=true 且各均分字段为 null（小样本可被反推身份，
 * 行业通行阈值）；教师端永不返回单条评价明细与文字评语，仅聚合结果。
 */
@Data
public class AdvisorEvalSummaryVO {

    /**
     * 小样本掩码：true 表示样本不足（<3份），前端显示「样本不足，暂不展示」
     */
    private Boolean masked;

    /**
     * 已收到的评价份数（份数本身不敏感，照常返回）
     */
    private Long evalCount;

    /**
     * 指导及时性均分（1-5，两位小数）
     */
    private BigDecimal avgTimeliness;

    /**
     * 反馈质量均分（1-5，两位小数）
     */
    private BigDecimal avgQuality;

    /**
     * 责任心态度均分（1-5，两位小数）
     */
    private BigDecimal avgAttitude;

    /**
     * 专业能力均分（1-5，两位小数）
     */
    private BigDecimal avgProfessional;

    /**
     * 总体满意度均分（1-5，两位小数）
     */
    private BigDecimal avgOverall;

    /**
     * 导师评价分（百分制 = avgOverall/5×100，一位小数）
     */
    private BigDecimal advisorScore;

    /**
     * 标签频次统计（按次数降序；掩码时为空列表）
     */
    private List<TagStat> tagStats;

    /**
     * 标签频次项
     */
    @Data
    public static class TagStat {
        /**
         * 标签值（字典 advisor_eval_tag 的 dict_value）
         */
        private String tag;
        /**
         * 标签中文名
         */
        private String label;
        /**
         * 被选中次数
         */
        private Long count;
    }
}
