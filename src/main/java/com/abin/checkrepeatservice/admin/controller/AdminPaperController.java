package com.abin.checkrepeatservice.admin.controller;

import com.abin.checkrepeatservice.admin.service.AdminPaperService;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.enums.CheckStatusFilterEnum;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

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
public class AdminPaperController {

    @Resource
    private AdminPaperService adminPaperService;

    /**
     * 获取论文列表（分页）
     */
    @GetMapping("/list")
    public Result<Page<PaperInfo>> getPaperList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String paperStatus,
            @RequestParam(required = false) String paperType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String majorName,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String checkStatus,
            @RequestParam(required = false) Double minSimilarity,
            @RequestParam(required = false) Double maxSimilarity) {
        
        log.info("接收获取论文列表请求: page={}, size={}, paperStatus={}, paperType={}, keyword={}, majorName={}, grade={}, minSimilarity={}, maxSimilarity={}, checkStatus={}",
                page, size, paperStatus, paperType, keyword, majorName, grade, minSimilarity, maxSimilarity, checkStatus);
        try {
            // 验证查重状态参数
            if (checkStatus != null && !CheckStatusFilterEnum.isValidCode(checkStatus)) {
                log.warn("无效的查重状态参数，忽略该筛选条件: {}", checkStatus);
                checkStatus = null;
            }
                    
            return adminPaperService.getPaperList(page, size, paperStatus, paperType, keyword, startDate, endDate, majorName, grade, checkStatus, minSimilarity, maxSimilarity);
        } catch (Exception e) {
            log.error("获取论文列表失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文列表失败: " + e.getMessage());
        }
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
    @GetMapping("/{paperId:[0-9]+}")
    public Result<PaperInfo> getPaperDetail(@PathVariable Long paperId) {
        log.info("接收获取论文详情请求: paperId={}", paperId);
        try {
            return adminPaperService.getPaperDetail(paperId);
        } catch (Exception e) {
            log.error("获取论文详情失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文详情失败: " + e.getMessage());
        }
    }

    /**
     * 审核论文
     */
    @PutMapping("/{paperId:[0-9]+}/audit")
    @OperationLog(type = "admin_paper_audit", description = "管理员审核论文", recordResult = true)
    public Result<String> auditPaper(@PathVariable Long paperId,
                                   @RequestBody Map<String, Object> auditRequest) {
        log.info("接收审核论文请求: paperId={}, auditRequest={}", paperId, auditRequest);
        try {
            String auditResult = (String) auditRequest.get("auditResult");
            String auditComment = (String) auditRequest.get("auditComment");
            
            if (auditResult == null || auditResult.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "审核结果不能为空");
            }
            
            return adminPaperService.auditPaper(paperId, auditResult, auditComment);
        } catch (Exception e) {
            log.error("审核论文失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "审核论文失败: " + e.getMessage());
        }
    }

    /**
     * 批量审核论文
     */
    @PostMapping("/batch-audit")
    @OperationLog(type = "admin_paper_batch_audit", description = "管理员批量审核论文", recordResult = true)
    public Result<String> batchAuditPapers(@RequestBody Map<String, Object> batchAuditRequest) {
        log.info("接收批量审核论文请求: batchAuditRequest={}", batchAuditRequest);
        try {
            @SuppressWarnings("unchecked")
            List<Long> paperIds = (List<Long>) batchAuditRequest.get("paperIds");
            String auditResult = (String) batchAuditRequest.get("auditResult");
            String auditComment = (String) batchAuditRequest.get("auditComment");
            
            if (paperIds == null || paperIds.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "论文ID列表不能为空");
            }
            if (auditResult == null || auditResult.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "审核结果不能为空");
            }
            
            return adminPaperService.batchAuditPapers(paperIds, auditResult, auditComment);
        } catch (Exception e) {
            log.error("批量审核论文失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量审核论文失败: " + e.getMessage());
        }
    }

    /**
     * 删除论文
     */
    @DeleteMapping("/{paperId:[0-9]+}")
    @OperationLog(type = "admin_paper_delete", description = "管理员删除论文")
    public Result<String> deletePaper(@PathVariable Long paperId) {
        log.info("接收删除论文请求: paperId={}", paperId);
        try {
            return adminPaperService.deletePaper(paperId);
        } catch (Exception e) {
            log.error("删除论文失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "删除论文失败: " + e.getMessage());
        }
    }

    /**
     * 获取论文统计信息
     */
    @GetMapping({"/statistics", "/stats"})
    public Result<Map<String, Object>> getPaperStatistics() {
        log.info("接收获取论文统计信息请求");
        try {
            return adminPaperService.getPaperStatistics();
        } catch (Exception e) {
            log.error("获取论文统计信息失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 导出论文列表
     */
    @GetMapping("/export")
    public void exportPaperList(@RequestParam Map<String, Object> params) {
        log.info("接收导出论文列表请求: params={}", params);
        try {
            adminPaperService.exportPaperList(params);
        } catch (Exception e) {
            log.error("导出论文列表失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 下载论文文件
     */
    @GetMapping("/{paperId:[0-9]+}/download")
    public Result<String> downloadPaper(@PathVariable Long paperId) {
        log.info("接收下载论文文件请求: paperId={}", paperId);
        try {
            return adminPaperService.downloadPaper(paperId);
        } catch (Exception e) {
            log.error("下载论文文件失败: {}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "下载论文文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 内部查重检测
     */
    @PostMapping("/{paperId:[0-9]+}/internal-check")
    public Result<String> internalCheckPaper(@PathVariable Long paperId) {
        log.info("接收内部查重检测请求：paperId={}", paperId);
        try {
            return adminPaperService.schoolInternalCheckPaper(paperId);
        } catch (Exception e) {
            log.error("校内查重检测失败：{}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "校内查重检测失败：" + e.getMessage());
        }
    }
    
    /**
     * 批量内部查重检测
     */
    @PostMapping("/batch-internal-check")
    public Result<String> batchInternalCheckPaper(@RequestBody List<Long> paperIds) {
        log.info("接收批量内部查重检测请求：paperIds={}", paperIds);
        try {
            return adminPaperService.batchSchoolInternalCheckPaper(paperIds);
        } catch (Exception e) {
            log.error("批量校内查重检测失败：{}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量校内查重检测失败：" + e.getMessage());
        }
    }
    
    /**
     * 批量第三方查重检测
     */
    @PostMapping("/batch-third-party-check")
    public Result<String> batchThirdPartyCheckPaper(@RequestBody List<Long> paperIds) {
        log.info("接收批量第三方查重检测请求：paperIds={}", paperIds);
        try {
            return adminPaperService.batchThirdPartyCheckPaper(paperIds);
        } catch (Exception e) {
            log.error("批量第三方查重检测失败：{}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量第三方查重检测失败：" + e.getMessage());
        }
    }
    
    /**
     * 第三方查重检测（单篇）
     */
    @PostMapping("/{paperId:[0-9]+}/third-party-check")
    public Result<String> thirdPartyCheckPaper(@PathVariable Long paperId) {
        log.info("接收第三方查重检测请求：paperId={}", paperId);
        try {
            List<Long> paperIds = new ArrayList<>();
            paperIds.add(paperId);
            return adminPaperService.batchThirdPartyCheckPaper(paperIds);
        } catch (Exception e) {
            log.error("第三方查重检测失败：{}", e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "第三方查重检测失败：" + e.getMessage());
        }
    }
}