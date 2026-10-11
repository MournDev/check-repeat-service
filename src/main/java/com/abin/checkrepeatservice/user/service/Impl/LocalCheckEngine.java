package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.cache.DetectionCacheService;
import com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum;
import com.abin.checkrepeatservice.common.service.PaperContentMinioService;
import com.abin.checkrepeatservice.common.utils.IKAnalyzerUtils;
import com.abin.checkrepeatservice.common.utils.TextSimilarityUtils;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.vo.CheckResult;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.user.service.CheckEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

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
        CheckResult result = new CheckResult();

        // 1. 预处理待查重论文（IK分词+SimHash计算）
        String segmentedTargetText = IKAnalyzerUtils.segmentToString(paperContent);
        if (segmentedTargetText.isEmpty()) {
            throw new RuntimeException("论文内容为空或分词后无有效内容");
        }
        BigInteger targetSimHash = textSimilarityUtils.calculateSimHash(segmentedTargetText);

        // 2. 查询论文信息（使用PaperInfo表）
        List<PaperInfo> paperList = paperInfoMapper.selectList(null);
        double maxSimilarity = 0.0;
        String mostSimilarPaperTitle = "";
        BigInteger mostSimilarSimHash = BigInteger.ZERO;

        // 3. 遍历论文信息，逐一比对
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

                // 3.2 余弦相似度精细计算
                double similarity = textSimilarityUtils.calculateCosineSimilarity(segmentedTargetText, libraryData.getSegmentedText());

                // 3.3 记录最高相似度
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

        // 4. 封装查重结果
        BigDecimal similarityPercent = BigDecimal.valueOf(maxSimilarity * 100)
                .setScale(2, BigDecimal.ROUND_HALF_UP);
        result.setSimilarity(similarityPercent);
        result.setCheckSource("论文信息库（SimHash+余弦相似度）");
        result.setReportUrl("/local/report/" + System.currentTimeMillis());
        result.setExtraInfo(String.format(
                "最高相似度论文：%s\nSimHash海明距离：%d\n相似度：%.2f%%\n比对论文总数：%d",
                mostSimilarPaperTitle,
                textSimilarityUtils.calculateHammingDistance(targetSimHash, mostSimilarSimHash),
                similarityPercent,
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