package com.abin.checkrepeatservice.user.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.user.service.SysUserMajorService;
import com.abin.checkrepeatservice.user.vo.AdvisorMaxCountVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;


/**
 * 用户-专业关联控制器（指导任务上限相关接口）
 */
@RestController
@RequestMapping("/api/sys/user-major")
@Api(tags = "指导任务上限管理")
public class SysUserMajorController {

    @Resource
    private SysUserMajorService sysUserMajorService;

    /**
     * 修改指导任务上限接口（仅管理员可调用）
     * @param operatorId 当前登录用户ID（由拦截器从Token中解析，放入RequestAttribute）
     * @param targetUserId 目标教师ID
     * @param majorId 专业ID
     * @param newMaxCount 新上限（1-50）
     * @return 结果VO
     */
    @PostMapping("/modify-advisor-max-count")
    @ApiOperation(value = "修改指导任务上限", notes = "仅管理员可操作，新上限需在1-50之间")
    public Result<AdvisorMaxCountVO> modifyAdvisorMaxCount(
            // 从RequestAttribute获取当前登录用户ID（避免前端传参篡改）
            @RequestAttribute("loginUserId") Long operatorId,
            @ApiParam(value = "目标教师ID", required = true) @RequestParam @NotNull Long targetUserId,
            @ApiParam(value = "专业ID", required = true) @RequestParam @NotNull Long majorId,
            @ApiParam(value = "新指导任务上限（1-50）", required = true)
            @RequestParam @NotNull @Min(1) @Max(50) Integer newMaxCount
    ) {
        AdvisorMaxCountVO result = sysUserMajorService.modifyAdvisorMaxCount(
                operatorId, targetUserId, majorId, newMaxCount
        );
        return Result.success("指导任务上限修改成功", result);
    }
}
