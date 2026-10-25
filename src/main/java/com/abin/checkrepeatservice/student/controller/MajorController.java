package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.entity.Major;
import com.abin.checkrepeatservice.student.service.MajorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/majors")
@Tag(name = "专业管理模块", description = "按学院查询专业列表接口")
public class MajorController {

    @Resource
    private MajorService majorService;

    /**
     * 根据学院ID查询专业接口
     *
     * @param collegeId 学院ID
     * @return 该学院下的专业列表
     */
    @GetMapping("/all")
    @Operation(summary = "根据学院ID查询专业", description = "返回该学院下的专业列表")
    public Result<List<Major>> getMajorsByCollegeId(
            @Parameter(description = "学院ID", required = true) @RequestParam Long collegeId) {
        List<Major> majors = majorService.getMajorsByCollegeId(collegeId);
        return Result.success("专业列表查询成功", majors);
    }
}
