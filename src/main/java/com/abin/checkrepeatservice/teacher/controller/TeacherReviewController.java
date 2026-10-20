package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.ReviewOperateReq;
import com.abin.checkrepeatservice.teacher.dto.ReviewQueryReq;
import com.abin.checkrepeatservice.teacher.dto.ReviewResultDTO;
import com.abin.checkrepeatservice.teacher.service.TeacherReviewService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 教师审核控制器：仅教师角色可访问，统一用@RequestParam传参
 */
@RestController
@RequestMapping("/api/teacher/reviews")
@PreAuthorize("hasAuthority('TEACHER')") // 权限控制：仅教师可访问
public class TeacherReviewController {

    @Resource
    private TeacherReviewService teacherReviewService;

    /**
     * 1. 教师查询待审核论文列表（分页）
     * @param studentName 学生姓名（可选，模糊查询）
     * @param paperTitle 论文标题（可选，模糊查询）
     * @param currentPage 当前页码（可选，默认1）
     * @param pageSize 每页条数（可选，默认10）
     */
    @GetMapping("/pending-list")
    public Result<Page<ReviewResultDTO>> getPendingReviewList(
            @RequestParam(value = "studentName", required = false) String studentName,
            @RequestParam(value = "paperTitle", required = false) String paperTitle,
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        // 构建查询参数
        ReviewQueryReq queryReq = new ReviewQueryReq();
        queryReq.setStudentName(studentName);
        queryReq.setPaperTitle(paperTitle);
        queryReq.setCurrentPage(currentPage);
        queryReq.setPageSize(pageSize);
        return teacherReviewService.getPendingReviewList(queryReq);
    }

    /**
     * 2. 教师执行单篇/批量审核
     * （注：文件上传需用multipart/form-data格式，参数通过@RequestParam接收）
     */
    @PostMapping(value = "/do-review", consumes = "multipart/form-data")
    public Result<Map<String, Object>> doReview(
            @RequestParam("paperIds") List<Long> paperIds, // 论文ID列表（逗号分隔，如1,2,3）
            @RequestParam("reviewStatus") Integer reviewStatus, // 3-通过，4-不通过
            @RequestParam(value = "reviewOpinion", required = false) String reviewOpinion, // 审核意见
            @RequestParam(value = "reviewAttach", required = false) MultipartFile reviewAttach) { // 审核附件
        // 构建审核操作参数
        ReviewOperateReq operateReq = new ReviewOperateReq();
        operateReq.setPaperIds(paperIds);
        operateReq.setReviewStatus(reviewStatus);
        operateReq.setReviewOpinion(reviewOpinion);
        operateReq.setReviewAttach(reviewAttach);
        return teacherReviewService.doReview(operateReq);
    }

    /**
     * 3. 教师查询已审核论文列表（分页）
     * @param studentName 学生姓名（可选）
     * @param paperTitle 论文标题（可选）
     * @param reviewStatus 审核结果（可选，3-审核通过，4-审核不通过）
     * @param currentPage 当前页码（可选）
     * @param pageSize 每页条数（可选）
     */
    @GetMapping("/reviewed-list")
    public Result<Page<ReviewResultDTO>> getReviewedList(
            @RequestParam(value = "studentName", required = false) String studentName,
            @RequestParam(value = "paperTitle", required = false) String paperTitle,
            @RequestParam(value = "reviewStatus", required = false) Integer reviewStatus,
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        ReviewQueryReq queryReq = new ReviewQueryReq();
        queryReq.setStudentName(studentName);
        queryReq.setPaperTitle(paperTitle);
        queryReq.setReviewStatus(reviewStatus);
        queryReq.setCurrentPage(currentPage);
        queryReq.setPageSize(pageSize);
        return teacherReviewService.getReviewedList(queryReq);
    }

    /**
     * 4. 教师查询单篇论文的审核详情
     * @param paperId 论文ID（必传，通过@RequestParam）
     */
    @GetMapping("/detail")
    public Result<ReviewResultDTO> getReviewDetail(
            @RequestParam("paperId") Long paperId) {
        return teacherReviewService.getReviewDetail(paperId);
    }

    /**
     * 5. 教师下载审核附件
     * @param attachPath 附件存储路径（必传，通过@RequestParam）
     * @param response HTTP响应
     */
    @GetMapping("/download-attach")
    public void downloadReviewAttach(
            @RequestParam("attachPath") String attachPath,
            HttpServletResponse response) {
        teacherReviewService.downloadReviewAttach(attachPath, response);
    }

    /**
     * 6. 教师重新发起审核（仅审核不通过的论文）
     * @param paperId 论文ID（必传，通过@RequestParam）
     */
    @PostMapping("/re-initiate")
    public Result<String> reInitiateReview(
            @RequestParam("paperId") Long paperId) {
        return teacherReviewService.reInitiateReview(paperId);
    }
}
