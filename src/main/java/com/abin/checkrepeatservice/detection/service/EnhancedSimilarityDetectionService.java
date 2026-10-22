package com.abin.checkrepeatservice.detection.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.PaperContentMinioService;
import com.abin.checkrepeatservice.common.utils.IKAnalyzerUtils;
import com.abin.checkrepeatservice.common.utils.RiskLevelUtils;
import com.abin.checkrepeatservice.common.utils.TextSimilarityUtils;
import com.abin.checkrepeatservice.detection.dto.SimilarityDetectionResult;
import com.abin.checkrepeatservice.detection.dto.SimilaritySegment;
import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

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
    private PaperContentMinioService paperContentMinioService;

    @Resource
    private SysUserMapper sysUserMapper;
    
    @Resource
    private TextSimilarityUtils textSimilarityUtils;

    // 复用全局异步线程池（不再自建固定线程池）
    @Resource(name = "asyncExecutor")
    private ThreadPoolTaskExecutor asyncExecutor;

    /**
     * 执行完整的论文查重检测。
     *
     * @param paperId        待检测论文ID
     * @param targetPaperIds 比对论文ID列表（null 表示已准入比对库全库）
     * @param requesterId    当前登录学生ID（归属校验，禁止检测他人论文）
     */
    public Result<SimilarityDetectionResult> detectPaperSimilarity(Long paperId, List<Long> targetPaperIds,
                                                                   Long requesterId) {
        return detectPaperSimilarityInternal(paperId, targetPaperIds, requesterId, true);
    }

    /**
     * 管理端校内查重入口：调用方已在管理端完成数据范围校验，此处不再按学生归属拦截。
     */
    public Result<SimilarityDetectionResult> detectPaperSimilarityForAdmin(Long paperId,
                                                                           List<Long> targetPaperIds) {
        return detectPaperSimilarityInternal(paperId, targetPaperIds, null, false);
    }

    private Result<SimilarityDetectionResult> detectPaperSimilarityInternal(Long paperId,
                                                                            List<Long> targetPaperIds,
                                                                            Long requesterId,
                                                                            boolean enforceOwnership) {
        try {
            log.info("开始论文查重检测: paperId={}, requesterId={}, targetCount={}", paperId,
                    requesterId, targetPaperIds != null ? targetPaperIds.size() : "全库");

            // 1. 归属校验：学生入口仅允许检测本人论文（原先可传任意 paperId，存在越权）
            PaperInfo targetPaper = paperInfoMapper.selectById(paperId);
            if (targetPaper == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "待检测论文不存在");
            }
            if (enforceOwnership && (requesterId == null
                    || !Objects.equals(targetPaper.getStudentId(), requesterId))) {
                log.warn("越权查重请求被拒绝: paperId={}, requesterId={}", paperId, requesterId);
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权检测他人论文");
            }

            // 2. 正文必须已入库：不再用摘要冒充正文（摘要比对结果没有参考价值）
            String targetContent = extractPaperContent(targetPaper);
            if (!StringUtils.hasText(targetContent)) {
                return Result.error(ResultCode.PARAM_ERROR,
                        "论文正文尚未入库，请先提交并完成一次正式查重后再使用此接口");
            }

            // 3. 比对源只取已准入、正文已入库的论文，并排除自身
            List<PaperInfo> comparisonPapers = getComparisonPapers(targetPaperIds, targetPaper.getId());
            if (comparisonPapers.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "无可用比对论文（比对库暂无已准入论文）");
            }

            // 4. 批量回填作者姓名，避免逐篇查库
            Map<Long, String> authorNames = loadAuthorNames(targetPaper, comparisonPapers);

            // 5. 执行并行查重检测
            SimilarityDetectionResult result = performParallelDetection(targetContent, comparisonPapers, authorNames);

            // 6. 生成检测报告
            generateDetectionReport(result, targetPaper, authorNames);

            log.info("论文查重检测完成: paperId={}, overallSimilarity={}%, segmentCount={}",
                    paperId, result.getOverallSimilarity(), result.getSimilarSegments().size());

            return Result.success("查重检测完成", result);

        } catch (IllegalStateException e) {
            log.error("论文查重检测失败: paperId={}, reason={}", paperId, e.getMessage());
            return Result.error(ResultCode.SYSTEM_ERROR, "查重检测失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("论文查重检测失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查重检测失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 读取论文真实正文（MinIO content_path）；未入库/读取失败返回 null。
     * 不再回落到摘要——摘要不是查重对象。
     */
    private String extractPaperContent(PaperInfo paper) {
        if (paper == null || !StringUtils.hasText(paper.getContentPath())) {
            return null;
        }
        try {
            return paperContentMinioService.readPaperContent(paper.getContentPath());
        } catch (Exception e) {
            log.warn("读取论文正文失败: paperId={}, error={}", paper.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 获取比对论文列表：只保留已准入（library_admitted=1）且正文已入库的论文。
     *
     * @param targetPaperId 待查论文ID（排除自身）
     */
    private List<PaperInfo> getComparisonPapers(List<Long> targetPaperIds, Long targetPaperId) {
        List<PaperInfo> papers = new ArrayList<>();

        try {
            if (targetPaperIds != null && !targetPaperIds.isEmpty()) {
                // 指定比对论文：同样必须已准入，防止借此读取未归档论文
                for (Long paperId : targetPaperIds) {
                    if (paperId == null || Objects.equals(paperId, targetPaperId)) {
                        continue;
                    }
                    PaperInfo paper = paperInfoMapper.selectById(paperId);
                    if (isComparable(paper)) {
                        papers.add(paper);
                    }
                }
            } else {
                LambdaQueryWrapper<PaperInfo> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(PaperInfo::getIsDeleted, 0)
                       .eq(PaperInfo::getLibraryAdmitted, 1)
                       .isNotNull(PaperInfo::getContentPath)
                       .ne(PaperInfo::getContentPath, "")
                       .ne(PaperInfo::getId, targetPaperId);
                papers = paperInfoMapper.selectList(wrapper);
            }
        } catch (Exception e) {
            log.error("获取比对论文列表失败", e);
        }

        return papers;
    }

    private boolean isComparable(PaperInfo paper) {
        return paper != null && Integer.valueOf(1).equals(paper.getLibraryAdmitted())
                && StringUtils.hasText(paper.getContentPath());
    }

    /**
     * 批量加载作者姓名（realName 优先，空回落 username）
     */
    private Map<Long, String> loadAuthorNames(PaperInfo targetPaper, List<PaperInfo> comparisonPapers) {
        Set<Long> studentIds = new HashSet<>();
        if (targetPaper.getStudentId() != null) {
            studentIds.add(targetPaper.getStudentId());
        }
        comparisonPapers.stream()
                .map(PaperInfo::getStudentId)
                .filter(Objects::nonNull)
                .forEach(studentIds::add);
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(SysUser::getId,
                        u -> StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername()));
    }

    /**
     * 执行并行查重检测
     */
    private SimilarityDetectionResult performParallelDetection(String targetContent,
                                                               List<PaperInfo> comparisonPapers,
                                                               Map<Long, String> authorNames) {
        SimilarityDetectionResult result = new SimilarityDetectionResult();
        List<SimilaritySegment> segments = new ArrayList<>();
        int unreadableCount = 0;

        // 分批提交：全局异步池队列有界（50），一次性提交全库论文会触发拒绝策略
        final int batchSize = 20;
        for (int start = 0; start < comparisonPapers.size(); start += batchSize) {
            int end = Math.min(start + batchSize, comparisonPapers.size());
            List<CompletableFuture<SimilaritySegment>> futures = new ArrayList<>(end - start);
            for (int i = start; i < end; i++) {
                PaperInfo paper = comparisonPapers.get(i);
                try {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> detectSimilarityWithPaper(targetContent, paper, authorNames),
                            asyncExecutor));
                } catch (RejectedExecutionException e) {
                    unreadableCount++;
                    log.warn("查重任务被线程池拒绝: paperId={}", paper.getId());
                }
            }
            for (CompletableFuture<SimilaritySegment> future : futures) {
                try {
                    SimilaritySegment segment = future.get();
                    if (segment == null) {
                        unreadableCount++;
                    } else if (segment.getSimilarity() > 0) {
                        segments.add(segment);
                    }
                } catch (Exception e) {
                    unreadableCount++;
                    log.warn("单篇论文查重失败", e);
                }
            }
        }

        // 有比对论文却全部读不到正文 → 引擎故障，不能静默返回 0%
        if (segments.isEmpty() && unreadableCount == comparisonPapers.size()) {
            throw new IllegalStateException("比对库论文正文全部读取失败，无法完成查重");
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
    private SimilaritySegment detectSimilarityWithPaper(String targetContent, PaperInfo comparisonPaper,
                                                        Map<Long, String> authorNames) {
        SimilaritySegment segment = new SimilaritySegment();
        segment.setPaperId(comparisonPaper.getId());
        segment.setPaperTitle(comparisonPaper.getPaperTitle());
        segment.setAuthor(getPaperAuthor(comparisonPaper, authorNames));
        segment.setCollege(comparisonPaper.getCollegeName());
        
        try {
            String comparisonContent = extractPaperContent(comparisonPaper);
            if (!StringUtils.hasText(comparisonContent)) {
                return null;
            }
            // 执行多维度相似度计算
            double similarity = textSimilarityUtils.calculateComprehensiveSimilarity(
                targetContent, comparisonContent);
            segment.setSimilarity(similarity);

            // 提取重复片段
            segment.setRepeatedFragments(extractRepeatedFragments(
                targetContent, comparisonContent, similarity));
        } catch (Exception e) {
            log.warn("与论文{}比对失败: {}", comparisonPaper.getId(), e.getMessage());
            return null;
        }
        
        return segment;
    }

    /**
     * 获取论文作者信息（批量预载的真实姓名）
     */
    private String getPaperAuthor(PaperInfo paper, Map<Long, String> authorNames) {
        if (paper.getStudentId() == null) {
            return "未知";
        }
        return authorNames.getOrDefault(paper.getStudentId(), "未知");
    }

    /**
     * 提取重复片段
     */
    private List<String> extractRepeatedFragments(String content1, String content2, double similarity) {
        List<String> fragments = new ArrayList<>();

        if (similarity > 10) { // 相似度大于10%才提取
            // 用统一分词器提取共同高频词元（原先按空格切分，对中文等于整段比较）
            List<String> tokens1 = IKAnalyzerUtils.segment(content1);
            Set<String> tokens2 = new HashSet<>(IKAnalyzerUtils.segment(content2));

            Map<String, Integer> commonFreq = new HashMap<>();
            for (String token : tokens1) {
                if (tokens2.contains(token)) {
                    commonFreq.merge(token, 1, Integer::sum);
                }
            }
            // 按出现次数降序取前 5 个真实共同词元
            commonFreq.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .forEach(fragments::add);
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
                                       PaperInfo targetPaper, Map<Long, String> authorNames) {
        // 设置基础信息
        result.setTargetPaperId(targetPaper.getId());
        result.setTargetPaperTitle(targetPaper.getPaperTitle());
        result.setTargetAuthor(getPaperAuthor(targetPaper, authorNames));
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

}