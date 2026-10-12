package com.abin.checkrepeatservice.superadmin.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 检测策略保存请求（P5 超管端）：按 degreeType 新增或更新，一个学位类型一行策略
 */
@Data
public class CheckPolicySaveReq {

    /**
     * 学位类型：undergraduate-本科 / master-硕士 / doctor-博士（必填，唯一键）
     */
    private String degreeType;

    /**
     * 合格阈值（总相似比百分比，必填，0-100）
     */
    private BigDecimal threshold;

    /**
     * 单篇最大相似比上限（百分比，必填，0-100）
     */
    private BigDecimal maxPerSource;

    /**
     * 每篇论文最多查重次数（可空=沿用全局配置）
     */
    private Integer maxCheckCount;

    /**
     * 两次查重间隔秒数（可空=沿用全局配置）
     */
    private Integer checkInterval;

    /**
     * 是否启用（1-启用 0-停用，缺省 1）
     */
    private Integer enabled;
}
