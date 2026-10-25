package com.abin.checkrepeatservice.config;

import com.abin.checkrepeatservice.admin.dto.PerformanceConfigDTO;
import com.abin.checkrepeatservice.admin.mapper.SystemConfigMapper;
import com.abin.checkrepeatservice.pojo.entity.SystemConfig;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionException;

/**
 * 异步任务配置
 * 配置查重任务的线程池
 * AsyncConfig → Service → Executor → AsyncConfig 的循环依赖。</p>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 论文提交后置任务池：核心线程数 */
    @Value("${async.paper-submit.core-size:2}")
    private int paperSubmitCoreSize;

    /** 论文提交后置任务池：最大线程数 */
    @Value("${async.paper-submit.max-size:4}")
    private int paperSubmitMaxSize;

    /** 论文提交后置任务池：队列容量 */
    @Value("${async.paper-submit.queue-size:100}")
    private int paperSubmitQueueSize;

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
        // 拒绝策略（P0 雪崩修复）：绝不允许 CallerRunsPolicy 把 Tika 解析 + 全库比对
        // 回落到 Tomcat 请求线程。队列满即拒绝，由调用方捕获并给出“排队中”提示。
        executor.setRejectedExecutionHandler((task, pool) -> {
            log.error("查重任务队列已满，拒绝执行 - activeCount={}, queueSize={}, poolSize={}",
                    pool.getActiveCount(), pool.getQueue().size(), pool.getPoolSize());
            throw new RejectedExecutionException("查重队列已满");
        });
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
        // 通知/日志等非关键异步任务：队列满时丢弃并告警，绝不回落到请求线程，
        // 也不让“发不出通知”反向拖垮主业务。
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("通用异步队列已满，丢弃非关键任务 - activeCount={}, queueSize={}",
                        pool.getActiveCount(), pool.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }

    /**
     * 论文提交后置任务线程池（独立化）。
     *
     * <p>只承载「自动分配导师 + 触发查重」这类轻量 DB/通知工作，与查重执行池隔离：
     * 查重队列被打满时，论文提交链路不会跟着排队，也不会把 AFTER_COMMIT 阶段的
     * 提交请求拖成异常。任务被拒绝由监听器显式捕获并回落到提交线程（该路径不含
     * Tika 解析与全库比对，回落是可控的）。</p>
     */
    @Bean("paperSubmitExecutor")
    public ThreadPoolTaskExecutor paperSubmitExecutor() {
        int core = Math.max(1, paperSubmitCoreSize);
        int max = Math.max(core, paperSubmitMaxSize);
        int queue = Math.max(10, paperSubmitQueueSize);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix("paper-submit-");
        executor.setKeepAliveSeconds(60);
        // AbortPolicy：拒绝由 PaperSubmissionAllocationListener 捕获后决定回落，
        // 不在此处静默丢弃（论文已落库，导师分配不能无声丢失）
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        log.info("论文提交后置线程池初始化完成: core={}, max={}, queue={}", core, max, queue);
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