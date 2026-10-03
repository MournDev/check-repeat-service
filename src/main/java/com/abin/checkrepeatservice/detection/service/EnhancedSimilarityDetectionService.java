package com.abin.checkrepeatservice.detection.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.RiskLevelUtils;
import com.abin.checkrepeatservice.common.utils.TextSimilarityUtils;
import com.abin.checkrepeatservice.detection.dto.SimilarityDetectionResult;
import com.abin.checkrepeatservice.detection.dto.SimilaritySegment;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 增强版查重检测服务
 * 提供多维度相似度计算、智能段落比对、可视化报告生成等功能
 */
@Slf4j
@Service
public class EnhancedSimilarityDetectionService {

    @Resource
    private PaperInfoMapper paperInfoMapper;
    
    @Resource
    private TextSimilarityUtils textSimilarityUtils;
    
    // 用于并行处理的线程池
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    // Apache Tika实例
    private final Tika tika = new Tika();

    /**
     * 执行完整的论文查重检测
     * @param paperId 待检测论文ID
     * @param targetPaperIds 比对论文ID列表（null表示全库比对）
     * @return 查重检测结果
     */
    public Result<SimilarityDetectionResult> detectPaperSimilarity(Long paperId, List<Long> targetPaperIds) {
        try {
            log.info("开始论文查重检测: paperId={}, targetCount={}", paperId, 
                    targetPaperIds != null ? targetPaperIds.size() : "全库");
            
            // 1. 获取待检测论文内容
            PaperInfo targetPaper = paperInfoMapper.selectById(paperId);
            if (targetPaper == null) {
                return Result.error(ResultCode.PARAM_ERROR, "待检测论文不存在");
            }
            
            String targetContent = extractPaperContent(targetPaper);
            if (targetContent == null || targetContent.trim().isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "论文内容为空，无法进行查重");
            }
            
            // 2. 获取比对论文列表
            List<PaperInfo> comparisonPapers = getComparisonPapers(targetPaperIds, targetPaper.getCollegeId());
            if (comparisonPapers.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "无可用的比对论文");
            }
            
            // 3. 执行并行查重检测
            SimilarityDetectionResult result = performParallelDetection(targetContent, comparisonPapers);
            
            // 4. 生成检测报告
            generateDetectionReport(result, targetPaper, comparisonPapers);
            
            log.info("论文查重检测完成: paperId={}, overallSimilarity={}%, segmentCount={}", 
                    paperId, result.getOverallSimilarity(), result.getSimilarSegments().size());
            
            return Result.success("查重检测完成", result);
            
        } catch (Exception e) {
            log.error("论文查重检测失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查重检测失败: " + e.getMessage());
        }
    }

    /**
     * 提取论文内容
     */
    private String extractPaperContent(PaperInfo paper) {
        try {
            // 这里应该根据文件ID从存储系统获取文件内容
            // 简化实现，返回论文摘要作为示例
            return paper.getPaperAbstract() != null ? paper.getPaperAbstract() : "";
        } catch (Exception e) {
            log.error("提取论文内容失败: paperId={}", paper.getId(), e);
            return null;
        }
    }

    /**
     * 获取比对论文列表
     */
    private List<PaperInfo> getComparisonPapers(List<Long> targetPaperIds, Long excludeCollegeId) {
        List<PaperInfo> papers = new ArrayList<>();
        
        try {
            if (targetPaperIds != null && !targetPaperIds.isEmpty()) {
                // 指定比对论文
                for (Long paperId : targetPaperIds) {
                    PaperInfo paper = paperInfoMapper.selectById(paperId);
                    if (paper != null && paper.getIsDeleted() == 0) {
                        papers.add(paper);
                    }
                }
            } else {
                // 全库比对（排除同学院论文）
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaperInfo> wrapper = 
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                wrapper.eq(PaperInfo::getIsDeleted, 0)
                       .ne(PaperInfo::getCollegeId, excludeCollegeId)
                       .isNotNull(PaperInfo::getPaperAbstract);
                
                papers = paperInfoMapper.selectList(wrapper);
            }
        } catch (Exception e) {
            log.error("获取比对论文列表失败", e);
        }
        
        return papers;
    }

    /**
     * 执行并行查重检测
     */
    private SimilarityDetectionResult performParallelDetection(String targetContent, List<PaperInfo> comparisonPapers) {
        SimilarityDetectionResult result = new SimilarityDetectionResult();
        List<CompletableFuture<SimilaritySegment>> futures = new ArrayList<>();
        
        // 为每篇比对论文创建异步检测任务
        for (PaperInfo paper : comparisonPapers) {
            CompletableFuture<SimilaritySegment> future = CompletableFuture.supplyAsync(() -> {
                return detectSimilarityWithPaper(targetContent, paper);
            }, executorService);
            futures.add(future);
        }
        
        // 等待所有任务完成
        List<SimilaritySegment> segments = new ArrayList<>();
        for (CompletableFuture<SimilaritySegment> future : futures) {
            try {
                SimilaritySegment segment = future.get();
                if (segment.getSimilarity() > 0) {
                    segments.add(segment);
                }
            } catch (Exception e) {
                log.warn("单篇论文查重失败", e);
            }
        }
        
        // 按相似度降序排列
        segments.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        
        // 计算总体相似度
        double overallSimilarity = calculateOverallSimilarity(segments);
        
        result.setOverallSimilarity(overallSimilarity);
        result.setSimilarSegments(segments);
        result.setTotalComparisons(comparisonPapers.size());
        result.setDetectionTime(new Date());
        
        return result;
    }

    /**
     * 与单篇论文进行相似度检测
     */
    private SimilaritySegment detectSimilarityWithPaper(String targetContent, PaperInfo comparisonPaper) {
        SimilaritySegment segment = new SimilaritySegment();
        segment.setPaperId(comparisonPaper.getId());
        segment.setPaperTitle(comparisonPaper.getPaperTitle());
        segment.setAuthor(getPaperAuthor(comparisonPaper));
        segment.setCollege(comparisonPaper.getCollegeName());
        
        try {
            String comparisonContent = extractPaperContent(comparisonPaper);
            if (comparisonContent != null && !comparisonContent.trim().isEmpty()) {
                // 执行多维度相似度计算
                double similarity = textSimilarityUtils.calculateComprehensiveSimilarity(
                    targetContent, comparisonContent);
                segment.setSimilarity(similarity);
                
                // 提取重复片段
                List<String> repeatedFragments = extractRepeatedFragments(
                    targetContent, comparisonContent, similarity);
                segment.setRepeatedFragments(repeatedFragments);
            }
        } catch (Exception e) {
            log.warn("与论文{}比对失败: {}", comparisonPaper.getId(), e.getMessage());
            segment.setSimilarity(0.0);
        }
        
        return segment;
    }

    /**
     * 获取论文作者信息
     */
    private String getPaperAuthor(PaperInfo paper) {
        // 这里应该关联查询用户表获取真实姓名
        return "作者" + paper.getStudentId(); // 简化实现
    }

    /**
     * 提取重复片段
     */
    private List<String> extractRepeatedFragments(String content1, String content2, double similarity) {
        List<String> fragments = new ArrayList<>();
        
        if (similarity > 10) { // 相似度大于10%才提取片段
            // 简化实现：提取高频词汇作为重复片段
            String[] words1 = content1.split("\\s+");
            String[] words2 = content2.split("\\s+");
            
            Set<String> commonWords = new HashSet<>();
            for (String word : words1) {
                if (Arrays.asList(words2).contains(word) && word.length() > 2) {
                    commonWords.add(word);
                }
            }
            
            // 取前5个最常见的重复词
            fragments.addAll(commonWords.stream().limit(5).toList());
        }
        
        return fragments;
    }

    /**
     * 计算总体相似度
     */
    private double calculateOverallSimilarity(List<SimilaritySegment> segments) {
        if (segments.isEmpty()) {
            return 0.0;
        }
        
        // 加权平均算法：高相似度段落权重更高
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        
        for (SimilaritySegment segment : segments) {
            double similarity = segment.getSimilarity();
            double weight = Math.pow(similarity / 100.0, 2); // 相似度平方作为权重
            weightedSum += similarity * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    /**
     * 生成检测报告
     */
    private void generateDetectionReport(SimilarityDetectionResult result, 
                                       PaperInfo targetPaper, List<PaperInfo> comparisonPapers) {
        // 设置基础信息
        result.setTargetPaperId(targetPaper.getId());
        result.setTargetPaperTitle(targetPaper.getPaperTitle());
        result.setTargetAuthor(getPaperAuthor(targetPaper));
        result.setTargetCollege(targetPaper.getCollegeName());
        
        // 风险等级评估
        result.setRiskLevel(evaluateRiskLevel(result.getOverallSimilarity()));
        
        // 建议措施
        result.setRecommendations(generateRecommendations(result.getOverallSimilarity()));
        
        // 统计信息
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("highSimilarityCount", 
            result.getSimilarSegments().stream().filter(s -> s.getSimilarity() > 30).count());
        statistics.put("mediumSimilarityCount", 
            result.getSimilarSegments().stream().filter(s -> s.getSimilarity() > 15 && s.getSimilarity() <= 30).count());
        statistics.put("lowSimilarityCount", 
            result.getSimilarSegments().stream().filter(s -> s.getSimilarity() <= 15).count());
        result.setStatistics(statistics);
    }

    /**
     * 评估风险等级
     *
     * 分档表统一放在 RiskLevelUtils（通知里那份也用它），这里不再自己写一遍。
     */
    private String evaluateRiskLevel(double similarity) {
        return RiskLevelUtils.of(similarity);
    }

    /**
     * 生成建议措施
     */
    private List<String> generateRecommendations(double similarity) {
        List<String> recommendations = new ArrayList<>();
        
        if (similarity <= 15) {
            recommendations.add("论文原创性良好，请继续保持");
            recommendations.add("建议检查引用格式是否规范");
        } else if (similarity <= 30) {
            recommendations.add("存在一定程度的重复内容，需要适当修改");
            recommendations.add("重点检查高相似度段落的表述方式");
            recommendations.add("加强文献综述部分的原创性表达");
        } else {
            recommendations.add("重复率较高，建议大幅修改相关内容");
            recommendations.add("重新组织高相似度段落的内容结构");
            recommendations.add("增加原创性分析和观点阐述");
            recommendations.add("严格按照学术规范处理引用内容");
        }
        
        return recommendations;
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        executorService.shutdown();
    }
}