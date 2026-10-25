package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminPaperService;
import com.abin.checkrepeatservice.admin.dto.OverdueRemindReq;
import com.abin.checkrepeatservice.admin.vo.OverduePaperPageVO;
import com.abin.checkrepeatservice.admin.vo.OverdueRemindResultVO;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.enums.CheckStatusFilterEnum;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理员论文管理控制器
 * 职责：处理管理员对论文的查询、审核、管理等操作
 */
@RestController
@RequestMapping("/api/admin/papers")
@PreAuthorize("hasAuthority('ADMIN')")
@Slf4j
@Tag(name = "论文管理", description = "论文查询/代审/查重检测/导出")
public class AdminPaperController {

    @Resource
    private AdminPaperService adminPaperService;

    /**
     * 获取论文列表（分页）
     */
    @Operation(summary = "获取论文列表", description = "分页查询论文，支持状态、类型、关键字、专业、年级与相似度等筛选")
    @GetMapping("/list")
    public Result<Page<PaperInfo>> getPaperList(
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "论文状态（可选）")
            @RequestParam(required = false) String paperStatus,
            @Parameter(description = "论文类型（可选）")
            @RequestParam(required = false) String paperType,
            @Parameter(description = "搜索关键字（可选）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "开始日期（可选）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期（可选）")
            @RequestParam(required = false) String endDate,
            @Parameter(description = "专业名称（可选）")
            @RequestParam(required = false) String majorName,
            @Parameter(description = "年级（可选）")
            @RequestParam(required = false) String grade,
            @Parameter(description = "查重状态（可选）")
            @RequestParam(required = false) String checkStatus,
            @Parameter(description = "最小相似度（可选）")
            @RequestParam(required = false) Double minSimilarity,
            @Parameter(description = "最大相似度（可选）")
            @RequestParam(required = false) Double maxSimilarity) {
        
        log.info("接收获取论文列表请求: page={}, size={}, paperStatus={}, paperType={}, keyword={}, majorName={}, grade={}, minSimilarity={}, maxSimilarity={}, checkStatus={}",
                page, size, paperStatus, paperType, keyword, majorName, grade, minSimilarity, maxSimilarity, checkStatus);
        // 验证查重状态参数
        if (checkStatus != null && !CheckStatusFilterEnum.isValidCode(checkStatus)) {
            log.warn("无效的查重状态参数，忽略该筛选条件: {}", checkStatus);
            checkStatus = null;
        }
                
        return adminPaperService.getPaperList(page, size, paperStatus, paperType, keyword, startDate, endDate, majorName, grade, checkStatus, minSimilarity, maxSimilarity);
    }
    
    /**
     * 验证相似度范围参数是否有效
     */
    private boolean isValidSimilarityRange(String range) {
        if (range == null || range.isEmpty()) {
            return false;
        }
        
        // 支持的范围值
        String[] validRanges = {"<20%", "lt20", "20%-50%", "20to50", ">50%", "gt50"};
        
        for (String validRange : validRanges) {
            if (validRange.equalsIgnoreCase(range)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 获取论文详情
     */
    @Operation(summary = "获取论文详情", description = "查询指定论文的详细信息")
    @GetMapping("/{paperId:[0-9]+}")
    public Result<PaperInfo> getPaperDetail(
            @Parameter(description = "论文ID", required = true)
            @PathVariable Long paperId) {
        log.info("接收获取论文详情请求: paperId={}", paperId);
        return adminPaperService.getPaperDetail(paperId);
    }

    /** 代审原因最大长度（与 review_record.proxy_reason 列宽一致） */
    private static final int PROXY_REASON_MAX_LENGTH = 200;

    /**
     * 审核论文（管理员代审）
     *
     * <p>auditSource 不从请求体读取，由服务端按登录主体角色落库（管理端入口一律 ADMIN_PROXY）；
     * proxyReason 为服务端必填校验，空白/超长直接 400。</p>
     */
    @Operation(summary = "审核论文（管理员代审）", description = "代审来源由服务端按登录主体落库，proxyReason 为服务端必填校验")
    @PutMapping("/{paperId:[0-9]+}/audit")
    @OperationLog(type = "admin_paper_proxy_audit", description = "论文审核-管理员代审", recordResult = true)
    public Result<Map<String, Object>> auditPaper(
            @Parameter(description = "论文ID", required = true)
            @PathVariable Long paperId,
            @Parameter(description = "审核请求体：auditResult 审核结果、auditComment 审核意见、proxyReason 代审原因", required = true)
            @RequestBody Map<String, Object> auditRequest) {
        log.info("接收审核论文请求: paperId={}, auditRequest={}", paperId, auditRequest);
        String auditResult = (String) auditRequest.get("auditResult");
        String auditComment = (String) auditRequest.get("auditComment");
        String proxyReason = (String) auditRequest.get("proxyReason");
        
        if (auditResult == null || auditResult.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "审核结果不能为空");
        }
        String proxyReasonError = validateProxyReason(proxyReason);
        if (proxyReasonError != null) {
            return Result.error(ResultCode.PARAM_ERROR, proxyReasonError);
        }
        
        return adminPaperService.auditPaper(paperId, auditResult, auditComment, proxyReason);
    }

    /**
     * 批量审核论文（管理员代审）
     */
    @Operation(summary = "批量审核论文（管理员代审）", description = "批量提交论文审核结果与代审原因")
    @PostMapping("/batch-audit")
    @OperationLog(type = "admin_paper_proxy_batch_audit", description = "论文审核-管理员批量代审", recordResult = true)
    public Result<Map<String, Object>> batchAuditPapers(
            @Parameter(description = "批量审核请求体：paperIds 论文ID列表、auditResult 审核结果、auditComment 审核意见、proxyReason 代审原因", required = true)
            @RequestBody Map<String, Object> batchAuditRequest) {
        log.info("接收批量审核论文请求: batchAuditRequest={}", batchAuditRequest);
        @SuppressWarnings("unchecked")
        List<Long> paperIds = (List<Long>) batchAuditRequest.get("paperIds");
        String auditResult = (String) batchAuditRequest.get("auditResult");
        String auditComment = (String) batchAuditRequest.get("auditComment");
        String proxyReason = (String) batchAuditRequest.get("proxyReason");
        
        if (paperIds == null || paperIds.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "论文ID列表不能为空");
        }
        if (auditResult == null || auditResult.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "审核结果不能为空");
        }
        String proxyReasonError = validateProxyReason(proxyReason);
        if (proxyReasonError != null) {
            return Result.error(ResultCode.PARAM_ERROR, proxyReasonError);
        }
        
        return adminPaperService.batchAuditPapers(paperIds, auditResult, auditComment, proxyReason);
    }

    /** 代审原因服务端强校验；返回 null 表示通过 */
    private String validateProxyReason(String proxyReason) {
        if (proxyReason == null || proxyReason.trim().isEmpty()) {
            return "代审原因不能为空";
        }
        if (proxyReason.trim().length() > PROXY_REASON_MAX_LENGTH) {
            return "代审原因长度不能超过" + PROXY_REASON_MAX_LENGTH + "字符";
        }
        return null;
    }

    /**
     * 删除论文
     */
    @Operation(summary = "删除论文", description = "删除指定论文")
    @DeleteMapping("/{paperId:[0-9]+}")
    @OperationLog(type = "admin_paper_delete", description = "管理员删除论文")
    public Result<String> deletePaper(
            @Parameter(description = "论文ID", required = true)
            @PathVariable Long paperId) {
        log.info("接收删除论文请求: paperId={}", paperId);
        return adminPaperService.deletePaper(paperId);
    }

    /**
     * 获取论文统计信息
     */
    @Operation(summary = "获取论文统计信息", description = "统计论文数量与状态分布")
    @GetMapping({"/statistics", "/stats"})
    public Result<Map<String, Object>> getPaperStatistics() {
        log.info("接收获取论文统计信息请求");
        return adminPaperService.getPaperStatistics();
    }

    /**
     * 导出论文列表（S12：EasyExcel 流式写出 xlsx）
     */
    @Operation(summary = "导出论文列表", description = "按筛选参数流式导出论文列表为 xlsx")
    @GetMapping("/export")
    public void exportPaperList(
            @Parameter(description = "导出筛选参数（与列表接口同口径）")
            @RequestParam Map<String, Object> params, HttpServletResponse response) {
        log.info("接收导出论文列表请求: params={}", params);
        adminPaperService.exportPaperList(params, response);
    }

    /**
     * Wave 3：超期论文列表（bucket: all/1/3/7）
     */
    @Operation(summary = "查询超期论文列表", description = "按超期区间 bucket（all/1/3/7）分页查询超期论文")
    @GetMapping("/overdue")
    public Result<OverduePaperPageVO> getOverduePapers(
            @Parameter(description = "超期区间：all/1/3/7（可选）")
            @RequestParam(required = false) String bucket,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数，默认 10")
            @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "搜索关键字（可选）")
            @RequestParam(required = false) String keyword) {
        log.info("接收超期论文查询请求: bucket={}, page={}, size={}, keyword={}", bucket, page, size, keyword);
        return adminPaperService.getOverduePapers(bucket, page, size, keyword);
    }

    /**
     * Wave 3：超期论文一键催办（逐篇结果 + 留痕）
     */
    @Operation(summary = "催办超期论文", description = "逐篇执行催办并返回结果，操作留痕")
    @PostMapping("/overdue/remind")
    @OperationLog(type = "admin_overdue_remind", description = "管理员催办超期论文", recordResult = true)
    public Result<OverdueRemindResultVO> remindOverduePapers(
            @Parameter(description = "超期催办请求体：paperIds 论文ID列表、channel 通知渠道", required = true)
            @RequestBody OverdueRemindReq req) {
        log.info("接收超期论文催办请求: paperIds={}, channel={}", req.getPaperIds(), req.getChannel());
        return adminPaperService.remindOverduePapers(req);
    }

    /**
     * Wave 3：论文生命周期漏斗
     */
    @Operation(summary = "查询论文生命周期漏斗", description = "按学期统计论文各生命周期阶段数量")
    @GetMapping("/lifecycle-funnel")
    public Result<Map<String, Object>> getLifecycleFunnel(
            @Parameter(description = "学期（可选）")
            @RequestParam(required = false) String semester) {
        log.info("接收生命周期漏斗查询请求: semester={}", semester);
        return adminPaperService.getLifecycleFunnel(semester);
    }
    
    /**
     * 下载论文文件
     */
    @Operation(summary = "下载论文文件", description = "下载指定论文的原始文件")
    @GetMapping("/{paperId:[0-9]+}/download")
    public void downloadPaper(
            @Parameter(description = "论文ID", required = true)
            @PathVariable Long paperId, HttpServletResponse response) {
        log.info("接收下载论文文件请求: paperId={}", paperId);
        adminPaperService.downloadPaper(paperId, response);
    }

    /**
     * 获取论文预览地址（管理员）
     */
    @Operation(summary = "获取论文预览地址", description = "获取指定论文的预览访问地址")
    @GetMapping("/{paperId:[0-9]+}/preview-url")
    public Result<Map<String, String>> getPaperPreviewUrl(
            @Parameter(description = "论文ID", required = true)
            @PathVariable Long paperId) {
        log.info("接收管理员论文预览请求: paperId={}", paperId);
        return adminPaperService.getPaperPreviewUrl(paperId);
    }
    
    /**
     * 内部查重检测
     */
    @Operation(summary = "内部查重检测", description = "对指定论文执行校内查重检测")
    @PostMapping("/{paperId:[0-9]+}/internal-check")
    public Result<String> internalCheckPaper(
            @Parameter(description = "论文ID", required = true)
            @PathVariable Long paperId) {
        log.info("接收内部查重检测请求：paperId={}", paperId);
        return adminPaperService.schoolInternalCheckPaper(paperId);
    }
    
    /**
     * 批量内部查重检测
     */
    @Operation(summary = "批量内部查重检测", description = "对多篇论文批量执行校内查重检测")
    @PostMapping("/batch-internal-check")
    public Result<String> batchInternalCheckPaper(
            @Parameter(description = "论文ID列表", required = true)
            @RequestBody List<Long> paperIds) {
        log.info("接收批量内部查重检测请求：paperIds={}", paperIds);
        return adminPaperService.batchSchoolInternalCheckPaper(paperIds);
    }
    
    /**
     * 批量第三方查重检测
     */
    @Operation(summary = "批量第三方查重检测", description = "对多篇论文批量发起第三方查重检测")
    @PostMapping("/batch-third-party-check")
    public Result<String> batchThirdPartyCheckPaper(
            @Parameter(description = "论文ID列表", required = true)
            @RequestBody List<Long> paperIds) {
        log.info("接收批量第三方查重检测请求：paperIds={}", paperIds);
        return adminPaperService.batchThirdPartyCheckPaper(paperIds);
    }
    
    /**
     * 第三方查重检测（单篇）
     */
    @Operation(summary = "第三方查重检测（单篇）", description = "对单篇论文发起第三方查重检测")
    @PostMapping("/{paperId:[0-9]+}/third-party-check")
    public Result<String> thirdPartyCheckPaper(
            @Parameter(description = "论文ID", required = true)
            @PathVariable Long paperId) {
        log.info("接收第三方查重检测请求：paperId={}", paperId);
        List<Long> paperIds = new ArrayList<>();
        paperIds.add(paperId);
        return adminPaperService.batchThirdPartyCheckPaper(paperIds);
    }
}