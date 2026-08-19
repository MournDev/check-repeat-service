package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.teacher.dto.UpdateTeacherInfoReq;
import com.abin.checkrepeatservice.teacher.service.TeacherInfoService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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
    public Result<String> updateInfo(@Valid @RequestBody UpdateTeacherInfoReq updateReq) {
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
    public Result<UpdateTeacherInfoReq> getInfo(@RequestParam Long userId) {
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
    public Result<String> changePassword(@RequestParam Long userId, @RequestParam String newPassword) {
        log.info("接收删除教师信息请求：用户ID={}", userId);
        return  teacherService.changePasswordByUserId(userId, newPassword);
    }
}
