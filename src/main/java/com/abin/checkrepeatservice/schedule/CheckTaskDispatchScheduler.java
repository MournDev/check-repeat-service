package com.abin.checkrepeatservice.schedule;

import com.abin.checkrepeatservice.common.enums.CheckTaskStatusEnum;
import com.abin.checkrepeatservice.common.statemachine.CheckTaskStateMachine;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.student.listener.CheckTaskEventListener;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 查重任务受控派发器（替代“创建即发事件、@Async 直接执行”）。
 *
 * <p>核心思路：DB 的 {@code pending} 行就是持久化队列，派发器每 5 秒按容量捞取：</p>
 * <ol>
 *   <li>统计 {@code checking} 任务数，计算剩余槽位 {@code maxConcurrent - checking}；</li>
 *   <li>无槽位直接返回，等待下一轮（任务继续安全地留在 pending）；</li>
 *   <li>有槽位则按 {@code create_time} 升序取 pending，逐条
 *       {@link CheckTaskStateMachine#claimForExecution} 抢占（多实例下天然互斥）；</li>
 *   <li>抢占成功后投递 {@code checkTaskExecutor}；若线程池拒绝，立即
 *       {@link CheckTaskStateMachine#releaseClaim} 退回 pending，绝不把任务卡在 checking。</li>
 * </ol>
 *
 * <p>与旧实现相比：提交接口只落库，不会因为瞬时流量把事件直接灌进线程池队列；
 * 重启后 pending 任务由本调度器自动续跑，无需人工干预。</p>
 */
@Component
@Slf4j
public class CheckTaskDispatchScheduler {

    private static final String DISPATCH_LOCK = "scheduler:check-task-dispatch";

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private CheckTaskStateMachine stateMachine;

    @Resource
    private CheckTaskEventListener checkTaskEventListener;

    @Resource(name = "checkTaskExecutor")
    private ThreadPoolTaskExecutor checkTaskExecutor;

    /** 多实例防重：抢不到锁的实例跳过本轮（未装配 Redisson 时退化为单机） */
    @Autowired(required = false)
    private RedissonClient redissonClient;

    /** 全局并发上限：与线程池最大线程数保持同一口径 */
    @Value("${check.task.max-concurrent:2}")
    private int maxConcurrent;

    @Scheduled(fixedDelay = 5000)
    public void dispatchPendingTasks() {
        RLock lock = redissonClient == null ? null : redissonClient.getLock(DISPATCH_LOCK);
        boolean locked = false;
        try {
            if (lock != null) {
                // 抢不到立即跳过，不阻塞调度线程；租约覆盖本轮派发
                locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
                if (!locked) {
                    return;
                }
            }

            long checking = countChecking();
            int slots = Math.max(1, maxConcurrent) - (int) checking;
            if (slots <= 0) {
                return;
            }

            List<CheckTask> pendingTasks = checkTaskMapper.selectList(
                    new LambdaQueryWrapper<CheckTask>()
                            .eq(CheckTask::getCheckStatus, CheckTaskStatusEnum.PENDING.getCode())
                            .orderByAsc(CheckTask::getCreateTime)
                            .last("LIMIT " + slots));
            if (pendingTasks.isEmpty()) {
                return;
            }

            int dispatched = 0;
            for (CheckTask task : pendingTasks) {
                // 条件抢占：已被其它实例/线程拿走的任务会返回 false
                if (!stateMachine.claimForExecution(task.getId())) {
                    continue;
                }
                try {
                    Long taskId = task.getId();
                    Long paperId = task.getPaperId();
                    checkTaskExecutor.execute(() -> runTask(taskId, paperId));
                    dispatched++;
                } catch (RejectedExecutionException e) {
                    // 线程池拒绝：立刻退回 pending，避免占着 checking 名额直到超时
                    stateMachine.releaseClaim(task.getId());
                    log.error("查重派发被线程池拒绝，任务已回退 pending - taskId={}, checking={}",
                            task.getId(), checking, e);
                    break;
                }
            }
            if (dispatched > 0) {
                log.debug("查重派发完成 - 本轮={}，派发前 checking={}", dispatched, checking);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("查重派发获取分布式锁被中断，跳过本轮");
        } catch (Exception e) {
            log.error("查重任务派发失败", e);
        } finally {
            if (lock != null && locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private long countChecking() {
        Long count = checkTaskMapper.selectCount(new LambdaQueryWrapper<CheckTask>()
                .eq(CheckTask::getCheckStatus, CheckTaskStatusEnum.CHECKING.getCode()));
        return count == null ? 0 : count;
    }

    /**
     * 线程池里执行任务：异常在这里兜底，避免未捕获异常直接打到调度线程池。
     */
    private void runTask(Long taskId, Long paperId) {
        try {
            checkTaskEventListener.executeClaimedTask(taskId, paperId);
        } catch (Exception e) {
            log.error("查重任务执行异常 - taskId={}, paperId={}", taskId, paperId, e);
        }
    }
}
