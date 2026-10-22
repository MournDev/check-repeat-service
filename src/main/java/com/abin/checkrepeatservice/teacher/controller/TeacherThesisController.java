package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.admin.service.ThesisArchiveService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教师端论文定稿控制器（P4）：仅教师角色可访问。
 *
 * <p>终期答辩通过（defense_passed）后，指导教师确认最终版本 → finalized；
 * 之后由管理员执行归档（finalized → archived）。</p>
 */
@RestController
@RequestMapping("/api/teacher/thesis")
@PreAuthorize("hasAuthority('TEACHER')")
public class TeacherThesisController {

    @Resource
    private ThesisArchiveService thesisArchiveService;

    /**
     * 定稿确认：仅该论文的指导教师可操作
     */
    @PostMapping("/{paperId}/finalize")
    @OperationLog(type = "teacher_thesis_finalize", description = "教师定稿确认")
    public Result<String> finalize(@PathVariable Long paperId) {
        return thesisArchiveService.finalizePaper(paperId);
    }
}
