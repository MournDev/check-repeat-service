package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.student.dto.AcademicResourceDTO;
import com.abin.checkrepeatservice.student.dto.ChecklistItemDTO;
import com.abin.checkrepeatservice.student.dto.PersonalAdviceDTO;
import com.abin.checkrepeatservice.student.service.AcademicIntegrityService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
public class AcademicIntegrityController {
    
    @Resource
    private AcademicIntegrityService academicIntegrityService;
    
    /**
     * 获取个性化学术建议
     */
    @GetMapping("/personal-advice")
    @ApiOperation("获取个性化学术建议")
    public Result<PersonalAdviceDTO> getPersonalAdvice() {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            PersonalAdviceDTO advice = academicIntegrityService.getPersonalAdvice(studentId);
            return Result.success("获取个性化学术建议成功", advice);
        } catch (Exception e) {
            log.error("获取个性化学术建议失败", e);
            return Result.error(500, "获取个性化学术建议失败: " + "系统异常，请稍后重试");
        }
    }
    
    /**
     * 获取推荐学习资源
     */
    @GetMapping("/resources")
    @ApiOperation("获取推荐学习资源")
    public Result<List<AcademicResourceDTO>> getRecommendedResources(
            @RequestParam(required = false) String resourceType) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            List<AcademicResourceDTO> resources = academicIntegrityService.getRecommendedResources(studentId, resourceType);
            return Result.success("获取推荐学习资源成功", resources);
        } catch (Exception e) {
            log.error("获取推荐学习资源失败", e);
            return Result.error(500, "获取推荐学习资源失败: " + "系统异常，请稍后重试");
        }
    }
    
    /**
     * 获取用户检查清单
     */
    @GetMapping("/checklist")
    @ApiOperation("获取用户检查清单")
    public Result<List<ChecklistItemDTO>> getChecklist() {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            List<ChecklistItemDTO> checklist = academicIntegrityService.getChecklist(studentId);
            return Result.success("获取用户检查清单成功", checklist);
        } catch (Exception e) {
            log.error("获取用户检查清单失败", e);
            return Result.error(500, "获取用户检查清单失败: " + "系统异常，请稍后重试");
        }
    }
    
    /**
     * 更新检查项状态
     */
    @PutMapping("/checklist/{itemId}")
    @ApiOperation("更新检查项状态")
    public Result<String> updateChecklistItem(
            @PathVariable Long itemId,
            @RequestBody ChecklistItemDTO updateRequest) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            boolean result = academicIntegrityService.updateChecklistItem(studentId, itemId, updateRequest.getChecked());
            if (result) {
                return Result.success("更新检查项状态成功");
            } else {
                return Result.error(500, "更新检查项状态失败");
            }
        } catch (Exception e) {
            log.error("更新检查项状态失败", e);
            return Result.error(500, "更新检查项状态失败: " + "系统异常，请稍后重试");
        }
    }
    
    /**
     * 初始化用户检查清单
     */
    @PostMapping("/checklist/init")
    @ApiOperation("初始化用户检查清单")
    public Result<String> initializeChecklist() {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            academicIntegrityService.initializeChecklist(studentId);
            return Result.success("初始化用户检查清单成功");
        } catch (Exception e) {
            log.error("初始化用户检查清单失败", e);
            return Result.error(500, "初始化用户检查清单失败: " + "系统异常，请稍后重试");
        }
    }
}