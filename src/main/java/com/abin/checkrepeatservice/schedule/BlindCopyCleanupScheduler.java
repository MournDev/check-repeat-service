package com.abin.checkrepeatservice.schedule;

import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 盲审副本清理任务。
 *
 * <p>背景：{@code BlindReviewCopyService} 按 {@code paperId/fileId} 缓存匿名副本，
 * 学生每次重新提交都会生成新 fileId 的新副本，旧副本与已删除论文的副本会长期滞留，
 * 需要定期回收，避免 /data 卷被历史 PDF 撑满。</p>
 *
 * <p>清理规则（逐论文目录）：</p>
 * <ol>
 *   <li>论文已删除/不存在 → 清空该目录并移除空目录；</li>
 *   <li>文件名不是当前 {@code paper.fileId}.pdf → 历史版本副本，删除；</li>
 *   <li>当前副本超过 {@code retention-days} → 删除，下次评阅访问时按需重新生成；</li>
 *   <li>只处理修改时间超过 {@code min-age-minutes} 的文件，避免删掉正在生成/刚生成的副本。</li>
 * </ol>
 *
 * <p>多实例用 Redisson 锁串行化；未装配 Redisson 时退化为单机执行（重复清理是幂等的）。</p>
 */
@Component
@Slf4j
public class BlindCopyCleanupScheduler {

    private static final String CLEANUP_LOCK = "scheduler:review-blind-cleanup";

    @Value("${review.blind.base-path:/data/review-blind/}")
    private String basePath;

    @Value("${review.blind.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    /** 当前版本副本保留天数；<=0 表示不按时间清理，只在版本变化/论文删除时清理 */
    @Value("${review.blind.cleanup.retention-days:30}")
    private int retentionDays;

    /** 最小文件年龄（分钟）：避免与其他请求的“生成中”竞争 */
    @Value("${review.blind.cleanup.min-age-minutes:10}")
    private int minAgeMinutes;

    @Autowired
    private PaperInfoMapper paperInfoMapper;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Scheduled(cron = "${review.blind.cleanup.cron:0 50 3 * * ?}")
    public void cleanup() {
        if (!cleanupEnabled) {
            log.debug("盲审副本清理已关闭（review.blind.cleanup.enabled=false），跳过");
            return;
        }

        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        if (!Files.isDirectory(base)) {
            log.debug("盲审副本目录不存在，跳过清理：{}", base);
            return;
        }

        RLock lock = redissonClient == null ? null : redissonClient.getLock(CLEANUP_LOCK);
        boolean locked = false;
        try {
            if (lock != null) {
                // 抢不到立即跳过；leaseTime 覆盖整轮磁盘扫描
                locked = lock.tryLock(0, 10, TimeUnit.MINUTES);
                if (!locked) {
                    log.info("另一实例正在执行盲审副本清理，跳过本轮");
                    return;
                }
            }
            doCleanup(base);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("盲审副本清理获取分布式锁被中断，跳过本轮");
        } catch (Exception e) {
            log.error("盲审副本清理执行失败", e);
        } finally {
            if (lock != null && locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doCleanup(Path base) {
        long minAgeMillis = Math.max(0, minAgeMinutes) * 60_000L;
        long retentionMillis = Math.max(0, retentionDays) * 86_400_000L;
        long now = System.currentTimeMillis();

        int scannedDirs = 0;
        int deletedFiles = 0;
        long freedBytes = 0;

        // 先收集目录再关闭父目录流：Windows 上持有父目录句柄时删除子目录会失败
        List<Path> paperDirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    paperDirs.add(path);
                }
            }
        } catch (IOException e) {
            log.error("遍历盲审副本目录失败：{}", base, e);
            return;
        }

        for (Path paperDir : paperDirs) {
            Long paperId = parsePaperId(paperDir.getFileName().toString());
            if (paperId == null) {
                // 非“论文ID”命名的目录不碰，避免误删运维放置的其它内容
                continue;
            }
            scannedDirs++;
            try {
                PaperInfo paper = paperInfoMapper.selectById(paperId);
                Long currentFileId = (paper == null || Integer.valueOf(1).equals(paper.getIsDeleted()))
                        ? null : paper.getFileId();

                DirectoryStream.Filter<Path> pdfFilter =
                        path -> Files.isRegularFile(path)
                                && path.getFileName().toString().toLowerCase().endsWith(".pdf");
                try (DirectoryStream<Path> files = Files.newDirectoryStream(paperDir, pdfFilter)) {
                    for (Path file : files) {
                        FileTime lastModified = Files.getLastModifiedTime(file);
                        if (now - lastModified.toMillis() < minAgeMillis) {
                            continue;
                        }
                        String fileName = file.getFileName().toString();
                        boolean current = currentFileId != null
                                && fileName.equals(currentFileId + ".pdf");
                        boolean expired = retentionMillis > 0
                                && now - lastModified.toMillis() > retentionMillis;
                        if (!current || expired) {
                            long size = Files.size(file);
                            if (Files.deleteIfExists(file)) {
                                deletedFiles++;
                                freedBytes += size;
                            }
                        }
                    }
                }

                // 目录已空则一并移除：先关闭枚举流再删目录（Windows 句柄约束）
                boolean empty;
                try (DirectoryStream<Path> remaining = Files.newDirectoryStream(paperDir)) {
                    empty = !remaining.iterator().hasNext();
                }
                if (empty) {
                    try {
                        Files.deleteIfExists(paperDir);
                    } catch (DirectoryNotEmptyException ignored) {
                        // 检查与删除之间恰好有新副本写入，保留目录即可
                    }
                }
            } catch (Exception e) {
                // 单个论文目录失败不影响其它论文，下一轮会重试
                log.warn("盲审副本清理失败 - paperId: {}, dir: {}", paperId, paperDir, e);
            }
        }

        log.info("盲审副本清理完成 - 扫描目录: {}, 删除文件: {}, 释放空间: {}KB",
                scannedDirs, deletedFiles, freedBytes / 1024);
    }

    private Long parsePaperId(String dirName) {
        try {
            long value = Long.parseLong(dirName);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
