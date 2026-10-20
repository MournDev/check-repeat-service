package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.teacher.dto.GuidanceProgressVO;
import com.abin.checkrepeatservice.teacher.dto.PaperCheckSignoffReq;
import com.abin.checkrepeatservice.teacher.dto.PaperCheckSignoffVO;
import com.abin.checkrepeatservice.teacher.service.CheckReportSignoffService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 教师端查重签核与指导看板控制器（论文全生命周期 P5）：仅教师角色可访问。
 *
 * SecurityConfig 已按 /api/teacher/** 限 TEACHER，这里沿用 teacher 包既有惯例
 *（如 TeacherDefenseController）补类级 @PreAuthorize 做双保险；
 * 查重任务管理在 /api/teacher/check-task/**（TeacherCheckTaskController），本控制器只管签核与看板。
 */
@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasAuthority('TEACHER')")
public class TeacherCheckController {

    @Resource
    private CheckReportSignoffService checkReportSignoffService;

    /**
     * 签核论文最新一次查重报告（qualified=1 合格放行 / 0 不合格退回；指导评分统一必填）
     */
    @PostMapping("/papers/{paperId}/check-signoff")
    @OperationLog(type = "teacher_check_signoff", description = "教师签核查重报告")
    public Result<String> signoff(@PathVariable("paperId") Long paperId,
                                  @Valid @RequestBody PaperCheckSignoffReq req) {
        // 路径参数优先：覆盖请求体里的 paperId，避免前端 URL 与 body 传不一致对象时签错论文
        req.setPaperId(paperId);
        return checkReportSignoffService.sign(req);
    }

    /**
     * 查询论文最新一次签核记录（未签核时 data 为 null）
     */
    @GetMapping("/papers/{paperId}/check-signoff")
    public Result<PaperCheckSignoffVO> getSignoff(@PathVariable("paperId") Long paperId) {
        return checkReportSignoffService.getSignoff(paperId);
    }

    /**
     * 指导看板：我指导论文的状态分布 + 最近 50 条明细（相似比/总评成绩一览）
     */
    @GetMapping("/dashboard/guidance-progress")
    public Result<GuidanceProgressVO> guidanceProgress() {
        return checkReportSignoffService.guidanceProgress();
    }
}
