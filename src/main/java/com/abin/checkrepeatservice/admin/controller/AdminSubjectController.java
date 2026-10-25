package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.SubjectUpsertReq;
import com.abin.checkrepeatservice.admin.service.AdminSubjectService;
import com.abin.checkrepeatservice.admin.vo.AdminSubjectVO;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端学科领域控制器。
 *
 * 挂在 /api/admin/** 下有两个理由：
 *   1. SecurityConfig 的 URL 规则会给未授权请求返回正确的 403；
 *      若只靠 @PreAuthorize，拒绝会被 GlobalExceptionHandler 的 Exception 兜底成 500。
 *   2. 超级管理员经 UserDetailsServiceImpl 额外持有 ADMIN 权限，无需单独放行。
 *
 * 所有写接口都写成「把 BusinessException 原样抛出」，交给 GlobalExceptionHandler
 * 变成 400 + 自定义文案；落到 catch (Exception) 会被吞成 500「系统繁忙」，
 * 把"编码重复了"这类可自解的提示伪装成服务故障。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/subjects")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN')")
@Api(tags = "学科领域管理")
@Tag(name = "学科领域管理", description = "学科领域树/新增/修改/启停与删除")
public class AdminSubjectController {

    private final AdminSubjectService adminSubjectService;

    /**
     * 学科领域树（门类 → 二级学科），带引用次数与是否有子节点
     */
    @Operation(summary = "获取学科领域树", description = "返回门类与二级学科树，带引用次数与是否有子节点")
    @GetMapping
    @ApiOperation("获取学科领域树")
    public Result<List<AdminSubjectVO>> listTree() {
        return Result.success(adminSubjectService.listTree());
    }

    /**
     * 新增学科领域。parentId 为空建一级门类，否则建该门类下的二级学科
     */
    @Operation(summary = "新增学科领域", description = "parentId 为空建一级门类，否则建该门类下的二级学科")
    @PostMapping
    @ApiOperation("新增学科领域")
    @OperationLog(type = "subject_create", description = "新增学科领域")
    public Result<Long> create(
            @Parameter(description = "学科领域新增请求体", required = true)
            @Valid @RequestBody SubjectUpsertReq req) {
        log.info("接收新增学科领域请求: code={}, name={}, parentId={}", req.getCode(), req.getName(), req.getParentId());
        return Result.success("新增成功", adminSubjectService.create(req));
    }

    /**
     * 修改学科领域（只允许改名称 / 排序 / 启用状态）
     */
    @Operation(summary = "修改学科领域", description = "只允许修改名称/排序/启用状态")
    @PutMapping("/{id}")
    @ApiOperation("修改学科领域")
    @OperationLog(type = "subject_update", description = "修改学科领域")
    public Result<Void> update(
            @Parameter(description = "学科领域ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "学科领域更新请求体", required = true)
            @Valid @RequestBody SubjectUpsertReq req) {
        log.info("接收修改学科领域请求: id={}, name={}", id, req.getName());
        adminSubjectService.update(id, req);
        return Result.success("修改成功");
    }

    /**
     * 启用/停用。停用后不再出现在选择器与阈值候选中，已引用的数据不受影响
     */
    @Operation(summary = "启用或停用学科领域", description = "停用后不再出现在选择器与阈值候选中，已引用数据不受影响")
    @PutMapping("/{id}/enabled")
    @ApiOperation("启用或停用学科领域")
    @OperationLog(type = "subject_enabled", description = "启用或停用学科领域")
    public Result<Void> setEnabled(
            @Parameter(description = "学科领域ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "是否启用", required = true)
            @RequestParam("isEnabled") Boolean isEnabled) {
        log.info("接收启用/停用学科领域请求: id={}, isEnabled={}", id, isEnabled);
        if (isEnabled == null) {
            return Result.error(ResultCode.PARAM_EMPTY, "请指定启用状态");
        }
        adminSubjectService.setEnabled(id, isEnabled);
        return Result.success(isEnabled ? "已启用" : "已停用");
    }

    /**
     * 删除学科领域（物理删除；有子节点或被引用时拒绝）
     */
    @Operation(summary = "删除学科领域", description = "物理删除，有子节点或被引用时拒绝")
    @DeleteMapping("/{id}")
    @ApiOperation("删除学科领域")
    @OperationLog(type = "subject_delete", description = "删除学科领域")
    public Result<Void> delete(
            @Parameter(description = "学科领域ID", required = true)
            @PathVariable("id") Long id) {
        log.info("接收删除学科领域请求: id={}", id);
        adminSubjectService.delete(id);
        return Result.success("删除成功");
    }

}
