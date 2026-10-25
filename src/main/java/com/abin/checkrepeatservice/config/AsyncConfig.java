package com.abin.checkrepeatservice.config;

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

    /** 查重执行池：核心线程数（与 CPU 核数对齐） */
    @Value("${check.task.executor.core-size:2}")
    private int checkCoreSize;

    /** 查重执行池：最大线程数（与 check.task.max-concurrent 同一口径） */
    @Value("${check.task.executor.max-size:2}")
    private int checkMaxSize;

    /** 查重执行池：队列容量（真正的排队在数据库，这里只留很小的缓冲） */
    @Value("${check.task.executor.queue-size:50}")
    private int checkQueueSize;

    @Bean("checkTaskExecutor")
    public ThreadPoolTaskExecutor checkTaskExecutor() {
        int core = Math.max(1, checkCoreSize);
        int max = Math.max(core, checkMaxSize);
        int queue = Math.max(10, checkQueueSize);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix("check-task-");
        executor.setKeepAliveSeconds(60);
        // 拒绝策略（P0 雪崩修复）：绝不允许 CallerRunsPolicy 把 Tika 解析 + 全库比对
        // 回落到 Tomcat 请求线程。派发器会捕获拒绝并把任务退回 pending。
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
        log.info("查重线程池初始化完成: core={}, max={}, queue={}", core, max, queue);
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

}