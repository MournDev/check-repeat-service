package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.ReviewAllocateReq;
import com.abin.checkrepeatservice.admin.service.AdminReviewAssignService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.teacher.vo.ReviewAssignVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端评阅任务分配控制器（论文全生命周期 P2）：仅管理员角色可访问。
 */
@RestController
@RequestMapping("/api/admin/review-assign")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminReviewAssignController {

    @Resource
    private AdminReviewAssignService adminReviewAssignService;

    /**
     * 分配评阅任务（assignType=manual 手动指定评阅人 / auto 系统挑选，回避指导教师）
     */
    @PostMapping("/allocate")
    @OperationLog(type = "admin_review_assign_allocate", description = "管理员分配评阅任务")
    public Result<Map<String, Object>> allocate(@Valid @RequestBody ReviewAllocateReq req) {
        return adminReviewAssignService.allocate(req);
    }

    /**
     * 评阅任务列表（分页，监控与改派入口）
     */
    @GetMapping("/list")
    public Result<Page<ReviewAssignVO>> listAssignments(
            @RequestParam(value = "paperId", required = false) Long paperId,
            @RequestParam(value = "reviewerId", required = false) Long reviewerId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return adminReviewAssignService.listAssignments(paperId, reviewerId, status, currentPage, pageSize);
    }
}
