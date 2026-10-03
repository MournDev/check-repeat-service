package com.abin.checkrepeatservice.monitor.service;

import com.abin.checkrepeatservice.monitor.config.ResponseTimeProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 逐请求响应时间采集器（分钟桶环形缓冲）
 *
 * 数据只存在内存里，进程重启即清零。这是刻意的：
 * 逐请求原始耗时数据量大、价值随时间快速衰减，落库的收益远低于成本。
 *
 * 内存上限 = windowMinutes × maxSamplesPerBucket × 8B
 *          = 60 × 10000 × 8B ≈ 4.8MB（另加桶对象开销），可控。
 * 调大 window-minutes 会让内存线性增长；max-samples-per-bucket 是另一个闸门。
 */
@Slf4j
@Component
public class ResponseTimeStore {

    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final long MILLIS_PER_MINUTE = 60_000L;

    /** 距上次淘汰不足这么多分钟就跳过，避免每次写入都遍历跳跃表 */
    private static final long EVICT_INTERVAL_MINUTES = 5L;

    @Resource
    private ResponseTimeProperties properties;

    /**
     * key = epoch 分钟数，value = 该分钟的统计桶。
     * 用跳跃表是为了支持有序范围查询（subMap）和廉价的头部淘汰。
     */
    private final ConcurrentSkipListMap<Long, MinuteBucket> buckets = new ConcurrentSkipListMap<>();

    private volatile long lastEvictedMinute = -1L;

    /**
     * 记录一次请求耗时
     *
     * @param durationMs 耗时（毫秒）
     * @param status     HTTP 状态码（当前仅用于未来的错误率统计，桶里不做区分）
     */
    public void record(long durationMs, int status) {
        long minute = currentMinute();
        MinuteBucket bucket = buckets.computeIfAbsent(minute, k -> new MinuteBucket(properties.getMaxSamplesPerBucket()));
        bucket.add(durationMs);
        maybeEvict(minute);
    }

    /**
     * 生成最近 minutes 分钟的趋势快照。
     *
     * 空桶补零而不是跳过——前端 ECharts 用类目轴，缺桶会让时间轴不连续。
     *
     * @param minutes 窗口分钟数，调用方负责保证落在 [1, windowMinutes] 内
     */
    public Map<String, Object> snapshot(int minutes) {
        long nowMinute = currentMinute();
        // 读取前先淘汰，保证返回的数据不会越出窗口
        evictExpired(nowMinute);

        int points = minutes;
        long firstMinute = nowMinute - (points - 1L);

        String[] timestamps = new String[points];
        long[] avg = new long[points];
        long[] min = new long[points];
        long[] max = new long[points];
        long[] p95 = new long[points];
        long[] p99 = new long[points];
        long[] count = new long[points];

        for (int i = 0; i < points; i++) {
            long minute = firstMinute + i;
            timestamps[i] = Instant.ofEpochMilli(minute * MILLIS_PER_MINUTE)
                    .atZone(ZoneId.systemDefault())
                    .format(LABEL_FORMAT);

            MinuteBucket bucket = buckets.get(minute);
            if (bucket == null) {
                continue; // 空桶保持全 0
            }
            MinuteBucket.Stats stats = bucket.snapshot();
            count[i] = stats.count;
            avg[i] = stats.avg;
            min[i] = stats.min;
            max[i] = stats.max;
            p95[i] = stats.p95;
            p99[i] = stats.p99;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamps", timestamps);
        data.put("avgResponseTime", avg);
        data.put("minResponseTime", min);
        data.put("maxResponseTime", max);
        data.put("p95ResponseTime", p95);
        data.put("p99ResponseTime", p99);
        data.put("requestCount", count);
        return data;
    }

    /**
     * 当前保留窗口（分钟），供 controller 做入参裁剪
     */
    public int getWindowMinutes() {
        return properties.getWindowMinutes();
    }

    private long currentMinute() {
        return System.currentTimeMillis() / MILLIS_PER_MINUTE;
    }

    private void maybeEvict(long currentMinute) {
        if (currentMinute - lastEvictedMinute < EVICT_INTERVAL_MINUTES) {
            return;
        }
        evictExpired(currentMinute);
    }

    private void evictExpired(long currentMinute) {
        lastEvictedMinute = currentMinute;
        long cutoff = currentMinute - properties.getWindowMinutes();
        try {
            SortedMap<Long, MinuteBucket> expired = buckets.headMap(cutoff);
            if (!expired.isEmpty()) {
                expired.clear();
            }
        } catch (Exception e) {
            log.debug("淘汰过期响应时间桶失败: {}", e.getMessage());
        }
    }

    /**
     * 单个分钟桶
     *
     * sum/min/max/count 是精确值；百分位基于有界样本近似。
     * 样本用蓄水池采样，保证在任意流量下内存有硬上限，同时样本无偏。
     */
    private static final class MinuteBucket {

        private final long[] samples;
        private final Random random = new Random();

        private long sum;
        private long min = Long.MAX_VALUE;
        private long max;
        private int seen; // 累计观测次数；实际保留样本数 = min(seen, samples.length)

        MinuteBucket(int capacity) {
            this.samples = new long[Math.max(1, capacity)];
        }

        synchronized void add(long durationMs) {
            long value = Math.max(0L, durationMs);
            int capacity = samples.length;

            if (seen < capacity) {
                samples[seen] = value;
            } else {
                // 蓄水池采样：以 capacity/(seen+1) 的概率替换已有样本
                int j = random.nextInt(seen + 1);
                if (j < capacity) {
                    samples[j] = value;
                }
            }
            seen++;

            sum += value;
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }

        synchronized Stats snapshot() {
            if (seen == 0) {
                return Stats.EMPTY;
            }
            int stored = Math.min(seen, samples.length);
            long[] sorted = new long[stored];
            System.arraycopy(samples, 0, sorted, 0, stored);
            java.util.Arrays.sort(sorted);

            Stats stats = new Stats();
            stats.count = seen;
            stats.avg = sum / seen;
            stats.min = min;
            stats.max = max;
            stats.p95 = percentile(sorted, 95);
            stats.p99 = percentile(sorted, 99);
            return stats;
        }

        /**
         * 最近秩法取百分位：index = ceil(p/100 * n) - 1，再夹到 [0, n-1]
         */
        private static long percentile(long[] sorted, int p) {
            int n = sorted.length;
            if (n == 0) {
                return 0L;
            }
            int index = (int) Math.ceil(p / 100.0 * n) - 1;
            if (index < 0) {
                index = 0;
            }
            if (index >= n) {
                index = n - 1;
            }
            return sorted[index];
        }

        private static final class Stats {
            private static final Stats EMPTY = new Stats();

            private long count;
            private long avg;
            private long min;
            private long max;
            private long p95;
            private long p99;
        }
    }
}
