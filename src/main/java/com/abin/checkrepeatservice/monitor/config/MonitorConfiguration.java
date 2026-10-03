package com.abin.checkrepeatservice.monitor.config;

import com.abin.checkrepeatservice.monitor.service.BusinessMetricsService;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 监控配置类
 * 配置Micrometer和Actuator相关组件
 */
@Configuration
public class MonitorConfiguration {

    /**
     * 自定义MeterRegistry配置
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "check-repeat-service")
                .commonTags("version", "1.0.0");
    }

    /**
     * 启用@Timed注解支持
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    /**
     * 查重任务队列深度 Gauge
     *
     * 读数来自 BusinessMetricsService 里的缓存（由 @Scheduled 每 30 秒刷新），
     * 不在 supplier 里查库——否则 /metrics 和每次 Prometheus 抓取都会打 MySQL。
     * Prometheus 指标名：check_task_queue_depth
     */
    @Bean
    public Gauge queueDepthGauge(MeterRegistry registry, BusinessMetricsService businessMetricsService) {
        return Gauge.builder(BusinessMetricsService.QUEUE_DEPTH_KEY,
                        businessMetricsService, BusinessMetricsService::getQueueDepth)
                .description("Pending check-task queue depth")
                .register(registry);
    }
}