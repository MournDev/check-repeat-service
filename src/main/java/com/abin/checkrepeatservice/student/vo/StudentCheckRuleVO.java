package com.abin.checkrepeatservice.student.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生端"当前查重规则"展示 VO。
 *
 * <p>与 CheckRule 实体的差别只在 passThreshold：实体上的 pass_threshold 列已废弃
 * （不参与合格判定，编辑分支也不写），真值源是 similarity_threshold /
 * category_similarity_threshold 两张表，由 SimilarityThresholdResolver 按
 * 「学科 &gt; 专业 &gt; 学院 &gt; 仅类型 &gt; 全局 &gt; yml 兜底」解析。
 * 学生看到的阈值必须是这个解析结果，否则页面上显示的合格线会和实际判定用的对不上。</p>
 *
 * <p>阈值现在还能按论文类型分档，所以真正的答案不是一个数而是一组数 —— 见
 * {@link #paperTypeThresholds}。{@link #passThreshold} 是"不带类型"的那个值，
 * 保留它只为后端/前端灰度期不同步时页面不至于空掉。</p>
 */
@Data
public class StudentCheckRuleVO {

    /** 规则名称 */
    private String ruleName;

    /** 规则编码 */
    private String ruleCode;

    /** 规则描述 */
    private String description;

    /**
     * 该生当前生效的通过阈值（百分比，如 20 表示重复率需 ≤20%）。
     *
     * <p>不带论文类型的解析结果。论文类型的档位已经接入判定，所以这个值
     * 只对"解析不出类型"的论文成立 —— 前端应优先展示 paperTypeThresholds。</p>
     */
    private BigDecimal passThreshold;

    /**
     * 按论文类型分档的通过阈值，每种枚举值一条（顺序即 PaperTypeEnum 声明顺序）。
     *
     * <p>学生在提交前就该知道"我这篇按多少算合格"，而查重管理页是学生级的、
     * 没有单篇论文上下文，所以这里一次把各类型都算出来给页面并列展示。</p>
     */
    private List<PaperTypeThreshold> paperTypeThresholds;

    /** 最大查重次数，null 表示不限制 */
    private Integer maxCheckCount;

    /** 最大重提次数，null 表示不限制 */
    private Integer maxReSubmitCount;

    /** 两次查重的最小间隔（秒），null 表示不限制 */
    private Integer checkInterval;

    /**
     * 单个论文类型下的生效阈值。
     */
    @Data
    public static class PaperTypeThreshold {

        /** 论文类型 code（PaperTypeEnum） */
        private String paperType;

        /** 论文类型展示名。由后端给出，前端不再自持一份 code→中文 的映射 */
        private String paperTypeDesc;

        /** 该类型下的通过阈值（百分比） */
        private BigDecimal passThreshold;
    }
}
