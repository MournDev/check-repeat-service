package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.dto.DefenseBatchReq;
import com.abin.checkrepeatservice.admin.dto.DefenseEnrollReq;
import com.abin.checkrepeatservice.admin.dto.DefenseGroupMemberReq;
import com.abin.checkrepeatservice.admin.service.AdminDefenseService;
import com.abin.checkrepeatservice.admin.vo.DefenseBatchVO;
import com.abin.checkrepeatservice.admin.vo.DefenseGroupMemberVO;
import com.abin.checkrepeatservice.admin.vo.DefenseRecordExcelVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.teacher.vo.DefenseRecordVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 管理端答辩批次管理控制器（论文全生命周期 P3）：仅管理员角色可访问。
 *
 * 路由前缀：/api/admin/defense/batch/**（批次 CRUD、答辩组分配、学生入批、记录列表）。
 * 中期/终期共用同一套接口，batch_type 区分。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/defense/batch")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminDefenseController {

    @Resource
    private AdminDefenseService adminDefenseService;

    /**
     * 新建答辩批次
     */
    @PostMapping
    @OperationLog(type = "admin_defense_batch_create", description = "管理员新建答辩批次")
    public Result<DefenseBatchVO> createBatch(@Valid @RequestBody DefenseBatchReq req) {
        return adminDefenseService.createBatch(req);
    }

    /**
     * 编辑答辩批次（批次编号与类型不可改）
     */
    @PutMapping("/{id}")
    @OperationLog(type = "admin_defense_batch_update", description = "管理员编辑答辩批次")
    public Result<DefenseBatchVO> updateBatch(@PathVariable("id") Long id,
                                              @Valid @RequestBody DefenseBatchReq req) {
        return adminDefenseService.updateBatch(id, req);
    }

    /**
     * 答辩批次分页列表
     */
    @GetMapping("/list")
    public Result<Page<DefenseBatchVO>> listBatches(
            @RequestParam(value = "batchType", required = false) String batchType,
            @RequestParam(value = "collegeId", required = false) Long collegeId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return adminDefenseService.listBatches(batchType, collegeId, status, currentPage, pageSize);
    }

    /**
     * 答辩批次详情
     */
    @GetMapping("/{id}")
    public Result<DefenseBatchVO> getBatchDetail(@PathVariable("id") Long id) {
        return adminDefenseService.getBatchDetail(id);
    }

    /**
     * 删除答辩批次（仅筹备中状态可删）
     */
    @DeleteMapping("/{id}")
    @OperationLog(type = "admin_defense_batch_delete", description = "管理员删除答辩批次")
    public Result<Void> deleteBatch(@PathVariable("id") Long id) {
        return adminDefenseService.deleteBatch(id);
    }

    /**
     * 分配答辩组成员（1 组长 + N 成员）
     */
    @PostMapping("/{id}/members")
    @OperationLog(type = "admin_defense_members_assign", description = "管理员分配答辩组成员")
    public Result<Map<String, Object>> assignMembers(@PathVariable("id") Long id,
                                                     @Valid @RequestBody DefenseGroupMemberReq req) {
        return adminDefenseService.assignMembers(id, req);
    }

    /**
     * 批次答辩组成员名单（组长在前，成员在后）
     */
    @GetMapping("/{id}/members")
    public Result<List<DefenseGroupMemberVO>> listBatchMembers(@PathVariable("id") Long id) {
        return adminDefenseService.listBatchMembers(id);
    }

    /**
     * 学生入批（按批次类型经阶段门推进 paper_status）
     */
    @PostMapping("/{id}/enroll")
    @OperationLog(type = "admin_defense_enroll", description = "管理员安排学生入答辩批次")
    public Result<Map<String, Object>> enrollStudents(@PathVariable("id") Long id,
                                                      @Valid @RequestBody DefenseEnrollReq req) {
        return adminDefenseService.enrollStudents(id, req);
    }

    /**
     * 批次内答辩记录列表（监控与导出入口）
     */
    @GetMapping("/{id}/records")
    public Result<Page<DefenseRecordVO>> listBatchRecords(
            @PathVariable("id") Long id,
            @RequestParam(value = "studentId", required = false) Long studentId,
            @RequestParam(value = "result", required = false) String result,
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return adminDefenseService.listBatchRecords(id, studentId, result, currentPage, pageSize);
    }

    /**
     * 导出批次内答辩记录（EasyExcel xlsx，按顺序号升序，不分页导出全量）
     */
    @GetMapping("/{id}/records/export")
    public void exportBatchRecords(@PathVariable("id") Long id, HttpServletResponse response) {
        try {
            List<DefenseRecordExcelVO> rows = adminDefenseService.buildExportRecords(id);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = "答辩记录_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");
            com.alibaba.excel.EasyExcel.write(response.getOutputStream(), DefenseRecordExcelVO.class)
                    .sheet("答辩记录")
                    .doWrite(rows);
            log.info("导出答辩记录成功: batchId={}, 记录数={}", id, rows.size());
        } catch (Exception e) {
            log.error("导出答辩记录失败: batchId={}", id, e);
            try {
                response.getWriter().write("{\"code\":500,\"message\":\"导出失败：" + e.getMessage() + "\"}");
            } catch (Exception ex) {
                log.error("设置错误响应失败", ex);
            }
        }
    }
}
