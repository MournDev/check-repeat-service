package com.abin.checkrepeatservice.common.cache;

import com.abin.checkrepeatservice.admin.dto.PerformanceConfigDTO;
import com.abin.checkrepeatservice.admin.mapper.SystemConfigMapper;
import com.abin.checkrepeatservice.pojo.entity.SystemConfig;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 查重检测缓存服务 —— 「性能配置」页 cacheStrategy/cacheSize 的真实落地组件
 * 每次查重原本都要对全库论文逐一执行 MinIO 网络读取、IK 分词、SimHash 计算，
 * 读库失败退回 DTO 默认值（lru / 1024MB），绝不阻断启动。</p>
 */
@Service
@Slf4j
public class DetectionCacheService {

    /**
     * 库内论文比对数据（缓存值对象）
     */
    @Getter
    public static class LibraryPaperData {
        /** IK 分词后的文本（空格分隔的词序列） */
        private final String segmentedText;
        /** 分词文本的 SimHash 指纹 */
        private final java.math.BigInteger simHash;

        public LibraryPaperData(String segmentedText, java.math.BigInteger simHash) {
            this.segmentedText = segmentedText;
            this.simHash = simHash;
        }
    }

    /**
     * 缓存未命中时的加载器（由调用方提供，执行 MinIO 读取 + 分词 + SimHash）
     * 返回 null 表示该论文无有效数据（调用方语义：跳过该论文），不缓存
     */
    @FunctionalInterface
    public interface PaperDataLoader {
        LibraryPaperData load() throws Exception;
    }

    /** 缓存条目 */
    private static class CacheEntry {
        final LibraryPaperData data;
        final long weightBytes;
        final long createdAt;

        CacheEntry(LibraryPaperData data, long weightBytes) {
            this.data = data;
            this.weightBytes = weightBytes;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private static final String CONFIG_KEY = "performance";

    @Resource
    private SystemConfigMapper systemConfigMapper;

    /** ttl 策略的条目过期时间（小时），来源 check.local.cache-expire-hours（原为死配置，现接活） */
    @Value("${check.local.cache-expire-hours:24}")
    private long ttlHours;

    /** 当前策略：lru / fifo / ttl（volatile 保证配置热更新后的可见性） */
    private volatile String strategy = "lru";

    /** 容量上限（字节），默认 1024MB，与 PerformanceConfigDTO 默认值一致 */
    private volatile long maxWeightBytes = 1024L * 1024 * 1024;

    /**
     * 委托容器。synchronized 保护；accessOrder 由策略决定：
     * lru=true（get 会把条目移到队尾），fifo/ttl=false（保持写入顺序）。
     * 迭代顺序 = 淘汰顺序（最旧在前）。
     */
    private LinkedHashMap<String, CacheEntry> delegate = new LinkedHashMap<>(64, 0.75f, true);

    private long currentWeight = 0;

    // 运行统计（便于排查命中率）
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictCount = new AtomicLong();

    /**
     * 启动时从 system_config 表读取性能配置初始化策略与容量
     */
    @PostConstruct
    public void init() {
        try {
            SystemConfig config = systemConfigMapper.selectByConfigKey(CONFIG_KEY);
            if (config != null && config.getConfigValue() != null) {
                PerformanceConfigDTO dto = JSON.parseObject(config.getConfigValue(), PerformanceConfigDTO.class);
                if (dto != null) {
                    if (dto.getCacheStrategy() != null) {
                        this.strategy = dto.getCacheStrategy();
                    }
                    if (dto.getCacheSize() != null) {
                        this.maxWeightBytes = dto.getCacheSize() * 1024L * 1024;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("启动时读取性能配置失败，检测缓存使用默认参数（lru/1024MB）: {}", e.getMessage());
        }
        log.info("检测缓存已初始化: 策略={}, 容量={}MB", strategy, maxWeightBytes / 1024 / 1024);
    }

    /**
     * 读取库内论文比对数据：命中直接返回，未命中执行 loader 并缓存
     *
     * <p>加载在锁外执行——MinIO 读取 + IK 分词可能耗时数百毫秒，
     * 持有缓存锁会阻塞并发查重任务。</p>
     *
     * @param key    缓存键（论文 contentPath）
     * @param loader 未命中时的加载逻辑；抛出的异常原样向上传播（由调用方按单篇失败处理）
     * @return 论文比对数据；loader 返回 null（无效论文）时原样返回 null 且不缓存
     */
    public LibraryPaperData getOrLoad(String key, PaperDataLoader loader) throws Exception {
        // 1. 命中检查
        synchronized (this) {
            CacheEntry entry = delegate.get(key); // accessOrder=true 时 get 自动移到队尾
            if (entry != null && !isExpired(entry)) {
                hitCount.incrementAndGet();
                return entry.data;
            }
            if (entry != null) {
                // ttl 过期条目移除
                removeInternal(key);
            }
        }

        // 2. 锁外加载
        missCount.incrementAndGet();
        LibraryPaperData data = loader.load();
        if (data != null && data.getSegmentedText() != null && !data.getSegmentedText().isEmpty()) {
            putInternal(key, data);
        }
        return data;
    }

    /**
     * 热更新策略与容量（性能配置页保存时调用，运行期立即生效）。
     * 重建容器并尽量迁移旧条目：按旧容器的淘汰顺序（迭代顺序）从最旧到最新重灌，
     * 过期条目与超容量条目丢弃，未命中的会在下次查重时重新加载。
     */
    public void reconfigure(String newStrategy, Integer newSizeMb) {
        String targetStrategy = (newStrategy == null || newStrategy.isBlank()) ? strategy : newStrategy;
        long targetMaxBytes = (newSizeMb == null || newSizeMb <= 0)
                ? maxWeightBytes : newSizeMb * 1024L * 1024;

        synchronized (this) {
            LinkedHashMap<String, CacheEntry> old = this.delegate;
            this.strategy = targetStrategy;
            this.maxWeightBytes = targetMaxBytes;
            this.delegate = new LinkedHashMap<>(64, 0.75f, "lru".equals(targetStrategy));
            this.currentWeight = 0;

            // 迁移：迭代顺序即旧容器淘汰顺序（最旧在前），重灌复用统一容量淘汰逻辑
            List<Map.Entry<String, CacheEntry>> entries = new ArrayList<>(old.entrySet());
            int migrated = 0;
            for (Map.Entry<String, CacheEntry> e : entries) {
                CacheEntry entry = e.getValue();
                if (isExpired(entry) || entry.weightBytes > targetMaxBytes) {
                    continue;
                }
                evictToFit(entry.weightBytes);
                this.delegate.put(e.getKey(), entry);
                this.currentWeight += entry.weightBytes;
                migrated++;
            }
            log.info("检测缓存已热更新: 策略={}, 容量={}MB, 迁移条目={}/{}",
                    targetStrategy, targetMaxBytes / 1024 / 1024, migrated, old.size());
        }
    }

    /**
     * 当前缓存条目数（监控/排查用）
     */
    public synchronized int size() {
        return delegate.size();
    }

    /**
     * 数值型命中率（百分比 0-100，保留一位小数），供仪表盘等结构化指标使用。
     *
     * <p>无读写记录时返回 0（即「尚未产生数据」），不编造默认值。</p>
     */
    public synchronized double hitRatePercent() {
        long hit = hitCount.get();
        long miss = missCount.get();
        long total = hit + miss;
        return total == 0 ? 0.0 : Math.round(hit * 1000.0 / total) / 10.0;
    }

    /**
     * 命中率快照（监控/排查用）
     */
    public synchronized String stats() {
        long hit = hitCount.get(), miss = missCount.get();
        long total = hit + miss;
        return String.format("entries=%d, hit=%d, miss=%d, hitRate=%.1f%%, evicted=%d",
                delegate.size(), hit, miss, total == 0 ? 0.0 : hit * 100.0 / total, evictCount.get());
    }

    // ==================== 内部方法（调用方必须已持有 this 锁） ====================

    private boolean isExpired(CacheEntry entry) {
        if (!"ttl".equals(strategy)) {
            return false;
        }
        long expireMillis = getTtlHours() * 3600_000L;
        return System.currentTimeMillis() - entry.createdAt > expireMillis;
    }

    /** ttl 过期时间（小时），取 check.local.cache-expire-hours */
    private long getTtlHours() {
        return ttlHours;
    }

    private void putInternal(String key, LibraryPaperData data) {
        long weight = data.getSegmentedText().length() * 2L; // UTF-16 字节近似
        synchronized (this) {
            if (weight > maxWeightBytes) {
                // 单条超容量上限，不缓存（放行返回数据，只省不了这篇的下次开销）
                return;
            }
            evictToFit(weight);
            delegate.put(key, new CacheEntry(data, weight));
            currentWeight += weight;
        }
    }

    /** 容量不足时按淘汰顺序移除最旧条目，直到能容纳新条目 */
    private void evictToFit(long incomingWeight) {
        while (currentWeight + incomingWeight > maxWeightBytes && !delegate.isEmpty()) {
            String oldestKey = delegate.keySet().iterator().next(); // 迭代顺序头部 = 最旧
            removeInternal(oldestKey);
            evictCount.incrementAndGet();
        }
    }

    private void removeInternal(String key) {
        CacheEntry removed = delegate.remove(key);
        if (removed != null) {
            currentWeight -= removed.weightBytes;
        }
    }
}
