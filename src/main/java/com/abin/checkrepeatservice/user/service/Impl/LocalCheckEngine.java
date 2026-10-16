package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.cache.DetectionCacheService;
import com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum;
import com.abin.checkrepeatservice.common.service.PaperContentMinioService;
import com.abin.checkrepeatservice.common.utils.IKAnalyzerUtils;
import com.abin.checkrepeatservice.common.utils.SentenceSplitterUtils;
import com.abin.checkrepeatservice.common.utils.TextSimilarityUtils;
import com.abin.checkrepeatservice.detection.dto.SentenceHit;
import com.abin.checkrepeatservice.detection.service.SentenceLevelAnalyzer;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.vo.CheckResult;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.user.service.CheckEngine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class LocalCheckEngine implements CheckEngine {
    
    @Value("${check.local.enabled:true}")
    private boolean enabled;
    
    @Value("${check.local.cache-enabled:true}")
    private boolean cacheEnabled;

    @Resource
    private PaperInfoMapper paperInfoMapper; // 论文信息Mapper
    @Resource
    private TextSimilarityUtils textSimilarityUtils; // 现有相似度工具类
    @Resource
    private PaperContentMinioService paperContentMinioService; // MinIO存储服务
    @Resource
    private DetectionCacheService detectionCacheService; // 检测缓存（性能配置页 cacheStrategy/cacheSize 的落地组件）
    @Resource
    private SentenceLevelAnalyzer sentenceLevelAnalyzer; // P5 句级相似度分析器（候选论文句级标注 + 三项指标）

    @PostConstruct
    public void init() {
        log.info("本地查重引擎初始化完成，enabled={}, cacheEnabled={}", enabled, cacheEnabled);
    }

    @Override
    public CheckEngineTypeEnum getEngineType() {
        return CheckEngineTypeEnum.LOCAL;
    }

    @Override
    public CheckResult check(String paperContent, String paperTitle) {
        return check(paperContent, paperTitle, null);
    }

    @Override
    public CheckResult check(String paperContent, String paperTitle, Long excludePaperId) {
        CheckResult result = new CheckResult();

        // ===== P5 引文排除（前置）：剔除参考文献区块后再分词，避免参考文献相似污染分词向量 =====
        // 无参考文献区块的文档整体参与比对（降级策略见 SentenceSplitterUtils 注释）
        int refsIndex = SentenceSplitterUtils.indexOfReferencesBlock(paperContent);
        boolean hasReferencesBlock = refsIndex >= 0;
        String bodyText = hasReferencesBlock ? paperContent.substring(0, refsIndex) : paperContent;

        // 1. 预处理待查重论文（IK分词+SimHash计算）—— 基于剔除参考文献后的正文
        String segmentedTargetText = IKAnalyzerUtils.segmentToString(bodyText);
        if (segmentedTargetText.isEmpty()) {
            throw new RuntimeException("论文内容为空或分词后无有效内容");
        }
        BigInteger targetSimHash = textSimilarityUtils.calculateSimHash(segmentedTargetText);

        // P5 句级切分（正文），并记录每句是否引用句：
        // 仅当文档存在参考文献区块时才启用 [n] 引注标记，否则一律按普通文本计重
        List<SentenceSplitterUtils.SentenceSpan> bodySentences = SentenceSplitterUtils.split(bodyText);
        Map<Integer, Boolean> citationFlags = new HashMap<>();
        for (SentenceSplitterUtils.SentenceSpan span : bodySentences) {
            citationFlags.put(span.getStartOffset(),
                    hasReferencesBlock && SentenceSplitterUtils.endsWithCitation(span.getText()));
        }

        // 2. 查询比对库论文（架构修复 P0）：
        //    - 只纳入 content_path 已回填（正文真实入库）的论文，此前 selectList(null)
        //      把全表（含从未入库的论文）扫进来，每篇 readPaperContent(null) 必抛异常，
        //      异常被逐篇吞掉后比对结果恒为 0；
        //    - 排除待查论文自身（正文在比对前已入库，不排除则自我相似度必然 100%）。
        //    比对库准入（仅定稿/归档稿）待 P4 归档落地后在此追加状态条件。
        LambdaQueryWrapper<PaperInfo> libraryWrapper = new LambdaQueryWrapper<PaperInfo>()
                .isNotNull(PaperInfo::getContentPath)
                .ne(PaperInfo::getContentPath, "");
        if (excludePaperId != null) {
            libraryWrapper.ne(PaperInfo::getId, excludePaperId);
        }
        List<PaperInfo> paperList = paperInfoMapper.selectList(libraryWrapper);
        double maxSimilarity = 0.0;
        String mostSimilarPaperTitle = "";
        BigInteger mostSimilarSimHash = BigInteger.ZERO;
        // P5：整篇海明预筛通过的候选论文（句级分析只对候选做，避免全库 O(N×M)）
        List<SentenceLevelAnalyzer.CandidatePaper> sentenceCandidates = new ArrayList<>();

        // 3. 遍历论文信息，逐一比对（整篇粗筛）
        for (PaperInfo paper : paperList) {
            try {
                // 取库中论文比对数据（分词文本 + SimHash）：
                // 走检测缓存，省掉每次查重对全库论文的 MinIO 读取、IK 分词与 SimHash 重算；
                // 缓存键为 contentPath（含随机 UUID），论文重新入库即换新键，天然自失效。
                // cache-enabled=false 时绕过缓存，每次现算（保留原行为入口）。
                DetectionCacheService.LibraryPaperData libraryData;
                if (cacheEnabled) {
                    libraryData = detectionCacheService.getOrLoad(
                            paper.getContentPath(), () -> loadLibraryPaperData(paper));
                } else {
                    libraryData = loadLibraryPaperData(paper);
                }
                if (libraryData == null) {
                    continue;
                }

                // 3.1 SimHash快速筛选（海明距离超过阈值，直接跳过）
                int hammingDistance = textSimilarityUtils.calculateHammingDistance(targetSimHash, libraryData.getSimHash());
                if (hammingDistance > textSimilarityUtils.getHammingThreshold()) {
                    continue; // 低相似，无需计算余弦相似度
                }

                // 3.2 P5：整篇预筛通过 → 收集为句级分析候选（现场读原文；单篇读取失败仅损失该篇句级明细）
                try {
                    String rawContent = paperContentMinioService.readPaperContent(paper.getContentPath());
                    if (rawContent != null && !rawContent.isEmpty()) {
                        sentenceCandidates.add(new SentenceLevelAnalyzer.CandidatePaper(
                                paper.getId(), paper.getPaperTitle(), rawContent));
                    }
                } catch (Exception ex) {
                    log.warn("句级候选原文读取失败（ID：{}）：{}", paper.getId(), ex.getMessage());
                }

                // 3.3 余弦相似度精细计算
                double similarity = textSimilarityUtils.calculateCosineSimilarity(segmentedTargetText, libraryData.getSegmentedText());

                // 3.4 记录最高相似度
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    mostSimilarPaperTitle = paper.getPaperTitle(); // 替代 getTitle()
                    mostSimilarSimHash = libraryData.getSimHash();
                }
            } catch (Exception e) {
                log.error("比对库中论文失败（ID：{}）：", paper.getId(), e);
                continue; // 单篇论文比对失败，不影响整体流程
            }
        }

        // ===== P5 句级分析：候选论文句级标注 + 总相似比/单篇最大相似比/引用率 =====
        List<SentenceHit> sentenceHits = new ArrayList<>();
        BigDecimal totalSimilarityRate = null;
        BigDecimal maxPerSource = null;
        BigDecimal citationRate = null;
        try {
            SentenceLevelAnalyzer.AnalysisResult analysis = sentenceLevelAnalyzer.analyze(bodySentences, sentenceCandidates);
            if (analysis.getBodyCharCount() > 0) {
                for (SentenceHit hit : analysis.getHits()) {
                    hit.setIsCitation(citationFlags.getOrDefault(hit.getStartIndex(), Boolean.FALSE));
                    sentenceHits.add(hit);
                }
                totalSimilarityRate = analysis.getTotalSimilarityRate();
                maxPerSource = analysis.getMaxPerSource();
                citationRate = analysis.getCitationRate();
            }
        } catch (Exception e) {
            // 句级分析失败降级：整篇指标照常返回，落库口径回落旧值（similarity），句级明细为空
            log.error("句级相似度分析失败，本次查重降级为整篇口径。论文标题：{}", paperTitle, e);
        }

        // 4. 封装查重结果
        BigDecimal similarityPercent = BigDecimal.valueOf(maxSimilarity * 100)
                .setScale(2, BigDecimal.ROUND_HALF_UP);
        result.setSimilarity(similarityPercent);
        // P5 一等指标：落库与阈值判定以 totalSimilarityRate 为准；
        // 句级分析不可用（无正文句/异常）时回落整篇余弦旧口径，保证 check_rate 不为空
        result.setTotalSimilarityRate(totalSimilarityRate != null ? totalSimilarityRate : similarityPercent);
        result.setMaxPerSource(maxPerSource != null ? maxPerSource : similarityPercent);
        result.setCitationRate(citationRate != null ? citationRate : BigDecimal.ZERO);
        result.setSentenceHits(sentenceHits);
        result.setCheckSource("论文信息库（SimHash+余弦相似度）");
        result.setReportUrl("/local/report/" + System.currentTimeMillis());
        result.setExtraInfo(String.format(
                "最高相似度论文：%s\nSimHash海明距离：%d\n整篇相似度：%.2f%%\n总相似比：%.2f%%\n单篇最大相似比：%.2f%%\n引用率：%.2f%%\n命中句数：%d\n比对论文总数：%d",
                mostSimilarPaperTitle,
                textSimilarityUtils.calculateHammingDistance(targetSimHash, mostSimilarSimHash),
                similarityPercent,
                result.getTotalSimilarityRate(),
                result.getMaxPerSource(),
                result.getCitationRate(),
                sentenceHits.size(),
                paperList.size()
        ));
        result.setSuccess(true);

        return result;
    }

    /**
     * 加载单篇库内论文的比对数据（MinIO 读正文 → 读/现算分词 → SimHash）。
     * 语义与重构前逐篇处理完全一致：
     * <ul>
     *   <li>正文为空 → 返回 null（跳过该篇）</li>
     *   <li>分词结果为空 → 用正文实时 IK 分词；仍为空 → 返回 null</li>
     *   <li>MinIO 读取异常 → 向上抛出，由比对循环按单篇失败处理</li>
     * </ul>
     */
    private DetectionCacheService.LibraryPaperData loadLibraryPaperData(PaperInfo paper) throws Exception {
        // 从MinIO读取库中论文内容
        String libraryContent = paperContentMinioService.readPaperContent(paper.getContentPath());
        if (libraryContent == null || libraryContent.isEmpty()) {
            return null;
        }

        // 预处理库中论文（使用MinIO中的内容进行分词）
        String segmentedLibraryText = paperContentMinioService.readSegmentedText(paper.getSegmentedPath());
        if (segmentedLibraryText == null || segmentedLibraryText.isEmpty()) {
            // 如果MinIO中没有分词结果，则实时处理
            segmentedLibraryText = IKAnalyzerUtils.segmentToString(libraryContent);
        }

        if (segmentedLibraryText.isEmpty()) {
            return null;
        }

        BigInteger librarySimHash = textSimilarityUtils.calculateSimHash(segmentedLibraryText);
        return new DetectionCacheService.LibraryPaperData(segmentedLibraryText, librarySimHash);
    }
}