package com.abin.checkrepeatservice.student.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.vo.PaperReSubmitReq;
import com.abin.checkrepeatservice.student.dto.StudentReviewDetailDTO;
import com.abin.checkrepeatservice.student.vo.StudentReviewQueryReq;
import com.abin.checkrepeatservice.student.service.StudentReviewService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 学生端审核结果控制器：仅学生角色可访问，统一用@RequestParam传参
 */
@RestController
@RequestMapping("/api/student/reviews")
@PreAuthorize("hasAuthority('STUDENT')") // 权限控制：仅学生可访问
@Tag(name = "学生论文审核模块", description = "学生查询审核结果、下载附件与重新提交论文接口")
public class StudentReviewController {

    @Resource
    private StudentReviewService studentReviewService;

    /**
     * 1. 学生查询自己的论文审核结果列表（分页）
     * @param paperStatus 论文状态（可选：2-待审核，3-通过，4-不通过）
     * @param currentPage 当前页码（可选，默认1）
     * @param pageSize 每页条数（可选，默认10）
     */
    @GetMapping("/list")
    @Operation(summary = "查询我的论文审核结果列表", description = "分页查询本人论文审核结果")
    public Result<Page<StudentReviewDetailDTO>> getMyReviewList(
            @Parameter(description = "论文状态（可选：2-待审核，3-通过，4-不通过）") @RequestParam(value = "paperStatus", required = false) Integer paperStatus,
            @Parameter(description = "当前页码") @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @Parameter(description = "每页条数") @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        StudentReviewQueryReq queryReq = new StudentReviewQueryReq();
        queryReq.setPaperStatus(paperStatus);
        queryReq.setCurrentPage(currentPage);
        queryReq.setPageSize(pageSize);
        return studentReviewService.getMyReviewList(queryReq);
    }

    /**
     * 2. 学生查询单篇论文的审核详情
     * @param paperId 论文ID（必传，通过@RequestParam）
     */
    @GetMapping("/detail")
    @Operation(summary = "查询论文审核详情")
    public Result<StudentReviewDetailDTO> getReviewDetail(
            @Parameter(description = "论文ID", required = true) @RequestParam("paperId") Long paperId) {
        return studentReviewService.getReviewDetail(paperId);
    }

    /**
     * 3. 学生下载审核附件
     * @param attachPath 附件存储路径（必传，通过@RequestParam）
     * @param response HTTP响应
     */
    @GetMapping("/download-attach")
    @Operation(summary = "下载审核附件")
    public void downloadReviewAttach(
            @Parameter(description = "附件存储路径", required = true) @RequestParam("attachPath") String attachPath,
            HttpServletResponse response) {
        studentReviewService.downloadReviewAttach(attachPath, response);
    }

    /**
     * 4. 学生重新提交修改后的论文（审核不通过后）
     * （注：文件上传需用multipart/form-data格式）
     */
    @PostMapping(value = "/re-submit", consumes = "multipart/form-data")
    @Operation(summary = "重新提交修改后的论文", description = "审核不通过后重新提交，文件上传使用 multipart/form-data 格式")
    public Result<Map<String, Object>> reSubmitPaper(
            @Parameter(description = "原论文ID", required = true) @RequestParam("originalPaperId") Long originalPaperId, // 原论文ID
            @Parameter(description = "修改后的文件", required = true) @RequestParam("revisedFile") MultipartFile revisedFile, // 修改后的文件
            @Parameter(description = "修改说明") @RequestParam(value = "revisionDesc", required = false) String revisionDesc) { // 修改说明
        PaperReSubmitReq reSubmitReq = new PaperReSubmitReq();
        reSubmitReq.setOriginalPaperId(originalPaperId);
        reSubmitReq.setRevisedFile(revisedFile);
        reSubmitReq.setRevisionDesc(revisionDesc);
        return studentReviewService.reSubmitPaper(reSubmitReq);
    }

    /**
     * 5. 学生查询重新提交记录
     * @param originalPaperId 原论文ID（必传）
     * @param currentPage 当前页码（必传，默认1）
     * @param pageSize 每页条数（必传，默认10）
     */
    @GetMapping("/resubmit-record")
    @Operation(summary = "查询重新提交记录", description = "分页查询指定原论文的重新提交记录")
    public Result<Page<PaperInfo>> getResubmitRecord(
            @Parameter(description = "原论文ID", required = true) @RequestParam("originalPaperId") Long originalPaperId,
            @Parameter(description = "当前页码") @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
            @Parameter(description = "每页条数") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return studentReviewService.getResubmitRecord(originalPaperId, currentPage, pageSize);
    }
}
