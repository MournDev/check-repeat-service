package com.abin.checkrepeatservice.common.service;

import com.abin.checkrepeatservice.pojo.entity.PaperInfo;

import java.math.BigDecimal;

/**
 * 相似度阈值解析器：把"某个学生该按多少的重复率算合格"这件事收敛到一处。
 *
 * 真值源是 similarity_threshold（全局）与 category_similarity_threshold（学院/专业）两张表。
 * 两表都为空时回落到 yml 的 admin.check-rule.default-threshold，
 * 因此"未配置任何阈值"时的判定结果与引入本类之前逐字节一致。
 *
 * 返回值是 BigDecimal 而非 int：yml 兜底值可能带小数（当前是 20.00），
 * 若在这里截断成 int，一旦有人把配置写成 20.5 判定就会偏移。
 * 保持 BigDecimal 让调用方沿用改造前的比较写法，边界语义不变。
 */
public interface SimilarityThresholdResolver {

    /**
     * 按学院/专业解析生效阈值。
     * 优先级：专业 &gt; 学院 &gt; 全局 &gt; yml 兜底。
     *
     * @param collegeId 学院ID，可为 null
     * @param majorId   专业ID，可为 null
     */
    BigDecimal resolve(Long collegeId, Long majorId);

    /**
     * 从论文上下文解析生效阈值。
     * 优先用论文自带的学院/专业；论文上缺失时回查学生资料兜底。
     * 传 null 时等同于取全局阈值。
     */
    BigDecimal resolveForPaper(PaperInfo paperInfo);

    /**
     * 当前真正生效的全局阈值（整数）。表为空时返回 yml 兜底值。
     * 供管理端页面回显、以及 check_rule.pass_threshold 的兼容镜像使用。
     */
    Integer getEffectiveGlobalThreshold();

}
