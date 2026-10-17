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
public class AssignmentHistoryController {

    @Resource
    private AssignmentHistoryService assignmentHistoryService;

    /**
     * 1. 获取分配记录统计信息
     */
    @GetMapping("/stats")
    public Result<AssignmentHistoryStatsDTO> getAssignmentHistoryStats() {
        log.info("接收获取分配记录统计信息请求");
        try {
            return assignmentHistoryService.getAssignmentHistoryStats();
        } catch (Exception e) {
            log.error("获取分配记录统计信息失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取分配记录统计信息失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 2. 获取分配记录列表
     */
    @GetMapping("/list")
    public Result<Page<AssignmentRecordDTO>> getAssignmentRecordList(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String assignmentType,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        
        log.info("接收获取分配记录列表请求: startDate={}, endDate={}, assignmentType={}, major={}, status={}, keyword={}, page={}, size={}",
                startDate, endDate, assignmentType, major, status, keyword, page, size);
        
        try {
            return assignmentHistoryService.getAssignmentRecordList(
                    startDate, endDate, assignmentType, major, status, keyword, page, size);
        } catch (Exception e) {
            log.error("获取分配记录列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取分配记录列表失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 3. 删除分配记录
     */
    @DeleteMapping
    public Result<String> deleteAssignmentRecords(@RequestBody DeleteAssignmentRecordsDTO request) {
        log.info("接收删除分配记录请求: ids={}", request.getIds());
        try {
            return assignmentHistoryService.deleteAssignmentRecords(request);
        } catch (Exception e) {
            log.error("删除分配记录失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除分配记录失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 4. 撤销指导老师分配
     */
    @PostMapping("/revoke")
    public Result<String> revokeAssignment(@RequestBody RevokeAssignmentDTO request) {
        log.info("接收撤销指导老师分配请求: recordId={}, reason={}", request.getRecordId(), request.getReason());
        try {
            return assignmentHistoryService.revokeAssignment(request);
        } catch (Exception e) {
            log.error("撤销指导老师分配失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "撤销指导老师分配失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 5. 重新分配指导老师
     */
    @PostMapping("/reassign")
    public Result<String> reassignTeacher(@RequestBody ReassignTeacherDTO request) {
        log.info("接收重新分配指导老师请求: recordId={}, newTeacherId={}, reason={}", 
                request.getRecordId(), request.getNewTeacherId(), request.getReason());
        try {
            return assignmentHistoryService.reassignTeacher(request);
        } catch (Exception e) {
            log.error("重新分配指导老师失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "重新分配指导老师失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 6. 获取可用于重新分配的教师列表
     */
    @GetMapping("/available-teachers")
    public Result<List<AvailableTeacherDTO>> getAvailableTeachers() {
        log.info("接收获取可用教师列表请求");
        try {
            return assignmentHistoryService.getAvailableTeachers();
        } catch (Exception e) {
            log.error("获取可用教师列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取可用教师列表失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 7. 导出分配记录
     */
    @GetMapping("/export")
    public void exportAssignmentRecords(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String assignmentType,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String status,
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
    @PostMapping("/refresh")
    public Result<String> refreshAssignmentData() {
        log.info("接收刷新分配记录数据请求");
        try {
            return assignmentHistoryService.refreshAssignmentData();
        } catch (Exception e) {
            log.error("刷新分配记录数据失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "刷新分配记录数据失败: " + "系统异常，请稍后重试");
        }
    }
}