package com.abin.checkrepeatservice.common.service;

import com.abin.checkrepeatservice.pojo.entity.CheckPolicy;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;

import java.math.BigDecimal;

/**
 * 检测策略解析器（P5）：按论文学位类型命中 check_policy 检测策略集。
 *
 * 策略集由超管端维护（/api/super/check-policy/**），每个 degree_type 一行；
 * 命中 enabled=1 的策略时，合格阈值以策略为准，未命中时回落
 * {@link SimilarityThresholdResolver} 既有链路（学科 &gt; 专业 &gt; 学院 &gt; 全局 &gt; yml 兜底），
 * 保证"没配策略集"的系统行为与引入本类之前完全一致。
 */
public interface CheckPolicyResolver {

    /**
     * 从论文上下文解析生效阈值。
     * degree_type 非空且命中 enabled=1 的 check_policy 行 → 返回该策略 threshold；
     * 否则（学位类型为空/未命中/查询异常）→ 回落 SimilarityThresholdResolver.resolveForPaper。
     */
    BigDecimal resolveForPaper(PaperInfo paperInfo);

    /**
     * 返回命中的完整策略行（含 max_check_count/check_interval 等扩展字段），未命中返回 null。
     * 供后续按学位类型差异化"查重次数/间隔"时扩展使用。
     */
    CheckPolicy getCurrentPolicy(PaperInfo paperInfo);
}
