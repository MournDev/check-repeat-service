package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.student.service.StudentDefenseService;
import com.abin.checkrepeatservice.student.vo.StudentDefenseVO;
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
public class StudentDefenseController {

    @Resource
    private StudentDefenseService studentDefenseService;

    /**
     * 我的答辩安排（按答辩时间倒序，含已出结论）
     *
     * @param batchType 批次类型过滤（可空：mid_term / final）
     */
    @GetMapping("/my")
    public Result<List<StudentDefenseVO>> listMyDefense(
            @RequestParam(value = "batchType", required = false) String batchType) {
        return studentDefenseService.listMyDefense(batchType);
    }
}
