package com.abin.checkrepeatservice.detection.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.detection.dto.SimilarityDetectionResult;
import com.abin.checkrepeatservice.detection.service.EnhancedSimilarityDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

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

    /**
     * 执行论文查重检测
     */
    @PostMapping("/similarity-check")
    @Operation(summary = "论文查重检测", description = "对本人已入库论文执行相似度检测（仅比对已准入论文）")
    public Result<SimilarityDetectionResult> detectSimilarity(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "论文ID") @RequestParam Long paperId,
            @Parameter(description = "比对论文ID列表（可选，null表示已准入比对库全库）") @RequestBody(required = false) List<Long> targetPaperIds) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            log.info("用户请求查重检测: userId={}, paperId={}", userId, paperId);
            
            Result<SimilarityDetectionResult> result = detectionService.detectPaperSimilarity(
                    paperId, targetPaperIds, userId);
            
            if (result.isSuccess()) {
                log.info("查重检测完成: paperId={}, similarity={}%", paperId, 
                        result.getData().getOverallSimilarity());
            }
            
            return result;
        } catch (Exception e) {
            log.error("查重检测失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查重检测失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取查重历史记录
     */
    @GetMapping("/history/{paperId}")
    @Operation(summary = "获取查重历史", description = "获取指定论文的查重检测历史记录")
    public Result<List<SimilarityDetectionResult>> getDetectionHistory(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "论文ID") @PathVariable Long paperId) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            log.debug("用户获取查重历史: userId={}, paperId={}", userId, paperId);

            return detectionService.getDetectionHistory(paperId, userId);
        } catch (Exception e) {
            log.error("获取查重历史失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取查重历史失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 重新检测论文
     */
    @PostMapping("/recheck/{paperId}")
    @Operation(summary = "重新查重检测", description = "重新对本人论文执行查重检测（仅比对已准入论文）")
    public Result<SimilarityDetectionResult> recheckPaper(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "论文ID") @PathVariable Long paperId) {
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            log.info("用户请求重新查重: userId={}, paperId={}", userId, paperId);
            
            return detectionService.detectPaperSimilarity(paperId, null, userId);
        } catch (Exception e) {
            log.error("重新查重失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "重新查重失败: " + "系统异常，请稍后重试");
        }
    }
}