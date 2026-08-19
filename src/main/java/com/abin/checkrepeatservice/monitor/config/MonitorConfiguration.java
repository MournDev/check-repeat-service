package com.abin.checkrepeatservice.monitor.config;

import io.micrometer.core.aop.TimedAspect;
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
}