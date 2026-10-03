package com.abin.checkrepeatservice.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 响应时间采集配置
 *
 * monitoring:
 *   response-time:
 *     window-minutes: 60          # 需 >= 60 才能覆盖前端 SystemMonitoring 的 60 分钟选项
 *     max-samples-per-bucket: 10000
 */
@Component
@ConfigurationProperties(prefix = "monitoring.response-time")
@Data
public class ResponseTimeProperties {

    /**
     * 分钟桶保留窗口。决定内存上限的一半因素（另一半是 max-samples-per-bucket）。
     */
    private int windowMinutes = 60;

    /**
     * 每个分钟桶最多保留的原始耗时样本数（蓄水池采样上限）。
     * 这是防止流量尖峰打满堆的硬性闸门，不要设为无界或去掉。
     */
    private int maxSamplesPerBucket = 10000;
}
