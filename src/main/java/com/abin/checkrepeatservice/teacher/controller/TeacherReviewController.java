package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.teacher.dto.ContactStudentDTO;
import com.abin.checkrepeatservice.teacher.dto.PaperReviewHistoryDTO;
import com.abin.checkrepeatservice.teacher.dto.ReviewOperateReq;
import com.abin.checkrepeatservice.teacher.dto.ReviewQueryReq;
import com.abin.checkrepeatservice.teacher.dto.ReviewResultDTO;
import com.abin.checkrepeatservice.teacher.service.TeacherReviewService;
import com.abin.checkrepeatservice.teacher.vo.PendingStatsVO;
import com.abin.checkrepeatservice.teacher.vo.TodayReviewedVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "教师审核模块", description = "待审核列表/执行审核/审核历史")
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
    @Operation(summary = "查询待审核论文列表", description = "分页查询待审核论文，支持按学生姓名、论文标题模糊搜索")
    public Result<Page<ReviewResultDTO>> getPendingReviewList(
            @Parameter(description = "学生姓名（可选，模糊查询）")
            @RequestParam(value = "studentName", required = false) String studentName,
            @Parameter(description = "论文标题（可选，模糊查询）")
            @RequestParam(value = "paperTitle", required = false) String paperTitle,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @Parameter(description = "每页条数，默认 10")
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
    @Operation(summary = "执行审核", description = "单篇/批量审核，提交格式为 multipart/form-data")
    public Result<Map<String, Object>> doReview(
            @Parameter(description = "论文ID列表，逗号分隔，如 1,2,3", required = true)
            @RequestParam("paperIds") List<Long> paperIds, // 论文ID列表（逗号分隔，如1,2,3）
            @Parameter(description = "审核结果：3-通过，4-不通过", required = true)
            @RequestParam("reviewStatus") Integer reviewStatus, // 3-通过，4-不通过
            @Parameter(description = "审核意见（可选，支持富文本）")
            @RequestParam(value = "reviewOpinion", required = false) String reviewOpinion, // 审核意见
            @Parameter(description = "意见模板ID（可选）")
            @RequestParam(value = "templateId", required = false) Long templateId, // 使用的意见模板ID（可选）
            @Parameter(description = "审核附件（可选）")
            @RequestParam(value = "reviewAttach", required = false) MultipartFile reviewAttach) { // 审核附件
        // 构建审核操作参数
        ReviewOperateReq operateReq = new ReviewOperateReq();
        operateReq.setPaperIds(paperIds);
        operateReq.setReviewStatus(reviewStatus);
        operateReq.setReviewOpinion(reviewOpinion);
        operateReq.setTemplateId(templateId);
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
    @Operation(summary = "查询已审核论文列表", description = "分页查询已审核论文，可按审核结果过滤")
    public Result<Page<ReviewResultDTO>> getReviewedList(
            @Parameter(description = "学生姓名（可选，模糊查询）")
            @RequestParam(value = "studentName", required = false) String studentName,
            @Parameter(description = "论文标题（可选，模糊查询）")
            @RequestParam(value = "paperTitle", required = false) String paperTitle,
            @Parameter(description = "审核结果：3-通过，4-不通过（可选）")
            @RequestParam(value = "reviewStatus", required = false) Integer reviewStatus,
            @Parameter(description = "当前页码，默认 1")
            @RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
            @Parameter(description = "每页条数，默认 10")
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
    @Operation(summary = "查询审核详情", description = "查询单篇论文的审核详情")
    public Result<ReviewResultDTO> getReviewDetail(
            @Parameter(description = "论文ID", required = true) @RequestParam("paperId") Long paperId) {
        return teacherReviewService.getReviewDetail(paperId);
    }

    /**
     * 5. 教师下载审核附件
     * @param attachPath 附件存储路径（必传，通过@RequestParam）
     * @param response HTTP响应
     */
    @GetMapping("/download-attach")
    @Operation(summary = "下载审核附件", description = "按附件存储路径下载审核附件")
    public void downloadReviewAttach(
            @Parameter(description = "附件存储路径", required = true) @RequestParam("attachPath") String attachPath,
            HttpServletResponse response) {
        teacherReviewService.downloadReviewAttach(attachPath, response);
    }

    /**
     * 6. 教师重新发起审核（仅审核不通过的论文）
     * @param paperId 论文ID（必传，通过@RequestParam）
     */
    @PostMapping("/re-initiate")
    @Operation(summary = "重新发起审核", description = "仅审核不通过的论文可重新发起审核")
    public Result<String> reInitiateReview(
            @Parameter(description = "论文ID", required = true) @RequestParam("paperId") Long paperId) {
        return teacherReviewService.reInitiateReview(paperId);
    }

    /**
     * 7. 教师待审核统计（总数 / 平均等待时长 / 今日已审核）
     */
    @GetMapping("/pending-stats")
    @Operation(summary = "待审核统计", description = "返回待审核总数、平均等待时长与今日已审核数")
    public Result<PendingStatsVO> getPendingStats() {
        return teacherReviewService.getPendingStats();
    }

    /**
     * 8. 教师今日审核统计（含 3 小时粒度分布）
     */
    @GetMapping("/today-reviewed")
    @Operation(summary = "今日审核统计", description = "返回今日已审核数量及 3 小时粒度分布")
    public Result<TodayReviewedVO> getTodayReviewedCount() {
        return teacherReviewService.getTodayReviewedCount();
    }

    /**
     * 9. 联系学生（即时消息 / 系统消息 / 邮件）
     */
    @PostMapping("/contact-student")
    @Operation(summary = "联系学生", description = "通过即时消息、系统消息或邮件联系学生")
    public Result<Map<String, Object>> contactStudent(
            @Parameter(description = "联系学生请求体：学生ID、联系方式与消息内容", required = true)
            @RequestBody ContactStudentDTO contactDTO) {
        return teacherReviewService.contactStudent(contactDTO);
    }

    /**
     * 10. 论文审核历史
     */
    @GetMapping("/history/{paperId}")
    @Operation(summary = "论文审核历史", description = "查询指定论文的完整审核流转记录")
    public Result<PaperReviewHistoryDTO> getPaperReviewHistory(
            @Parameter(description = "论文ID", required = true) @PathVariable("paperId") Long paperId) {
        return teacherReviewService.getPaperReviewHistory(paperId);
    }

    /**
     * 11. 盲审专用副本（文档级去标识化后的 PDF，供评阅人直接 iframe 预览）
     */
    @GetMapping("/blind-document")
    @Operation(summary = "获取盲审副本", description = "输出文档级去标识化后的 PDF，供评阅人直接预览")
    public void getBlindDocument(
            @Parameter(description = "论文ID", required = true) @RequestParam("paperId") Long paperId,
            HttpServletResponse response) {
        teacherReviewService.writeBlindDocument(paperId, response);
    }
}
