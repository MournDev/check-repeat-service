package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.common.enums.CheckEngineTypeEnum;
import com.abin.checkrepeatservice.pojo.vo.CheckResult;
import com.abin.checkrepeatservice.user.service.CheckEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 第三方查重引擎（P5 预留路径，未真实实现）
 *
 * 定位：只把「外部检测 API」的接入路径留出来——引擎注册、类型枚举、配置键
 * （check.third-party.enabled/api-url/api-key/timeout）全部就位；
 * 真实 HTTP 对接（请求体组装、结果归一化为 CheckResult、与本地指标合并报表）
 * 等产品确定对接哪家服务商后再补齐。
 *
 * 约束：
 * - 生产查重链路（CheckTaskEventListener）只传 LOCAL 引擎类型，本类不会被调用；
 * - enabled=false 时 check() 返回 success=false，由 CheckEngineManager 捕获记录，
 *   不会影响本地引擎结果；
 * - 手动指定 THIRD_PARTY 引擎类型执行查重时，会得到明确的「未对接」失败原因而非空指针。
 */
@Component
@Slf4j
public class ThirdPartyCheckEngine implements CheckEngine {

    @Value("${check.third-party.enabled:false}")
    private boolean enabled;

    @Value("${check.third-party.api-url:}")
    private String apiUrl;

    @Override
    public CheckEngineTypeEnum getEngineType() {
        return CheckEngineTypeEnum.THIRD_PARTY;
    }

    @Override
    public CheckResult check(String paperContent, String paperTitle) {
        CheckResult result = new CheckResult();
        result.setCheckSource("第三方查重引擎");
        result.setSuccess(false);
        result.setFailReason(enabled
                ? "第三方查重引擎已启用，但 API 对接尚未实现（P5 仅预留路径），请使用本地引擎"
                : "第三方查重引擎未启用（check.third-party.enabled=false，P5 仅预留路径）");
        return result;
    }

    @Override
    public boolean isHealthy() {
        return enabled && apiUrl != null && !apiUrl.isBlank();
    }

    @Override
    public String getConfigInfo() {
        return "{\"enabled\":" + enabled + ",\"apiUrl\":\"" + (apiUrl == null ? "" : apiUrl) + "\",\"implemented\":false}";
    }
}
