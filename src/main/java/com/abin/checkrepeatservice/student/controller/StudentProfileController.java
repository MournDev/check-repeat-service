package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.student.dto.StudentProfileDTO;
import com.abin.checkrepeatservice.student.dto.UpdateProfileReq;
import com.abin.checkrepeatservice.student.service.StudentProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 学生个人信息管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/student/profile")
@PreAuthorize("hasAuthority('STUDENT')")
@Tag(name = "学生个人信息接口", description = "学生个人资料管理相关接口")
public class StudentProfileController {

    @Resource
    private StudentProfileService studentProfileService;
    
    @Resource
    private JwtUtils jwtUtils;

    /**
     * 获取个人资料
     */
    @GetMapping
    @Operation(summary = "获取个人资料", description = "获取当前登录学生的个人资料信息")
    public Result<StudentProfileDTO> getProfile(@RequestHeader("Authorization") String token) throws Exception {
        Long studentId = jwtUtils.getUserIdFromToken(token);
        log.info("学生请求获取个人资料: studentId={}", studentId);
        return studentProfileService.getStudentProfile(studentId);
    }

    /**
     * 更新个人资料
     */
    @PutMapping
    @Operation(summary = "更新个人资料", description = "更新学生的个人资料信息")
    public Result<String> updateProfile(
            @RequestHeader("Authorization") String token,
            @RequestBody @Valid UpdateProfileReq updateReq) throws Exception {
        Long studentId = jwtUtils.getUserIdFromToken(token);
        log.info("学生请求更新个人资料: studentId={}", studentId);
        return studentProfileService.updateProfile(studentId, updateReq);
    }

    /**
     * 获取导师信息
     */
    @GetMapping("/advisor")
    @Operation(summary = "获取导师信息", description = "获取当前指导教师的信息")
    public Result<StudentProfileDTO.AdvisorInfo> getAdvisorInfo(@RequestHeader("Authorization") String token) throws Exception {
        Long studentId = jwtUtils.getUserIdFromToken(token);
        log.info("学生请求获取导师信息: studentId={}", studentId);
        return studentProfileService.getAdvisorInfo(studentId);
    }

    /**
     * 发送消息给导师
     */
    @PostMapping("/advisor/message")
    @Operation(summary = "发送消息给导师", description = "向指导教师发送咨询消息")
    public Result<String> sendMessageToAdvisor(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "导师ID") @RequestParam Long advisorId,
            @Parameter(description = "消息内容") @RequestParam String content) throws Exception {
        Long studentId = jwtUtils.getUserIdFromToken(token);
        log.info("学生向导师发送消息: studentId={}, advisorId={}", studentId, advisorId);
        return studentProfileService.sendMessageToAdvisor(studentId, advisorId, content);
    }

    /**
     * 获取沟通记录
     */
    @GetMapping("/advisor/messages")
    @Operation(summary = "获取沟通记录", description = "获取与指导教师的历史沟通记录")
    public Result<Object> getCommunicationHistory(
            @RequestHeader("Authorization") String token,
            @Parameter(description = "导师ID") @RequestParam Long advisorId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") Integer pageSize) throws Exception {
        Long studentId = jwtUtils.getUserIdFromToken(token);
        log.info("学生请求获取沟通记录: studentId={}, advisorId={}", studentId, advisorId);
        return studentProfileService.getCommunicationHistory(studentId, advisorId, pageNum, pageSize);
    }
}