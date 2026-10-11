package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.admin.vo.DefenseBatchVO;
import com.abin.checkrepeatservice.admin.vo.DefenseGroupMemberVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.teacher.dto.DefenseScoreReq;
import com.abin.checkrepeatservice.teacher.service.TeacherDefenseService;
import com.abin.checkrepeatservice.teacher.vo.DefenseRecordVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师端答辩工作台控制器（论文全生命周期 P3）：仅教师角色可访问。
 *
 * 答辩组组成：组长（leader）主持并汇总结论、成员（member）参与评分、秘书（secretary）记录与线下回填。
 * 本控制器只暴露工作台查询与组长评分录入入口，批次管理在 /api/admin/defense/batch/**。
 */
@RestController
@RequestMapping("/api/teacher/defense")
@PreAuthorize("hasAuthority('TEACHER')")
public class TeacherDefenseController {

    @Resource
    private TeacherDefenseService teacherDefenseService;

    /**
     * 我的答辩批次列表（我作为组员/组长/秘书参与的批次）
     */
    @GetMapping("/batches")
    public Result<List<DefenseBatchVO>> listMyBatches() {
        return teacherDefenseService.listMyBatches();
    }

    /**
     * 答辩批次会议信息（须为本批次组成员或秘书）
     */
    @GetMapping("/batch/{id}")
    public Result<DefenseBatchVO> getBatchDetail(@PathVariable("id") Long id) {
        return teacherDefenseService.getBatchDetail(id);
    }

    /**
     * 批次答辩组成员名单（须为本批次组成员或秘书；组长在前，成员在后）
     */
    @GetMapping("/batch/{id}/members")
    public Result<List<DefenseGroupMemberVO>> listBatchMembers(@PathVariable("id") Long id) {
        return teacherDefenseService.listBatchMembers(id);
    }

    /**
     * 批次内答辩记录列表（须为本批次组成员或秘书）
     *
     * @param result 结论过滤（可空：pass / fail / postpone）
     */
    @GetMapping("/batch/{id}/records")
    public Result<List<DefenseRecordVO>> listBatchRecords(
            @PathVariable("id") Long id,
            @RequestParam(value = "result", required = false) String result) {
        return teacherDefenseService.listBatchRecords(id, result);
    }

    /**
     * 评分录入（仅组长可提交结论；终期 result=pass 时回写 final_score 并推进 defense_passed）
     */
    @PostMapping("/score")
    @OperationLog(type = "teacher_defense_score_submit", description = "答辩组长提交评分结论")
    public Result<DefenseRecordVO> submitScore(@Valid @RequestBody DefenseScoreReq req) {
        return teacherDefenseService.submitScore(req);
    }
}
