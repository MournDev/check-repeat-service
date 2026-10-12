package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.AdvisorEvalSummaryVO;
import com.abin.checkrepeatservice.teacher.service.TeacherAdvisorEvalService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师端导师评价控制器（论文全生命周期 P6a）：仅教师角色可访问。
 *
 * 只暴露聚合汇总（小样本掩码），不提供任何单条明细/评语查询入口。
 */
@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasAuthority('TEACHER')")
public class TeacherAdvisorEvalController {

    @Resource
    private TeacherAdvisorEvalService teacherAdvisorEvalService;

    /**
     * 我收到的导师评价汇总（五维均分 + 导师评价分 + 标签频次；样本&lt;3 掩码）
     */
    @GetMapping("/advisor-evaluation/summary")
    public Result<AdvisorEvalSummaryVO> summary() {
        return teacherAdvisorEvalService.summary();
    }
}
