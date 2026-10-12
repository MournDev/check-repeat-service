package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.CheckPolicyMapper;
import com.abin.checkrepeatservice.common.service.CheckPolicyResolver;
import com.abin.checkrepeatservice.common.service.SimilarityThresholdResolver;
import com.abin.checkrepeatservice.pojo.entity.CheckPolicy;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 检测策略解析实现（P5）：check_policy（degree_type + enabled=1）&gt; SimilarityThresholdResolver 既有链路。
 *
 * 策略未命中不是异常而是常态（系统初期可能一行都没配），所以本类在任何"取不到策略"
 * 的分支都静默回落原解析器，判定结果与引入策略集之前逐字节一致。
 * 查询异常同样回落：策略集读不出来不应拖垮查重主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckPolicyResolverImpl implements CheckPolicyResolver {

    private final CheckPolicyMapper checkPolicyMapper;

    private final SimilarityThresholdResolver similarityThresholdResolver;

    @Override
    public BigDecimal resolveForPaper(PaperInfo paperInfo) {
        CheckPolicy policy = getCurrentPolicy(paperInfo);
        if (policy != null && policy.getThreshold() != null) {
            return policy.getThreshold();
        }
        return similarityThresholdResolver.resolveForPaper(paperInfo);
    }

    @Override
    public CheckPolicy getCurrentPolicy(PaperInfo paperInfo) {
        if (paperInfo == null || paperInfo.getDegreeType() == null || paperInfo.getDegreeType().isBlank()) {
            return null;
        }
        try {
            // degree_type 唯一，orderByDesc + LIMIT 1 仅为防御脏数据下多行时取最新一条；
            // BaseEntity 的 @TableLogic 会自动拼 is_deleted = 0
            return checkPolicyMapper.selectOne(new LambdaQueryWrapper<CheckPolicy>()
                    .eq(CheckPolicy::getDegreeType, paperInfo.getDegreeType().trim())
                    .eq(CheckPolicy::getEnabled, 1)
                    .orderByDesc(CheckPolicy::getId)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            log.warn("检测策略集查询失败，回落相似度阈值解析链路: degreeType={}, error={}",
                    paperInfo.getDegreeType(), e.getMessage());
            return null;
        }
    }
}
