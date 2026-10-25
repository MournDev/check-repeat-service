package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.student.dto.AcademicResourceDTO;
import com.abin.checkrepeatservice.student.dto.ChecklistItemDTO;
import com.abin.checkrepeatservice.student.dto.PersonalAdviceDTO;
import com.abin.checkrepeatservice.student.service.AcademicIntegrityService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 学术诚信控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/student/academic-integrity")
@Api(tags = "学生学术诚信管理")
@Tag(name = "学生学术诚信模块", description = "学术建议、推荐资源与检查清单接口")
public class AcademicIntegrityController {
    
    @Resource
    private AcademicIntegrityService academicIntegrityService;
    
    /**
     * 获取个性化学术建议
     */
    @GetMapping("/personal-advice")
    @Operation(summary = "获取个性化学术建议")
    @ApiOperation("获取个性化学术建议")
    public Result<PersonalAdviceDTO> getPersonalAdvice() {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        PersonalAdviceDTO advice = academicIntegrityService.getPersonalAdvice(studentId);
        return Result.success("获取个性化学术建议成功", advice);
    }
    
    /**
     * 获取推荐学习资源
     */
    @GetMapping("/resources")
    @Operation(summary = "获取推荐学习资源")
    @ApiOperation("获取推荐学习资源")
    public Result<List<AcademicResourceDTO>> getRecommendedResources(
            @Parameter(description = "资源类型") @RequestParam(required = false) String resourceType) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        List<AcademicResourceDTO> resources = academicIntegrityService.getRecommendedResources(studentId, resourceType);
        return Result.success("获取推荐学习资源成功", resources);
    }
    
    /**
     * 获取用户检查清单
     */
    @GetMapping("/checklist")
    @Operation(summary = "获取用户检查清单")
    @ApiOperation("获取用户检查清单")
    public Result<List<ChecklistItemDTO>> getChecklist() {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        List<ChecklistItemDTO> checklist = academicIntegrityService.getChecklist(studentId);
        return Result.success("获取用户检查清单成功", checklist);
    }
    
    /**
     * 更新检查项状态
     */
    @PutMapping("/checklist/{itemId}")
    @Operation(summary = "更新检查项状态")
    @ApiOperation("更新检查项状态")
    public Result<String> updateChecklistItem(
            @Parameter(description = "检查项ID", required = true) @PathVariable Long itemId,
            @Parameter(description = "检查项更新请求", required = true) @RequestBody ChecklistItemDTO updateRequest) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        boolean result = academicIntegrityService.updateChecklistItem(studentId, itemId, updateRequest.getChecked());
        if (result) {
            return Result.success("更新检查项状态成功");
        } else {
            return Result.error(500, "更新检查项状态失败");
        }
    }
    
    /**
     * 初始化用户检查清单
     */
    @PostMapping("/checklist/init")
    @Operation(summary = "初始化用户检查清单")
    @ApiOperation("初始化用户检查清单")
    public Result<String> initializeChecklist() {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        academicIntegrityService.initializeChecklist(studentId);
        return Result.success("初始化用户检查清单成功");
    }
}