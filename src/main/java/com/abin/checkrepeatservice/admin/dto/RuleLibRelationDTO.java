package com.abin.checkrepeatservice.admin.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 规则与比对库关联DTO：用于返回规则关联的库信息
 */
@Data
public class RuleLibRelationDTO {
    /**
     * 规则基础信息
     */
    private CheckRuleBaseDTO ruleBase;

    /**
     * 关联的比对库列表
     */
    private List<CompareLibBaseDTO> relatedLibs;

    // ------------------------------ 内部DTO ------------------------------
    @Data
    public static class CheckRuleBaseDTO {
        private Long ruleId;
        private String ruleName;
        private String ruleCode;
        /**
         * @deprecated 不参与合格判定，仅为兼容既有调用方而保留。
         * 阈值真值源见 SimilarityThresholdResolver。
         */
        @Deprecated
        private BigDecimal passThreshold;
        private Integer isDefault;
        private String description;
    }

    @Data
    public static class CompareLibBaseDTO {
        private Long libId;
        private String libName;
        private String libCode;
        private String libType;
        private Integer isEnabled;
    }
}
