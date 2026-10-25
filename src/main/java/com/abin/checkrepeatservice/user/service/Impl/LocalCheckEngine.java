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
import org.springframework.util.StringUtils;

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
        //    P4 归档落地：比对源只取已准入（library_admitted=1）的论文——存量已入库
        //    正文的历史论文已由迁移脚本宽容回填为 1，新论文归档时才准入。
        LambdaQueryWrapper<PaperInfo> libraryWrapper = new LambdaQueryWrapper<PaperInfo>()
                .isNotNull(PaperInfo::getContentPath)
                .ne(PaperInfo::getContentPath, "")
                .eq(PaperInfo::getLibraryAdmitted, 1);
        if (excludePaperId != null) {
            libraryWrapper.ne(PaperInfo::getId, excludePaperId);
        }
        List<PaperInfo> paperList = paperInfoMapper.selectList(libraryWrapper);
        // M1.2：比对库为空是合法业务状态（新学校/库尚未入库），返回 0% 并在 extraInfo 注明，
        // 不视为引擎故障；但库中有论文却一篇都没比对上，则属于引擎故障，必须抛出。
        boolean libraryEmpty = paperList.isEmpty();
        int comparedCount = 0;
        int failedCount = 0;
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
                    failedCount++;
                    log.error("比对库论文正文/分词为空，无法参与比对 - paperId={}, contentPath={}, segmentedPath={}",
                            paper.getId(), paper.getContentPath(), paper.getSegmentedPath());
                    continue;
                }
                // 成功加载比对数据即视为本篇完成比对（海明预筛跳过也属正常比对结果）
                comparedCount++;

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
                failedCount++;
                log.error("比对库论文处理失败 - paperId={}, contentPath={}",
                        paper.getId(), paper.getContentPath(), e);
                continue; // 单篇失败先继续，最终按失败率决定是否让任务失败
            }
        }

        // M1.2：库中有论文但一篇都没能加载/比对成功 → 引擎故障，不允许静默返回 0%
        if (!libraryEmpty && comparedCount == 0) {
            throw new RuntimeException("比对库论文全部读取失败，无法完成查重（库内论文数：" + paperList.size() + "）");
        }

        // P1-11：单篇失败可以容忍，但失败率超过 10% 时结果可信度已不可接受，
        // 直接抛出让任务落 failure + 退额度，避免返回被严重低估的相似度。
        if (!libraryEmpty) {
            double failureRate = (double) failedCount / paperList.size();
            if (failureRate > 0.10d) {
                throw new RuntimeException(String.format(
                        "比对库论文失败率过高（失败 %d/%d，%.1f%%），无法保证查重结果可信",
                        failedCount, paperList.size(), failureRate * 100));
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
        // M1.2：空库合法返回 0%，但在额外信息里明确说明原因，避免被误读为引擎成功比对的 0%
        String libraryNote = libraryEmpty
                ? "比对库暂无已准入论文（content_path 未回填或论文尚未归档），本次相似度按 0% 记，非引擎故障。\n"
                : "";
        result.setExtraInfo(libraryNote + String.format(
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

        // 预处理库中论文：只信任带当前分词版本头的缓存；
        // 旧文件（或没有分词文件）一律按原文实时分词，保证与待查文本使用同一套词表
        // （英文归一/停用词升级后，新旧词表混算会让英文论文相似度失真）。
        // segmentedPath 为空（分词入库失败/存量缺失）时直接实时分词，不能让无效路径把整篇论文跳过
        String cachedSegmentedText = StringUtils.hasText(paper.getSegmentedPath())
                ? paperContentMinioService.readSegmentedText(paper.getSegmentedPath())
                : null;
        String segmentedLibraryText;
        if (cachedSegmentedText != null
                && cachedSegmentedText.startsWith(IKAnalyzerUtils.SEGMENT_VERSION_HEADER)) {
            segmentedLibraryText = cachedSegmentedText.substring(
                    IKAnalyzerUtils.SEGMENT_VERSION_HEADER.length());
        } else {
            segmentedLibraryText = IKAnalyzerUtils.segmentToString(libraryContent);
        }

        if (segmentedLibraryText.isEmpty()) {
            return null;
        }

        BigInteger librarySimHash = textSimilarityUtils.calculateSimHash(segmentedLibraryText);
        return new DetectionCacheService.LibraryPaperData(segmentedLibraryText, librarySimHash);
    }
}