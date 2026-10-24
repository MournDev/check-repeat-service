package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SystemMessage;
import com.abin.checkrepeatservice.teacher.service.TeacherAssignmentService;
import com.abin.checkrepeatservice.user.service.AdvisorAssignService;
import com.abin.checkrepeatservice.user.service.MessageService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assignment")
@PreAuthorize("hasAuthority('TEACHER')")
@Api(tags = "教师论文分配操作")
@Slf4j
public class TeacherAssignmentController {

    @Resource
    private TeacherAssignmentService teacherAssignmentService;

    @Resource
    private MessageService messageService;

    /**
     * 获取待确认论文列表
     */
    @GetMapping("/pending-papers")
    public Result<Page<PaperInfo>> getPendingPapers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        // P0-4：操作人一律取 JWT 当前登录人，不接受请求参数里的 teacherId
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("获取待确认论文列表 - 教师ID: {}, 页码: {}, 页大小: {}", teacherId, pageNum, pageSize);
        return teacherAssignmentService.getPendingConfirmPapers(teacherId, pageNum, pageSize);
    }

    /**
     * 确认接收论文
     */
    @PostMapping("/confirm")
    @OperationLog(type = "teacher_confirm_assignment", description = "教师确认论文分配")
    public Result<Boolean> confirmPaper(@RequestParam Long paperId) {
        // P0-4：从登录上下文取教师 ID，防止伪造 teacherId 代他人确认
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("教师确认接收论文 - 论文ID: {}, 教师ID: {}", paperId, teacherId);
        return teacherAssignmentService.confirmAssignment(paperId, teacherId);
    }

    /**
     * 拒绝接收论文
     */
    @PostMapping("/reject")
    @OperationLog(type = "teacher_reject_assignment", description = "教师拒绝论文分配")
    public Result<Boolean> rejectPaper(@RequestParam Long paperId) {
        // P0-4：同上，操作主体只认当前登录人
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("教师拒绝接收论文 - 论文ID: {}, 教师ID: {}", paperId, teacherId);
        return teacherAssignmentService.rejectAssignment(paperId, teacherId);
    }

}
