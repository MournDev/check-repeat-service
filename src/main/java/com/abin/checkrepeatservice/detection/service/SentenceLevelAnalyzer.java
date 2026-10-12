package com.abin.checkrepeatservice.detection.service;

import com.abin.checkrepeatservice.common.utils.IKAnalyzerUtils;
import com.abin.checkrepeatservice.common.utils.SentenceSplitterUtils;
import com.abin.checkrepeatservice.common.utils.TextSimilarityUtils;
import com.abin.checkrepeatservice.detection.dto.SentenceHit;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 句级相似度分析器（论文全生命周期 P5）
 *
 * 定位：整篇 SimHash 海明预筛（LocalCheckEngine 已有）筛出候选论文后，
 * 仅对候选论文做句级比对——不做全库 O(N×M)。
 *
 * 算法：
 * 1. 候选论文现场 MinIO 读原文 → 切句 → 逐句 IK 分词 + 句级 SimHash（短句跳过）；
 * 2. 目标句逐句：句级 SimHash 与候选句做海明快筛（复用 check.simhash.hamming-threshold），
 *    通过者再算余弦精算（IK 分词词频余弦，与整篇口径一致）；
 * 3. 每个目标句只保留全局最优命中（跨所有候选论文取余弦最大者）；
 * 4. 最优句相似度 ≥ check.sentence.threshold（默认 50%）判定为命中。
 *
 * 指标口径（字数权重 = 句去空白字符长度）：
 * - 总相似比 totalSimilarityRate = 非引用命中句字数 / 正文字数 × 100
 * - 引用率 citationRate          = 引用命中句字数 / 正文字数 × 100
 * - 单篇最大相似比 maxPerSource  = 单篇来源非引用命中字数 / 正文字数 × 100 的最大值
 */
@Component
@Slf4j
public class SentenceLevelAnalyzer {

    /**
     * 句命中判定阈值（%）：最优句余弦相似度达到该值才记为命中
     */
    @Value("${check.sentence.threshold:50}")
    private double sentenceHitThresholdPercent;

    /**
     * 候选论文数安全上限：整篇海明预筛通常仅命中个位数~几十篇，
     * 超限截断防止极端数据把句级比对拖垮（多出的候选放弃句级分析，整篇指标不受影响）
     */
    @Value("${check.sentence.max-candidates:50}")
    private int maxCandidates;

    @Resource
    private TextSimilarityUtils textSimilarityUtils;

    /**
     * 候选论文：整篇海明预筛通过、需要做句级比对的库内论文
     */
    @Data
    @AllArgsConstructor
    public static class CandidatePaper {
        private Long paperId;
        private String paperTitle;
        /**
         * 库内论文原文（调用方已从 MinIO 读取；LocalCheckEngine 预筛阶段本身要读
         * 分词文本，原文在此统一传入避免二次 IO）
         */
        private String rawContent;
    }

    /**
     * 分析结果：句命中明细 + 三项一等指标
     */
    @Data
    public static class AnalysisResult {
        private List<SentenceHit> hits = new ArrayList<>();
        /** 总相似比（%）：非引用命中句字数/正文字数 */
        private BigDecimal totalSimilarityRate = BigDecimal.ZERO;
        /** 单篇最大相似比（%） */
        private BigDecimal maxPerSource = BigDecimal.ZERO;
        /** 引用率（%） */
        private BigDecimal citationRate = BigDecimal.ZERO;
        /** 正文字数（去空白字符，全部参与句的分母） */
        private int bodyCharCount = 0;
    }

    /**
     * 句级比对入口
     *
     * @param targetSentences 目标论文正文句（调用方用 SentenceSplitterUtils.split 切出，
     *                        句文本若句尾带 [n] 引注则由调用方标记 isCitation——仅当文档存在参考文献区块）
     * @param candidates      候选论文列表（含原文）
     */
    public AnalysisResult analyze(List<SentenceSplitterUtils.SentenceSpan> targetSentences,
                                  List<CandidatePaper> candidates) {
        AnalysisResult output = new AnalysisResult();
        if (targetSentences == null || targetSentences.isEmpty() || candidates == null || candidates.isEmpty()) {
            output.setBodyCharCount(sumBodyChars(targetSentences));
            return output;
        }
        output.setBodyCharCount(sumBodyChars(targetSentences));

        // 1. 候选论文切句 + 预分词 + 句级 SimHash（一次构建，全程复用）
        List<CandidateSentences> candidateIndexes = buildCandidateIndexes(candidates);
        if (candidateIndexes.isEmpty()) {
            return output;
        }

        // 2. 逐目标句比对
        for (int i = 0; i < targetSentences.size(); i++) {
            SentenceSplitterUtils.SentenceSpan span = targetSentences.get(i);
            String targetTokens = IKAnalyzerUtils.segmentToString(span.getText());
            if (targetTokens.isEmpty()) {
                continue;
            }
            BigInteger targetHash = safeSimHash(targetTokens);
            if (targetHash == null) {
                continue;
            }

            Double bestSimilarity = null;
            CandidatePaper bestPaper = null;
            int hammingThreshold = textSimilarityUtils.getHammingThreshold();

            for (CandidateSentences candidate : candidateIndexes) {
                for (int s = 0; s < candidate.tokens.size(); s++) {
                    // 句级海明快筛：距离过大直接跳过，避免全量余弦
                    int hamming = textSimilarityUtils.calculateHammingDistance(targetHash, candidate.simHashes.get(s));
                    if (hamming > hammingThreshold) {
                        continue;
                    }
                    double cosine = textSimilarityUtils.calculateCosineSimilarity(
                            targetTokens, candidate.tokens.get(s)) * 100;
                    if (bestSimilarity == null || cosine > bestSimilarity) {
                        bestSimilarity = cosine;
                        bestPaper = candidate.paper;
                    }
                }
            }

            if (bestSimilarity != null && bestSimilarity >= sentenceHitThresholdPercent) {
                SentenceHit hit = new SentenceHit();
                hit.setSentenceText(truncate(span.getText()));
                hit.setStartIndex(span.getStartOffset());
                hit.setSimilarity(round2(bestSimilarity));
                hit.setHitPaperId(bestPaper.getPaperId());
                hit.setHitPaperTitle(bestPaper.getPaperTitle());
                output.getHits().add(hit);
            }
        }

        // 3. 指标汇总
        computeMetrics(output, targetSentences);
        return output;
    }

    /**
     * 指标汇总（口径见类注释；bodyCharCount 为分母，0 时全部指标为 0）
     */
    private void computeMetrics(AnalysisResult output, List<SentenceSplitterUtils.SentenceSpan> targetSentences) {
        int bodyChars = output.getBodyCharCount();
        if (bodyChars <= 0) {
            return;
        }
        // 句偏移 → 字数权重（hit 只有 startOffset，用偏移定位回 span 拿长度）
        Map<Integer, Integer> offsetToCharCount = new HashMap<>();
        for (SentenceSplitterUtils.SentenceSpan span : targetSentences) {
            offsetToCharCount.put(span.getStartOffset(), SentenceSplitterUtils.charCount(span.getText()));
        }

        int nonCitationHitChars = 0;
        int citationHitChars = 0;
        Map<Long, Integer> perSourceChars = new HashMap<>();

        for (SentenceHit hit : output.getHits()) {
            int chars = offsetToCharCount.getOrDefault(hit.getStartIndex(), 0);
            boolean citation = Boolean.TRUE.equals(hit.getIsCitation());
            if (citation) {
                citationHitChars += chars;
            } else {
                nonCitationHitChars += chars;
                if (hit.getHitPaperId() != null) {
                    perSourceChars.merge(hit.getHitPaperId(), chars, Integer::sum);
                }
            }
        }

        output.setTotalSimilarityRate(round2(nonCitationHitChars * 100.0 / bodyChars));
        output.setCitationRate(round2(citationHitChars * 100.0 / bodyChars));
        int maxSourceChars = perSourceChars.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        output.setMaxPerSource(round2(maxSourceChars * 100.0 / bodyChars));
    }

    private List<CandidateSentences> buildCandidateIndexes(List<CandidatePaper> candidates) {
        List<CandidateSentences> indexes = new ArrayList<>();
        int limit = Math.min(candidates.size(), maxCandidates);
        if (limit < candidates.size()) {
            log.warn("句级分析候选论文数 {} 超过上限 {}，仅分析前 {} 篇", candidates.size(), maxCandidates, limit);
        }
        for (int i = 0; i < limit; i++) {
            CandidatePaper candidate = candidates.get(i);
            try {
                CandidateSentences index = buildOne(candidate);
                if (index != null) {
                    indexes.add(index);
                }
            } catch (Exception e) {
                // 单篇候选失败不拖垮整体句级分析
                log.error("候选论文句级索引构建失败（ID：{}）：", candidate.getPaperId(), e);
            }
        }
        return indexes;
    }

    private CandidateSentences buildOne(CandidatePaper candidate) {
        List<String> tokens = new ArrayList<>();
        List<BigInteger> simHashes = new ArrayList<>();
        for (SentenceSplitterUtils.SentenceSpan span : SentenceSplitterUtils.split(candidate.getRawContent())) {
            String sentenceTokens = IKAnalyzerUtils.segmentToString(span.getText());
            if (sentenceTokens.isEmpty()) {
                continue;
            }
            BigInteger simHash = safeSimHash(sentenceTokens);
            if (simHash == null) {
                continue;
            }
            tokens.add(sentenceTokens);
            simHashes.add(simHash);
        }
        if (tokens.isEmpty()) {
            return null;
        }
        return new CandidateSentences(candidate, tokens, simHashes);
    }

    private int sumBodyChars(List<SentenceSplitterUtils.SentenceSpan> targetSentences) {
        if (targetSentences == null) {
            return 0;
        }
        int total = 0;
        for (SentenceSplitterUtils.SentenceSpan span : targetSentences) {
            total += SentenceSplitterUtils.charCount(span.getText());
        }
        return total;
    }

    private BigInteger safeSimHash(String tokens) {
        try {
            return textSimilarityUtils.calculateSimHash(tokens);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    private BigDecimal round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 单篇候选论文的句级索引（tokens 与 simHash 下标一一对应）
     */
    @AllArgsConstructor
    private static class CandidateSentences {
        private final CandidatePaper paper;
        private final List<String> tokens;
        private final List<BigInteger> simHashes;
    }
}
