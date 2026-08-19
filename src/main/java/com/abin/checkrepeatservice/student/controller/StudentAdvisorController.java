package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.student.service.StudentMessageService;
import com.abin.checkrepeatservice.student.vo.AdvisorInfoVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生导师信息控制器
 * 提供学生获取导师相关信息的接口
 */
@Slf4j
@RestController
@RequestMapping("/api/student/advisor")
@Api(tags = "学生导师接口", description = "学生获取导师相关信息接口")
public class StudentAdvisorController {

    @Resource
    private StudentMessageService studentMessageService;

    /**
     * 获取导师信息
     * GET /api/student/advisor/info
     */
    @GetMapping("/info")
    @ApiOperation("获取导师信息")
    public Result<AdvisorInfoVO> getAdvisorInfo() {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("获取导师信息 - 学生ID: {}", studentId);
            
            AdvisorInfoVO advisorInfo = studentMessageService.getAdvisorInfo(studentId);
            return Result.success("获取导师信息成功", advisorInfo);
        } catch (Exception e) {
            log.error("获取导师信息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取导师信息失败: " + e.getMessage());
        }
    }
}