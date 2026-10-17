package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminDictService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
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
public class AdminDictController {

    @Resource
    private AdminDictService adminDictService;

    /**
     * 获取专业列表
     */
    @GetMapping("/majors")
    public Result<List<Map<String, Object>>> getMajors() {
        log.info("接收获取专业列表请求");
        try {
            return adminDictService.getMajors();
        } catch (Exception e) {
            log.error("获取专业列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取专业列表失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取年级列表
     */
    @GetMapping("/grades")
    public Result<List<Map<String, Object>>> getGrades() {
        log.info("接收获取年级列表请求");
        try {
            return adminDictService.getGrades();
        } catch (Exception e) {
            log.error("获取年级列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取年级列表失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 获取学院列表
     */
    @GetMapping("/colleges")
    public Result<List<Map<String, Object>>> getColleges() {
        log.info("接收获取学院列表请求");
        try {
            return adminDictService.getColleges();
        } catch (Exception e) {
            log.error("获取学院列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取学院列表失败: " + "系统异常，请稍后重试");
        }
    }
    
    /**
     * 获取专业名称映射
     * 返回专业代码到专业名称的映射关系
     */
    @GetMapping("/majors/map")
    public Result<Map<String, String>> getMajorNameMap() {
        log.info("接收获取专业名称映射请求");
        try {
            return adminDictService.getMajorNameMap();
        } catch (Exception e) {
            log.error("获取专业名称映射失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取专业名称映射失败: " + "系统异常，请稍后重试");
        }
    }
}