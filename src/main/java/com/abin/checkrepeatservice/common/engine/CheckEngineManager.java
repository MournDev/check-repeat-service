package com.abin.checkrepeatservice.common.engine;

import com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum;
import com.abin.checkrepeatservice.pojo.vo.CheckResult;
import com.abin.checkrepeatservice.user.service.CheckEngine;
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
    
    @Autowired
    private List<CheckEngine> checkEngines;
    
    private final Map<CheckEngineTypeEnum, CheckEngine> engineMap = new ConcurrentHashMap<>();
    
    /**
     * 初始化引擎映射
     */
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
     * 执行查重 - 支持多引擎组合
     */
    public CheckResult executeCheck(String paperContent, String paperTitle, List<CheckEngineTypeEnum> engineTypes) {
        if (engineTypes == null || engineTypes.isEmpty()) {
            throw new IllegalArgumentException("至少需要指定一个查重引擎");
        }
        
        CheckResult compositeResult = new CheckResult();
        double maxSimilarity = 0.0;
        StringBuilder sourceInfo = new StringBuilder();
        StringBuilder extraInfo = new StringBuilder();
        
        // 依次执行各个引擎
        for (CheckEngineTypeEnum engineType : engineTypes) {
            try {
                CheckEngine engine = getEngine(engineType);
                CheckResult result = engine.check(paperContent, paperTitle);
                
                if (result.isSuccess() && result.getSimilarity() != null) {
                    // 记录最高相似度
                    if (result.getSimilarity().doubleValue() > maxSimilarity) {
                        maxSimilarity = result.getSimilarity().doubleValue();
                        compositeResult.setReportUrl(result.getReportUrl());
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
                }
                
            } catch (Exception e) {
                log.error("查重引擎执行失败: {}", engineType.getDescription(), e);
                // 单个引擎失败不影响整体流程
            }
        }
        
        // 设置综合结果
        compositeResult.setSimilarity(java.math.BigDecimal.valueOf(maxSimilarity));
        compositeResult.setCheckSource(sourceInfo.toString());
        compositeResult.setExtraInfo(extraInfo.toString());
        compositeResult.setSuccess(true);
        
        return compositeResult;
    }
    
    /**
     * 获取所有可用的引擎类型
     */
    public List<CheckEngineTypeEnum> getAvailableEngineTypes() {
        return engineMap.keySet().stream()
                .sorted((e1, e2) -> Integer.compare(e1.getOrder(), e2.getOrder()))
                .toList();
    }
}