package com.abin.checkrepeatservice.student.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学生端"当前查重规则"展示 VO。
 *
 * <p>与 CheckRule 实体的差别只在 passThreshold：实体上的 pass_threshold 列已废弃
 * （不参与合格判定，编辑分支也不写），真值源是 similarity_threshold /
 * category_similarity_threshold 两张表，由 SimilarityThresholdResolver 按
 * 「专业 &gt; 学院 &gt; 全局 &gt; yml 兜底」解析。学生看到的阈值必须是这个解析结果，
 * 否则页面上显示的合格线会和实际判定用的对不上。</p>
 */
@Data
public class StudentCheckRuleVO {

    /** 规则名称 */
    private String ruleName;

    /** 规则编码 */
    private String ruleCode;

    /** 规则描述 */
    private String description;

    /** 该生当前生效的通过阈值（百分比，如 20 表示重复率需 ≤20%） */
    private BigDecimal passThreshold;

    /** 最大查重次数，null 表示不限制 */
    private Integer maxCheckCount;

    /** 最大重提次数，null 表示不限制 */
    private Integer maxReSubmitCount;

    /** 两次查重的最小间隔（秒），null 表示不限制 */
    private Integer checkInterval;
}
