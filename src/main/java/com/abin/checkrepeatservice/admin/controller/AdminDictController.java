package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminDictService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;

/**
 * 管理员公共字典控制器
 * 职责：提供系统所需的公共字典数据
 */
@RestController
@RequestMapping("/api/admin/dict")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
@Tag(name = "公共字典", description = "专业/年级/学院等公共字典数据")
public class AdminDictController {

    @Resource
    private AdminDictService adminDictService;

    /**
     * 获取专业列表
     */
    @Operation(summary = "获取专业列表", description = "获取系统专业字典数据")
    @GetMapping("/majors")
    public Result<List<Map<String, Object>>> getMajors() {
        log.info("接收获取专业列表请求");
        return adminDictService.getMajors();
    }

    /**
     * 获取年级列表
     */
    @Operation(summary = "获取年级列表", description = "获取系统年级字典数据")
    @GetMapping("/grades")
    public Result<List<Map<String, Object>>> getGrades() {
        log.info("接收获取年级列表请求");
        return adminDictService.getGrades();
    }

    /**
     * 获取学院列表
     */
    @Operation(summary = "获取学院列表", description = "获取系统学院字典数据")
    @GetMapping("/colleges")
    public Result<List<Map<String, Object>>> getColleges() {
        log.info("接收获取学院列表请求");
        return adminDictService.getColleges();
    }
    
    /**
     * 获取专业名称映射
     * 返回专业代码到专业名称的映射关系
     */
    @Operation(summary = "获取专业名称映射", description = "返回专业代码到专业名称的映射关系")
    @GetMapping("/majors/map")
    public Result<Map<String, String>> getMajorNameMap() {
        log.info("接收获取专业名称映射请求");
        return adminDictService.getMajorNameMap();
    }
}