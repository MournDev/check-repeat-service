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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "答辩批次管理", description = "答辩批次CRUD/成员分配/学生入批/记录导出")
public class AdminDefenseController {

    @Resource
    private AdminDefenseService adminDefenseService;

    /**
     * 新建答辩批次
     */
    @Operation(summary = "新建答辩批次", description = "按批次类型创建中期/终期答辩批次")
    @PostMapping
    @OperationLog(type = "admin_defense_batch_create", description = "管理员新建答辩批次")
    public Result<DefenseBatchVO> createBatch(
            @Parameter(description = "答辩批次创建请求体", required = true)
            @Valid @RequestBody DefenseBatchReq req) {
        return adminDefenseService.createBatch(req);
    }

    /**
     * 编辑答辩批次（批次编号与类型不可改）
     */
    @Operation(summary = "编辑答辩批次", description = "批次编号与类型不可改")
    @PutMapping("/{id}")
    @OperationLog(type = "admin_defense_batch_update", description = "管理员编辑答辩批次")
    public Result<DefenseBatchVO> updateBatch(
            @Parameter(description = "答辩批次ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "答辩批次更新请求体", required = true)
            @Valid @RequestBody DefenseBatchReq req) {
        return adminDefenseService.updateBatch(id, req);
    }

    /**
     * 答辩批次分页列表
     */
    @Operation(summary = "答辩批次分页列表", description = "按批次类型、学院与状态分页查询答辩批次")
    @GetMapping("/list")
    public Result<Page<DefenseBatchVO>> listBatches(
            @Parameter(description = "批次类型（可选）")
            @RequestParam(value = "batchType", required = false) String batchType,
            @Parameter(description = "学院ID（可选）")
            @RequestParam(value = "collegeId", required = false) Long collegeId,
            @Parameter(description = "批次状态（可选）")
            @RequestParam(value = "status", required = false) Integer status,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return adminDefenseService.listBatches(batchType, collegeId, status, currentPage, pageSize);
    }

    /**
     * 答辩批次详情
     */
    @Operation(summary = "答辩批次详情", description = "查询指定答辩批次的详细信息")
    @GetMapping("/{id}")
    public Result<DefenseBatchVO> getBatchDetail(
            @Parameter(description = "答辩批次ID", required = true)
            @PathVariable("id") Long id) {
        return adminDefenseService.getBatchDetail(id);
    }

    /**
     * 删除答辩批次（仅筹备中状态可删）
     */
    @Operation(summary = "删除答辩批次", description = "仅筹备中状态的批次可删除")
    @DeleteMapping("/{id}")
    @OperationLog(type = "admin_defense_batch_delete", description = "管理员删除答辩批次")
    public Result<Void> deleteBatch(
            @Parameter(description = "答辩批次ID", required = true)
            @PathVariable("id") Long id) {
        return adminDefenseService.deleteBatch(id);
    }

    /**
     * 分配答辩组成员（1 组长 + N 成员）
     */
    @Operation(summary = "分配答辩组成员", description = "为答辩批次分配 1 名组长与 N 名成员")
    @PostMapping("/{id}/members")
    @OperationLog(type = "admin_defense_members_assign", description = "管理员分配答辩组成员")
    public Result<Map<String, Object>> assignMembers(
            @Parameter(description = "答辩批次ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "答辩组成员分配请求体", required = true)
            @Valid @RequestBody DefenseGroupMemberReq req) {
        return adminDefenseService.assignMembers(id, req);
    }

    /**
     * 批次答辩组成员名单（组长在前，成员在后）
     */
    @Operation(summary = "查询答辩组成员名单", description = "组长在前，成员在后")
    @GetMapping("/{id}/members")
    public Result<List<DefenseGroupMemberVO>> listBatchMembers(
            @Parameter(description = "答辩批次ID", required = true)
            @PathVariable("id") Long id) {
        return adminDefenseService.listBatchMembers(id);
    }

    /**
     * 学生入批（按批次类型经阶段门推进 paper_status）
     */
    @Operation(summary = "学生入批", description = "按批次类型经阶段门推进论文状态")
    @PostMapping("/{id}/enroll")
    @OperationLog(type = "admin_defense_enroll", description = "管理员安排学生入答辩批次")
    public Result<Map<String, Object>> enrollStudents(
            @Parameter(description = "答辩批次ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "学生入批请求体", required = true)
            @Valid @RequestBody DefenseEnrollReq req) {
        return adminDefenseService.enrollStudents(id, req);
    }

    /**
     * 批次内答辩记录列表（监控与导出入口）
     */
    @Operation(summary = "批次内答辩记录列表", description = "分页查询批次内的答辩记录，用于监控与导出")
    @GetMapping("/{id}/records")
    public Result<Page<DefenseRecordVO>> listBatchRecords(
            @Parameter(description = "答辩批次ID", required = true)
            @PathVariable("id") Long id,
            @Parameter(description = "学生ID（可选）")
            @RequestParam(value = "studentId", required = false) Long studentId,
            @Parameter(description = "答辩结果（可选）")
            @RequestParam(value = "result", required = false) String result,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        return adminDefenseService.listBatchRecords(id, studentId, result, currentPage, pageSize);
    }

    /**
     * 导出批次内答辩记录（EasyExcel xlsx，按顺序号升序，不分页导出全量）
     */
    @Operation(summary = "导出批次答辩记录", description = "按顺序号升序导出批次内全部答辩记录为 xlsx")
    @GetMapping("/{id}/records/export")
    public void exportBatchRecords(
            @Parameter(description = "答辩批次ID", required = true)
            @PathVariable("id") Long id, HttpServletResponse response) {
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
