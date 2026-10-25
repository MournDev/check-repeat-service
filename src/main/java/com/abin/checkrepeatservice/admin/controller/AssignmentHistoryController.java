package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AssignmentHistoryService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.admin.dto.AssignmentHistoryStatsDTO;
import com.abin.checkrepeatservice.admin.dto.AssignmentRecordDTO;
import com.abin.checkrepeatservice.admin.dto.DeleteAssignmentRecordsDTO;
import com.abin.checkrepeatservice.admin.dto.RevokeAssignmentDTO;
import com.abin.checkrepeatservice.admin.dto.ReassignTeacherDTO;
import com.abin.checkrepeatservice.admin.dto.AvailableTeacherDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 分配记录管理控制器
 */
@RestController
@RequestMapping("/api/admin/assignment/history")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
@Tag(name = "分配记录管理", description = "分配记录查询、撤销、重新分配与导出")
public class AssignmentHistoryController {

    @Resource
    private AssignmentHistoryService assignmentHistoryService;

    /**
     * 1. 获取分配记录统计信息
     */
    @Operation(summary = "获取分配记录统计信息")
    @GetMapping("/stats")
    public Result<AssignmentHistoryStatsDTO> getAssignmentHistoryStats() {
        log.info("接收获取分配记录统计信息请求");
        return assignmentHistoryService.getAssignmentHistoryStats();
    }

    /**
     * 2. 获取分配记录列表
     */
    @Operation(summary = "获取分配记录列表", description = "分页查询分配记录，支持日期、类型、专业、状态与关键字筛选")
    @GetMapping("/list")
    public Result<Page<AssignmentRecordDTO>> getAssignmentRecordList(
            @Parameter(description = "开始日期（可选）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（可选）")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "分配类型（可选）")
            @RequestParam(required = false) String assignmentType,
            @Parameter(description = "专业（可选）")
            @RequestParam(required = false) String major,
            @Parameter(description = "分配状态（可选）")
            @RequestParam(required = false) String status,
            @Parameter(description = "关键字（可选，模糊搜索）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size) {
        
        log.info("接收获取分配记录列表请求: startDate={}, endDate={}, assignmentType={}, major={}, status={}, keyword={}, page={}, size={}",
                startDate, endDate, assignmentType, major, status, keyword, page, size);
        
        return assignmentHistoryService.getAssignmentRecordList(
                startDate, endDate, assignmentType, major, status, keyword, page, size);
    }

    /**
     * 3. 删除分配记录
     */
    @Operation(summary = "删除分配记录")
    @DeleteMapping
    public Result<String> deleteAssignmentRecords(@Parameter(description = "待删除的分配记录ID集合", required = true) @RequestBody DeleteAssignmentRecordsDTO request) {
        log.info("接收删除分配记录请求: ids={}", request.getIds());
        return assignmentHistoryService.deleteAssignmentRecords(request);
    }

    /**
     * 4. 撤销指导老师分配
     */
    @Operation(summary = "撤销指导老师分配")
    @PostMapping("/revoke")
    public Result<String> revokeAssignment(@Parameter(description = "撤销分配请求体：记录ID与撤销原因", required = true) @RequestBody RevokeAssignmentDTO request) {
        log.info("接收撤销指导老师分配请求: recordId={}, reason={}", request.getRecordId(), request.getReason());
        return assignmentHistoryService.revokeAssignment(request);
    }

    /**
     * 5. 重新分配指导老师
     */
    @Operation(summary = "重新分配指导老师")
    @PostMapping("/reassign")
    public Result<String> reassignTeacher(@Parameter(description = "重新分配请求体：记录ID、新教师ID与原因", required = true) @RequestBody ReassignTeacherDTO request) {
        log.info("接收重新分配指导老师请求: recordId={}, newTeacherId={}, reason={}", 
                request.getRecordId(), request.getNewTeacherId(), request.getReason());
        return assignmentHistoryService.reassignTeacher(request);
    }

    /**
     * 6. 获取可用于重新分配的教师列表
     */
    @Operation(summary = "获取可用于重新分配的教师列表")
    @GetMapping("/available-teachers")
    public Result<List<AvailableTeacherDTO>> getAvailableTeachers() {
        log.info("接收获取可用教师列表请求");
        return assignmentHistoryService.getAvailableTeachers();
    }

    /**
     * 7. 导出分配记录
     */
    @Operation(summary = "导出分配记录", description = "按筛选条件导出分配记录文件")
    @GetMapping("/export")
    public void exportAssignmentRecords(
            @Parameter(description = "开始日期（可选）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（可选）")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "分配类型（可选）")
            @RequestParam(required = false) String assignmentType,
            @Parameter(description = "专业（可选）")
            @RequestParam(required = false) String major,
            @Parameter(description = "分配状态（可选）")
            @RequestParam(required = false) String status,
            @Parameter(description = "关键字（可选，模糊搜索）")
            @RequestParam(required = false) String keyword,
            HttpServletResponse response) {
            
        log.info("接收导出分配记录请求: startDate={}, endDate={}, assignmentType={}, major={}, status={}, keyword={}"
                , startDate, endDate, assignmentType, major, status, keyword);
            
        try {
            assignmentHistoryService.exportAssignmentRecords(
                    startDate, endDate, assignmentType, major, status, keyword, response);
        } catch (Exception e) {
            log.error("导出分配记录失败: {}", e.getMessage(), e);
            try {
                response.reset();
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":500,\"message\":\"导出失败：" + e.getMessage() + "\"}");
            } catch (Exception ex) {
                log.error("设置错误响应失败", ex);
            }
        }
    }

    /**
     * 8. 刷新分配记录数据
     */
    @Operation(summary = "刷新分配记录数据")
    @PostMapping("/refresh")
    public Result<String> refreshAssignmentData() {
        log.info("接收刷新分配记录数据请求");
        return assignmentHistoryService.refreshAssignmentData();
    }
}