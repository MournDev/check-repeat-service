package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum;
import com.abin.checkrepeatservice.pojo.vo.CheckResult;
import com.abin.checkrepeatservice.user.service.CheckEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 深度学习查重引擎（P5 预留路径，未真实实现）
 *
 * 定位：为后续语义级相似度检测（BERT 句向量等）与 AIGC 检测预留接入路径——
 * 引擎注册、类型枚举、配置键（check.deep-learning.enabled/model-path/gpu-enabled）就位；
 * 模型加载、推理与结果归一化等产品确定技术方案后再补齐。
 *
 * 约束同 ThirdPartyCheckEngine：生产链路只传 LOCAL；未实现时返回明确失败原因；
 * 不影响本地引擎结果。
 */
@Component
@Slf4j
public class DeepLearningCheckEngine implements CheckEngine {

    @Value("${check.deep-learning.enabled:false}")
    private boolean enabled;

    @Value("${check.deep-learning.model-path:}")
    private String modelPath;

    @Override
    public CheckEngineTypeEnum getEngineType() {
        return CheckEngineTypeEnum.DEEP_LEARNING;
    }

    @Override
    public CheckResult check(String paperContent, String paperTitle) {
        CheckResult result = new CheckResult();
        result.setCheckSource("深度学习查重引擎");
        result.setSuccess(false);
        result.setFailReason(enabled
                ? "深度学习引擎已启用，但模型推理尚未实现（P5 仅预留路径），请使用本地引擎"
                : "深度学习引擎未启用（check.deep-learning.enabled=false，P5 仅预留路径）");
        return result;
    }

    @Override
    public boolean isHealthy() {
        return enabled && modelPath != null && !modelPath.isBlank();
    }

    @Override
    public String getConfigInfo() {
        return "{\"enabled\":" + enabled + ",\"modelPath\":\"" + (modelPath == null ? "" : modelPath) + "\",\"implemented\":false}";
    }
}
