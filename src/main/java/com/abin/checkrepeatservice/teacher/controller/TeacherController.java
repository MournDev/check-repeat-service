package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.teacher.dto.UpdateTeacherInfoReq;
import com.abin.checkrepeatservice.teacher.service.TeacherInfoService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 教师个人信息控制器
 */
@Slf4j
@RestController
@PreAuthorize("hasAuthority('TEACHER')")
@RequestMapping("/api/teacher/info")
@Tag(name = "教师个人信息", description = "教师个人信息查询、更新与密码修改")
public class TeacherController {

    @Resource
    private TeacherInfoService teacherService;

    /**
     * 教师信息更新
     *
     * @param updateReq
     * @return
     */
    @PostMapping("/update")
    @Operation(summary = "更新教师信息")
    public Result<String> updateInfo(
            @Parameter(description = "教师信息更新请求体", required = true)
            @Valid @RequestBody UpdateTeacherInfoReq updateReq) {
        log.info("接收教师信息更新请求：用户ID={}", updateReq.getUserId());
        teacherService.updateInfo(updateReq);
        return Result.success("信息更新成功");
    }
    /**
     * 获取教师信息
     *
     * @return
     */
    @GetMapping("/get")
    @Operation(summary = "获取教师信息")
    public Result<UpdateTeacherInfoReq> getInfo(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId) {
        if (userId == null){
            return Result.error(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        log.info("接收获取教师信息请求：用户ID={}", userId);
        return  teacherService.getInfoByUserId(userId);
    }
    /**
     * 教师修改密码
     *
     * @return
     */
    @PostMapping("/changePassword")
    @Operation(summary = "修改教师密码")
    public Result<String> changePassword(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId,
            @Parameter(description = "新密码", required = true)
            @RequestParam String newPassword) {
        log.info("接收删除教师信息请求：用户ID={}", userId);
        return  teacherService.changePasswordByUserId(userId, newPassword);
    }
}
