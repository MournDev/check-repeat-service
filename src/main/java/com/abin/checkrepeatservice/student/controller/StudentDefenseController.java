package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.service.StudentDefenseService;
import com.abin.checkrepeatservice.student.vo.StudentDefenseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端答辩安排控制器（论文全生命周期 P3）：仅学生角色可访问。
 */
@RestController
@RequestMapping("/api/student/defense")
@PreAuthorize("hasAuthority('STUDENT')")
@Tag(name = "学生答辩安排模块", description = "学生查询本人答辩安排接口")
public class StudentDefenseController {

    @Resource
    private StudentDefenseService studentDefenseService;

    /**
     * 我的答辩安排（按答辩时间倒序，含已出结论）
     *
     * @param batchType 批次类型过滤（可空：mid_term / final）
     */
    @GetMapping("/my")
    @Operation(summary = "查询我的答辩安排", description = "按答辩时间倒序返回，含已出结论")
    public Result<List<StudentDefenseVO>> listMyDefense(
            @Parameter(description = "批次类型过滤（可空：mid_term / final）") @RequestParam(value = "batchType", required = false) String batchType) {
        return studentDefenseService.listMyDefense(batchType);
    }
}
