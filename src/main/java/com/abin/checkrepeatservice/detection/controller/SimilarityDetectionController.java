package com.abin.checkrepeatservice.detection.controller;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.detection.dto.SimilarityDetectionResult;
import com.abin.checkrepeatservice.detection.service.EnhancedSimilarityDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 查重检测控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/detection")
@PreAuthorize("hasAuthority('STUDENT')")
@Tag(name = "查重检测接口", description = "论文相似度检测和报告生成相关接口")
public class SimilarityDetectionController {

    @Resource
    private EnhancedSimilarityDetectionService detectionService;
    
    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 旁路检测入口的短窗口（秒）与每日上限，防止绕过正式查重刷 CPU */
    @Value("${check.detection.user-window-seconds:30}")
    private long detectionWindowSeconds;

    @Value("${check.detection.user-daily-limit:20}")
    private long detectionDailyLimit;

    /**
     * 执行论文查重检测
     */
    @PostMapping("/similarity-check")
    @Operation(summary = "论文查重检测", description = "对本人已入库论文执行相似度检测（仅比对已准入论文）")
    public Result<SimilarityDetectionResult> detectSimilarity(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "论文ID") @RequestParam Long paperId,
            @Parameter(description = "比对论文ID列表（可选，null表示已准入比对库全库）") @RequestBody(required = false) List<Long> targetPaperIds) throws Exception {
        Long userId = jwtUtils.getUserIdFromToken(token);
        enforceDetectionRateLimit(userId);
        log.info("用户请求查重检测: userId={}, paperId={}", userId, paperId);
        
        Result<SimilarityDetectionResult> result = detectionService.detectPaperSimilarity(
                paperId, targetPaperIds, userId);
        
        if (result.isSuccess()) {
            log.info("查重检测完成: paperId={}, similarity={}%", paperId, 
                    result.getData().getOverallSimilarity());
        }
        
        return result;
    }

    /**
     * 获取查重历史记录
     */
    @GetMapping("/history/{paperId}")
    @Operation(summary = "获取查重历史", description = "获取指定论文的查重检测历史记录")
    public Result<List<SimilarityDetectionResult>> getDetectionHistory(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "论文ID") @PathVariable Long paperId) throws Exception {
        Long userId = jwtUtils.getUserIdFromToken(token);
        log.debug("用户获取查重历史: userId={}, paperId={}", userId, paperId);

        return detectionService.getDetectionHistory(paperId, userId);
    }

    /**
     * 重新检测论文
     */
    @PostMapping("/recheck/{paperId}")
    @Operation(summary = "重新查重检测", description = "重新对本人论文执行查重检测（仅比对已准入论文）")
    public Result<SimilarityDetectionResult> recheckPaper(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "论文ID") @PathVariable Long paperId) throws Exception {
        Long userId = jwtUtils.getUserIdFromToken(token);
        enforceDetectionRateLimit(userId);
        log.info("用户请求重新查重: userId={}, paperId={}", userId, paperId);
        
        return detectionService.detectPaperSimilarity(paperId, null, userId);
    }

    /**
     * 旁路检测入口的用户级限流：30 秒一次 + 每日上限。
     * Redis 异常 fail-open，避免限流组件故障导致功能不可用。
     */
    private void enforceDetectionRateLimit(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN, "无法识别当前用户", null);
        }
        try {
            String shortKey = "detection:rate:user:" + userId;
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    shortKey, "1", Math.max(1, detectionWindowSeconds), TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(acquired)) {
                throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT,
                        "检测操作过于频繁，请稍后再试", null);
            }

            String dayKey = "detection:daily:user:" + userId + ":" + LocalDate.now();
            Long count = stringRedisTemplate.opsForValue().increment(dayKey);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(dayKey, 24, TimeUnit.HOURS);
            }
            if (count != null && count > detectionDailyLimit) {
                throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT,
                        "今日查重检测次数已用完（上限 " + detectionDailyLimit + " 次）", null);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("检测入口限流不可用（Redis 异常），放行本次请求 - userId={}, 原因={}",
                    userId, e.getMessage());
        }
    }
}