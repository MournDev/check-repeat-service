package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.SimilarityThresholdUpdateReq;
import com.abin.checkrepeatservice.admin.service.SimilarityThresholdService;
import com.abin.checkrepeatservice.admin.vo.SimilarityThresholdVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 相似度阈值设置控制器。
 *
 * 挂在 /api/admin/** 下有两个理由：
 *   1. SecurityConfig 的 URL 规则会给未授权请求返回正确的 403；
 *      若只靠 @PreAuthorize，拒绝会被 GlobalExceptionHandler 的 Exception 兜底成 500。
 *   2. 超级管理员经 UserDetailsServiceImpl 额外持有 ADMIN 权限，无需单独放行。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/similarity-thresholds")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Api(tags = "相似度阈值设置")
@Tag(name = "相似度阈值设置", description = "相似度阈值设置的获取与更新")
public class SimilarityThresholdController {

    private final SimilarityThresholdService similarityThresholdService;

    /**
     * 获取相似度阈值设置
     */
    @Operation(summary = "获取相似度阈值设置")
    @GetMapping
    @ApiOperation("获取相似度阈值设置")
    public Result<SimilarityThresholdVO> getThresholds() {
        return Result.success(similarityThresholdService.getThresholds());
    }

    /**
     * 更新相似度阈值设置（整体覆盖）
     */
    @Operation(summary = "更新相似度阈值设置", description = "整体覆盖")
    @PutMapping
    @ApiOperation("更新相似度阈值设置")
    @OperationLog(type = "similarity_threshold_update", description = "更新相似度阈值设置")
    public Result<Void> updateThresholds(@Parameter(description = "相似度阈值设置", required = true) @RequestBody SimilarityThresholdUpdateReq req) {
        similarityThresholdService.updateThresholds(req);
        return Result.success("相似度阈值设置更新成功");
    }

}
