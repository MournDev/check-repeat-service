package com.abin.checkrepeatservice.superadmin.controller;

import com.abin.checkrepeatservice.admin.mapper.CheckPolicyMapper;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.CheckPolicy;
import com.abin.checkrepeatservice.superadmin.dto.CheckPolicySaveReq;
import com.abin.checkrepeatservice.superadmin.dto.CheckPolicyVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检测策略集控制器（P5 论文全生命周期·超管端）
 *
 * 按 degree_type 维护 check_policy 一行一学位类型的检测策略；
 * 解析侧由 CheckPolicyResolver 消费：命中 enabled=1 的策略取其 threshold，
 * 未命中回落 SimilarityThresholdResolver 既有链路，因此这里删/停某条策略是安全的。
 * 职责分离：仅超管可读写（SUPER_ADMIN 不下放）。
 */
@Slf4j
@RestController
@RequestMapping("/api/super/check-policy")
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class CheckPolicyController {

    @Resource
    private CheckPolicyMapper checkPolicyMapper;

    /** degree_type 合法取值（与 PaperInfo 默认值、字典 degree_type 对齐） */
    private static final Set<String> ALLOWED_DEGREE_TYPES = Set.of("undergraduate", "master", "doctor");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * 全部策略列表（按 degreeType 排序）
     */
    @GetMapping("/list")
    public Result<List<CheckPolicyVO>> list() {
        try {
            List<CheckPolicy> policies = checkPolicyMapper.selectList(
                    new LambdaQueryWrapper<CheckPolicy>()
                            .orderByAsc(CheckPolicy::getDegreeType));
            return Result.success("查询成功", policies.stream()
                    .map(this::toVO)
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("查询检测策略列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "查询失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 新增或按 degreeType 更新策略
     */
    @PostMapping("/save")
    @OperationLog(type = "check_policy_save", description = "保存检测策略")
    public Result<String> save(@RequestBody CheckPolicySaveReq req) {
        // ---- 参数校验（失败走 PARAM_* 错误码，不进写库分支）----
        if (req == null || req.getDegreeType() == null || req.getDegreeType().isBlank()) {
            return Result.error(ResultCode.PARAM_EMPTY, "degreeType 不能为空");
        }
        String degreeType = req.getDegreeType().trim();
        if (!ALLOWED_DEGREE_TYPES.contains(degreeType)) {
            return Result.error(ResultCode.PARAM_VALUE_INVALID,
                    "degreeType 只能是 undergraduate/master/doctor");
        }
        if (req.getThreshold() == null || !inRange(req.getThreshold())) {
            return Result.error(ResultCode.PARAM_VALUE_INVALID, "threshold 必填且范围 0-100");
        }
        if (req.getMaxPerSource() == null || !inRange(req.getMaxPerSource())) {
            return Result.error(ResultCode.PARAM_VALUE_INVALID, "maxPerSource 必填且范围 0-100");
        }
        if (req.getMaxCheckCount() != null && req.getMaxCheckCount() <= 0) {
            return Result.error(ResultCode.PARAM_VALUE_INVALID, "maxCheckCount 为正整数或留空");
        }
        if (req.getCheckInterval() != null && req.getCheckInterval() < 0) {
            return Result.error(ResultCode.PARAM_VALUE_INVALID, "checkInterval 不能为负数");
        }
        int enabled = req.getEnabled() == null ? 1 : req.getEnabled();
        if (enabled != 0 && enabled != 1) {
            return Result.error(ResultCode.PARAM_VALUE_INVALID, "enabled 只能是 0 或 1");
        }

        try {
            // 按 degreeType upsert；唯一键冲突兜底取最新一条改写，避免并发新增撞唯一索引报 500
            CheckPolicy existing = checkPolicyMapper.selectOne(
                    new LambdaQueryWrapper<CheckPolicy>()
                            .eq(CheckPolicy::getDegreeType, degreeType)
                            .orderByDesc(CheckPolicy::getId)
                            .last("LIMIT 1"));
            boolean creating = existing == null;
            CheckPolicy policy = creating ? new CheckPolicy() : existing;
            policy.setDegreeType(degreeType);
            policy.setThreshold(req.getThreshold());
            policy.setMaxPerSource(req.getMaxPerSource());
            policy.setMaxCheckCount(req.getMaxCheckCount());
            policy.setCheckInterval(req.getCheckInterval());
            policy.setEnabled(enabled);
            if (creating) {
                checkPolicyMapper.insert(policy);
            } else {
                checkPolicyMapper.updateById(policy);
            }
            log.info("检测策略保存成功: degreeType={}, id={}, mode={}",
                    degreeType, policy.getId(), creating ? "insert" : "update");
            return Result.success(creating ? "新增成功" : "更新成功");
        } catch (Exception e) {
            log.error("保存检测策略失败: degreeType={}", degreeType, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "保存失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 按 ID 删除策略（BaseEntity @TableLogic 软删除）
     */
    @DeleteMapping("/{id}")
    @OperationLog(type = "check_policy_delete", description = "删除检测策略")
    public Result<String> delete(@PathVariable Long id) {
        try {
            CheckPolicy existing = checkPolicyMapper.selectById(id);
            if (existing == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "策略不存在");
            }
            checkPolicyMapper.deleteById(id);
            log.info("检测策略删除成功: id={}, degreeType={}", id, existing.getDegreeType());
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除检测策略失败: id={}, error={}", id, e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除失败: " + "系统异常，请稍后重试");
        }
    }

    /** 百分比取值是否落在 [0, 100] */
    private boolean inRange(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(HUNDRED) <= 0;
    }

    private CheckPolicyVO toVO(CheckPolicy policy) {
        CheckPolicyVO vo = new CheckPolicyVO();
        vo.setId(policy.getId());
        vo.setDegreeType(policy.getDegreeType());
        vo.setThreshold(policy.getThreshold());
        vo.setMaxPerSource(policy.getMaxPerSource());
        vo.setMaxCheckCount(policy.getMaxCheckCount());
        vo.setCheckInterval(policy.getCheckInterval());
        vo.setEnabled(policy.getEnabled());
        vo.setCreateTime(policy.getCreateTime());
        vo.setUpdateTime(policy.getUpdateTime());
        return vo;
    }
}
