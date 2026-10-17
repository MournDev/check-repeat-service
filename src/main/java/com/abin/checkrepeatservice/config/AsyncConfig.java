package com.abin.checkrepeatservice.config;

import com.abin.checkrepeatservice.admin.dto.PerformanceConfigDTO;
import com.abin.checkrepeatservice.admin.mapper.SystemConfigMapper;
import com.abin.checkrepeatservice.pojo.entity.SystemConfig;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 配置查重任务的线程池
 * AsyncConfig → Service → Executor → AsyncConfig 的循环依赖。</p>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("checkTaskExecutor")
    public ThreadPoolTaskExecutor checkTaskExecutor(SystemConfigMapper systemConfigMapper) {
        PerformanceConfigDTO perf = loadPerformanceConfigWithFallback(systemConfigMapper);
        // 防御性夹取，与 PerformanceConfigDTO 校验范围一致
        int maxConcurrent = Math.max(1, Math.min(100,
                perf.getMaxConcurrent() == null ? 20 : perf.getMaxConcurrent()));
        int queueSize = Math.max(10, Math.min(1000,
                perf.getQueueSize() == null ? 100 : perf.getQueueSize()));

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：保持原有 5，但不得大于最大线程数（配置页可能把 max 调到 <5）
        executor.setCorePoolSize(Math.min(5, maxConcurrent));
        // 最大线程数 = 最大并发数
        executor.setMaxPoolSize(maxConcurrent);
        // 队列容量
        executor.setQueueCapacity(queueSize);
        // 线程名称前缀
        executor.setThreadNamePrefix("check-task-");
        // 线程空闲时间（秒）
        executor.setKeepAliveSeconds(60);
        // 拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 等待时间
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        log.info("查重线程池已按性能配置初始化: core={}, max={}, queue={}",
                executor.getCorePoolSize(), maxConcurrent, queueSize);
        return executor;
    }
    
    /**
     * 通用异步任务线程池
     */
    @Bean("asyncExecutor")
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-");
        executor.setKeepAliveSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }

    /**
     * 启动期从 system_config 表读取性能配置。
     * 任何异常（表不存在、JSON 损坏、数据库未就绪）都退回 DTO 默认值，绝不阻断启动。
     */
    private PerformanceConfigDTO loadPerformanceConfigWithFallback(SystemConfigMapper systemConfigMapper) {
        try {
            SystemConfig config = systemConfigMapper.selectByConfigKey("performance");
            if (config != null && config.getConfigValue() != null) {
                PerformanceConfigDTO dto = JSON.parseObject(config.getConfigValue(), PerformanceConfigDTO.class);
                if (dto != null && dto.getMaxConcurrent() != null && dto.getQueueSize() != null) {
                    return dto;
                }
            }
        } catch (Exception e) {
            log.warn("启动时读取性能配置失败，使用默认线程池参数: {}", e.getMessage());
        }
        return new PerformanceConfigDTO();
    }
}