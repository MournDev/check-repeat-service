package com.abin.checkrepeatservice.common.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.service.CommonDictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 公共字典接口控制器
 * 提供无权限限制的字典数据查询，供所有角色（学生、教师、管理员）使用
 */
@RestController
@RequestMapping("/api/common/dict")
@Slf4j
@Tag(name = "公共字典接口", description = "无需权限的学院、专业、年级等公共字典数据查询接口")
public class CommonDictController {

    @Resource
    private CommonDictService commonDictService;

    /**
     * 获取学院列表
     * @return 学院列表
     */
    @Operation(summary = "获取学院列表", description = "查询全部学院数据")
    @GetMapping("/colleges")
    public Result<List<Map<String, Object>>> getColleges() {
        log.info("接收获取学院列表请求");
        return commonDictService.getColleges();
    }

    /**
     * 获取专业列表
     * @param collegeId 学院ID（可选参数，不传则返回所有专业）
     * @return 专业列表
     */
    @Operation(summary = "获取专业列表", description = "可按学院ID过滤，不传则返回所有专业")
    @GetMapping("/majors")
    public Result<List<Map<String, Object>>> getMajors(
            @Parameter(description = "学院ID，可选；不传返回所有专业") @RequestParam(required = false) Long collegeId) {
        log.info("接收获取专业列表请求, collegeId={}", collegeId);
        return commonDictService.getMajors(collegeId);
    }

    /**
     * 获取年级列表
     * @return 年级列表
     */
    @Operation(summary = "获取年级列表", description = "查询全部年级数据")
    @GetMapping("/grades")
    public Result<List<Map<String, Object>>> getGrades() {
        log.info("接收获取年级列表请求");
        return commonDictService.getGrades();
    }

    /**
     * 获取专业名称映射
     * @return 专业ID到专业名称的映射
     */
    @Operation(summary = "获取专业名称映射", description = "返回专业ID到专业名称的映射")
    @GetMapping("/majors/map")
    public Result<Map<String, String>> getMajorNameMap() {
        log.info("接收获取专业名称映射请求");
        return commonDictService.getMajorNameMap();
    }
}
