package com.abin.checkrepeatservice.common.engine;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.vo.CheckResult;
import com.abin.checkrepeatservice.user.service.CheckEngine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 查重引擎管理器
 * 负责统一管理和调度多种查重引擎
 */
@Component
@Slf4j
public class CheckEngineManager {

    /**
     * M1.1 检测范围明示：当前部署实际参与比对的库范围。
     * 报告与结果必须显式带出，避免 0% 被误读为“全网无重复”。
     */
    public static final String CHECK_SCOPE = "校内历史论文库（未接入外部查重数据库）";

    @Autowired
    private List<CheckEngine> checkEngines;

    private final Map<CheckEngineTypeEnum, CheckEngine> engineMap = new ConcurrentHashMap<>();

    /**
     * 初始化引擎映射
     *
     * 踩坑修复（P5）：本方法此前无 @PostConstruct 且全库无调用，engineMap 恒为空，
     * executeCheck 内 getEngine 抛出的异常被 try-catch 吞掉后静默返回相似度 0 的
     * "成功"结果——所有查重任务实际从未执行过引擎比对。补上 @PostConstruct 后
     * 引擎注册才真正生效（P5 句级标注等指标依赖本链路）。
     */
    @PostConstruct
    public void init() {
        for (CheckEngine engine : checkEngines) {
            CheckEngineTypeEnum engineType = engine.getEngineType();
            if (engineType != null) {
                engineMap.put(engineType, engine);
                log.info("注册查重引擎: {} -> {}", engineType.getDescription(), engine.getClass().getSimpleName());
            }
        }
    }
    
    /**
     * 根据引擎类型获取查重引擎
     */
    public CheckEngine getEngine(CheckEngineTypeEnum engineType) {
        CheckEngine engine = engineMap.get(engineType);
        if (engine == null) {
            throw new IllegalArgumentException("未找到查重引擎: " + engineType.getDescription());
        }
        return engine;
    }
    
    /**
     * M1.1 检测边界校验：请求的引擎必须已注册且已真实接入。
     * 未接入时直接抛出明确业务异常，禁止建任务后再静默降级成 0%。
     */
    public void ensureEnginesAvailable(List<CheckEngineTypeEnum> engineTypes) {
        if (engineTypes == null || engineTypes.isEmpty()) {
            throw new IllegalArgumentException("至少需要指定一个查重引擎");
        }
        List<String> unavailable = engineTypes.stream()
                .filter(type -> {
                    CheckEngine engine = engineMap.get(type);
                    return engine == null || !engine.isImplemented();
                })
                .map(CheckEngineTypeEnum::getDescription)
                .distinct()
                .toList();
        if (!unavailable.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "当前院校未接入外部查重源，仅支持本地引擎（" + CHECK_SCOPE + "）；不可用引擎："
                            + String.join("、", unavailable), null);
        }
    }

    /**
     * 执行查重 - 支持多引擎组合
     */
    public CheckResult executeCheck(String paperContent, String paperTitle, List<CheckEngineTypeEnum> engineTypes) {
        return executeCheck(paperContent, paperTitle, engineTypes, null);
    }

    /**
     * 执行查重 - 支持多引擎组合，并把待查论文ID透传给引擎用于排除自我比对（架构修复 P0）
     */
    public CheckResult executeCheck(String paperContent, String paperTitle,
                                   List<CheckEngineTypeEnum> engineTypes, Long excludePaperId) {
        if (engineTypes == null || engineTypes.isEmpty()) {
            throw new IllegalArgumentException("至少需要指定一个查重引擎");
        }
        // M1.1：执行前再校验一次，防止绕过建任务入口直接调用
        ensureEnginesAvailable(engineTypes);

        CheckResult compositeResult = new CheckResult();
        double maxSimilarity = 0.0;
        CheckResult bestResult = null;
        StringBuilder sourceInfo = new StringBuilder();
        StringBuilder extraInfo = new StringBuilder();
        // M1.2：记录成功引擎数与各引擎失败原因，全部失败时向调用方抛异常
        int successCount = 0;
        StringBuilder failureInfo = new StringBuilder();

        // 依次执行各个引擎
        for (CheckEngineTypeEnum engineType : engineTypes) {
            try {
                CheckEngine engine = getEngine(engineType);
                CheckResult result = engine.check(paperContent, paperTitle, excludePaperId);

                if (result.isSuccess() && result.getSimilarity() != null) {
                    successCount++;
                    // 记录最高相似度
                    if (result.getSimilarity().doubleValue() >= maxSimilarity) {
                        maxSimilarity = result.getSimilarity().doubleValue();
                        compositeResult.setReportUrl(result.getReportUrl());
                        // 记录最优引擎结果：P5 句级标注/三项指标需从最优结果透传给监听器
                        bestResult = result;
                    }

                    // 拼接来源信息
                    if (sourceInfo.length() > 0) {
                        sourceInfo.append(" | ");
                    }
                    sourceInfo.append(result.getCheckSource());

                    // 拼接额外信息
                    if (result.getExtraInfo() != null) {
                        extraInfo.append("[").append(engineType.getDescription()).append("] ")
                                .append(result.getExtraInfo()).append("\n");
                    }
                } else {
                    failureInfo.append(engineType.getDescription()).append("：")
                            .append(result.getFailReason() != null ? result.getFailReason() : "未返回有效相似度")
                            .append("；");
                }

            } catch (Exception e) {
                log.error("查重引擎执行失败: {}", engineType.getDescription(), e);
                // 单个引擎失败不影响其他引擎，但失败原因必须记下来供全失败时上报
                failureInfo.append(engineType.getDescription()).append("：")
                        .append(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                        .append("；");
            }
        }

        // M1.2：所有引擎都失败/无有效结果时，禁止再返回 success=true 的 0%，
        // 必须抛异常由执行链路落 failure + fail_reason
        if (successCount == 0) {
            String reason = "查重引擎全部执行失败（" + failureInfo + "）";
            throw new BusinessException(ResultCode.SYSTEM_ERROR,
                    reason.length() > 500 ? reason.substring(0, 500) : reason, null);
        }

        // 设置综合结果
        compositeResult.setSimilarity(java.math.BigDecimal.valueOf(maxSimilarity));
        compositeResult.setCheckSource(sourceInfo.toString());
        compositeResult.setExtraInfo(extraInfo.toString());
        compositeResult.setSuccess(true);

        // P5：透传最优引擎结果的句级指标（多引擎时取相似度最高者，与综合相似度口径一致）
        if (bestResult != null) {
            compositeResult.setTotalSimilarityRate(bestResult.getTotalSimilarityRate());
            compositeResult.setMaxPerSource(bestResult.getMaxPerSource());
            compositeResult.setCitationRate(bestResult.getCitationRate());
            compositeResult.setSentenceHits(bestResult.getSentenceHits());
        }

        return compositeResult;
    }
    
    /**
     * 获取所有可用的引擎类型
     */
    public List<CheckEngineTypeEnum> getAvailableEngineTypes() {
        return engineMap.entrySet().stream()
                // M1.1：只暴露已真实接入的引擎，预留引擎不进入可选列表
                .filter(entry -> entry.getValue().isImplemented())
                .map(Map.Entry::getKey)
                .sorted((e1, e2) -> Integer.compare(e1.getOrder(), e2.getOrder()))
                .toList();
    }
}