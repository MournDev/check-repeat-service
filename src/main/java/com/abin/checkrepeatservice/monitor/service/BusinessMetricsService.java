package com.abin.checkrepeatservice.monitor.service;

import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.monitor.mapper.MetricEventDailyMapper;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务指标服务
 *
 * 负责两类指标的采集与读取：
 *   1. 业务事件计数（登录/注册/提交/审核）—— 落库到 metric_event_daily，同时打 Micrometer Counter
 *   2. 待处理查重任务队列深度 —— DB 计数，定时刷新到 AtomicLong 供 Gauge 读取
 */
@Slf4j
@Service
public class BusinessMetricsService {

    /** /metrics 返回的计数键前缀，形如 business.event.count[event=user_login,result=success] */
    public static final String KEY_PREFIX = "business.event.count";

    /** 队列深度键，与前端 Dashboard 读取的 gauges 键一致 */
    public static final String QUEUE_DEPTH_KEY = "check.task.queue.depth";

    /**
     * 事件类型常量。调用点只用这里的常量，不要传自由文本——
     * metric_event_daily.event 是 varchar 且无白名单约束，随意取值会抬高指标基数。
     */
    public static final class Event {
        public static final String USER_LOGIN = "user_login";
        public static final String USER_REGISTER = "user_register";
        public static final String PAPER_SUBMIT = "paper_submit";
        public static final String PAPER_REVIEW = "paper_review";

        private Event() {
        }
    }

    /**
     * 结果标签常量。刻意不叫 Result，避免与 common.Result 混淆。
     */
    public static final class Outcome {
        public static final String SUCCESS = "success";
        public static final String FAILURE = "failure";
        public static final String PASS = "pass";
        public static final String REJECT = "reject";

        private Outcome() {
        }
    }

    /**
     * 规范指标点。两个 counters map 都按这个顺序预置为 0，
     * 这样前端 Dashboard 无需依赖 `|| 0` 兜底，也保证键集合稳定。
     */
    private static final String[][] CANONICAL_POINTS = {
            {Event.USER_LOGIN, Outcome.SUCCESS},
            {Event.USER_LOGIN, Outcome.FAILURE},
            {Event.USER_REGISTER, Outcome.SUCCESS},
            {Event.PAPER_SUBMIT, Outcome.SUCCESS},
            {Event.PAPER_REVIEW, Outcome.PASS},
            {Event.PAPER_REVIEW, Outcome.REJECT},
    };

    @Resource
    private MetricEventDailyMapper metricEventDailyMapper;

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    /** 队列深度缓存，由 refreshQueueDepth() 定时刷新，Gauge 只读它不查库 */
    private final AtomicLong queueDepth = new AtomicLong(0);

    /**
     * 拼接前端约定的指标键，前端 Dashboard 按这个字符串精确取值。
     */
    public static String key(String event, String result) {
        return KEY_PREFIX + "[event=" + event + ",result=" + result + "]";
    }

    /**
     * 记录一次业务事件。
     *
     * 事务传播刻意用 REQUIRES_NEW：
     *   调用点位于 register / doReview 等 @Transactional 方法内。
     *   - 若加入外层事务，业务回滚会把埋点一起抹掉；
     *   - 若埋点插入抛错，还可能把外层事务标记为 rollback-only，反过来拖垮业务。
     *   独立事务 + 方法内吞掉所有异常，业务主流程完全不受埋点影响。
     *
     * 注意：REQUIRES_NEW 必须经 Spring 代理进入，因此本方法【只能被其他 bean 调用】，不能自调用。
     *
     * 代价：每次事件多占一个连接。仅用于登录/注册/提交/审核这类低频事件，
     *       不要把本方法用在逐请求路径上。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String event, String result) {
        // 1) Micrometer：供 /actuator/prometheus 抓取
        try {
            if (meterRegistry != null) {
                Counter.builder("business.event.count")
                        .description("Business event count")
                        .tags("event", event, "result", result)
                        .register(meterRegistry)
                        .increment();
            }
        } catch (Exception e) {
            log.debug("记录业务指标到 Micrometer 失败: event={}, result={}, {}", event, result, e.getMessage());
        }

        // 2) 落库：供 /api/admin/monitoring/metrics 查询
        try {
            metricEventDailyMapper.upsert(IdWorker.getId(), event, result);
        } catch (Exception e) {
            log.warn("记录业务指标落库失败: event={}, result={}", event, result, e);
        }
    }

    /**
     * 全量累计计数
     */
    public Map<String, Long> getAllTimeCounters() {
        return toCounters(metricEventDailyMapper.selectAllTime());
    }

    /**
     * 今日计数
     */
    public Map<String, Long> getTodayCounters() {
        return toCounters(metricEventDailyMapper.selectToday());
    }

    /**
     * 待处理查重任务队列深度（读缓存，不查库）
     */
    public long getQueueDepth() {
        return queueDepth.get();
    }

    /**
     * 定时刷新队列深度。
     *
     * 不让 Gauge 的 supplier 直接查库：那样 /metrics 和每次 Prometheus 抓取都会打 MySQL，
     * 抓取延迟与 DB 耦合，抓取频繁时还会形成冲击。30 秒对本指标完全够用。
     */
    @Scheduled(fixedRate = 30_000)
    public void refreshQueueDepth() {
        try {
            // 同时匹配小写规范值与大写 "PENDING"：
            // BatchCheckTaskServiceImpl 写的是大写形式（其查询也按大写），
            // 只匹配规范值会让该路径产生的任务在仪表盘上不可见。
            Long count = checkTaskMapper.selectCount(new LambdaQueryWrapper<CheckTask>()
                    .in(CheckTask::getCheckStatus, DictConstants.CheckStatus.PENDING, "PENDING")
                    .eq(CheckTask::getIsDeleted, 0));
            queueDepth.set(count == null ? 0L : count);
        } catch (Exception e) {
            log.warn("刷新查重任务队列深度失败: {}", e.getMessage());
        }
    }

    /**
     * 把分组查询结果转成以规范键为 key 的 map，并预置全部规范指标点为 0。
     */
    private Map<String, Long> toCounters(List<Map<String, Object>> rows) {
        Map<String, Long> counters = new LinkedHashMap<>();
        for (String[] point : CANONICAL_POINTS) {
            counters.put(key(point[0], point[1]), 0L);
        }
        if (rows == null) {
            return counters;
        }
        for (Map<String, Object> row : rows) {
            Object event = row.get("event");
            Object result = row.get("result");
            if (event == null || result == null) {
                continue;
            }
            Object cnt = row.get("cnt");
            long value = cnt instanceof Number ? ((Number) cnt).longValue() : 0L;
            // 只接受规范指标点，避免历史脏数据把非法键带进响应
            String k = key(event.toString(), result.toString());
            if (counters.containsKey(k)) {
                counters.put(k, value);
            }
        }
        return counters;
    }
}
