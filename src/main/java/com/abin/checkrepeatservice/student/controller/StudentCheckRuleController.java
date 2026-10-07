package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.student.service.StudentCheckRuleService;
import com.abin.checkrepeatservice.student.vo.StudentCheckRuleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端查重规则查询控制器。
 *
 * <p>只读：规则的增删改仍在管理端 /api/admin/config/check-rule/**。
 * 路径落在 /api/student/** 下，由 SecurityConfig 的过滤器链统一限定 STUDENT 角色。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/student/check-rule")
@PreAuthorize("hasAuthority('STUDENT')")
@Tag(name = "学生查重规则接口", description = "学生查看当前生效的查重规则")
public class StudentCheckRuleController {

    @Resource
    private StudentCheckRuleService studentCheckRuleService;

    @Resource
    private JwtUtils jwtUtils;

    /**
     * 获取当前生效的查重规则
     */
    @GetMapping("/current")
    @Operation(summary = "获取当前查重规则", description = "获取当前登录学生生效的查重规则（含按学院/专业解析出的通过阈值）")
    public Result<StudentCheckRuleVO> getCurrentCheckRule(
            @RequestHeader("Authorization") String token) {
        try {
            Long studentId = jwtUtils.getUserIdFromToken(token);
            return studentCheckRuleService.getCurrentCheckRule(studentId);
        } catch (Exception e) {
            log.error("获取当前查重规则失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取查重规则失败");
        }
    }
}
