package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.teacher.dto.*;
import com.abin.checkrepeatservice.teacher.service.PendingReviewService;
import com.abin.checkrepeatservice.teacher.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * 待审核论文页面控制器
 * 提供待审核论文列表、统计、审核等相关接口
 */
@Slf4j
@RestController
@RequestMapping("/api/teacher/pending-reviews")
@PreAuthorize("hasAuthority('TEACHER')")
@Tag(name = "待审核论文接口", description = "待审核论文相关接口")
public class PendingReviewController {

    @Resource
    private PendingReviewService pendingReviewService;

    /**
     * 1. 获取待审核论文列表接口
     * GET /api/teacher/pending-reviews/list
     */
    @GetMapping("/list")
    @Operation(summary = "获取待审核论文列表", description = "获取待审核论文列表，支持多种筛选和排序条件")
    public Result<Object> getPendingReviews(
            @Parameter(description = "教师ID") @RequestParam(required = false) String teacherId,
            @Parameter(description = "优先级筛选") @RequestParam(required = false) String priority,
            @Parameter(description = "学院筛选") @RequestParam(required = false) String college,
            @Parameter(description = "相似度范围") @RequestParam(required = false) String similarityRange,
            @Parameter(description = "排序字段") @RequestParam(required = false) String sortField,
            @Parameter(description = "排序方式") @RequestParam(required = false) String sortOrder,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize) {
        
        try {
            // 如果没有传入teacherId，则从当前用户获取
            if (teacherId == null || teacherId.isEmpty()) {
                teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            }
            
            PendingReviewQueryDTO queryDTO = new PendingReviewQueryDTO();
            queryDTO.setTeacherId(teacherId);
            queryDTO.setPriority(priority);
            queryDTO.setCollege(college);
            queryDTO.setSimilarityRange(similarityRange);
            queryDTO.setSortField(sortField);
            queryDTO.setSortOrder(sortOrder);
            queryDTO.setPage(page);
            queryDTO.setPageSize(pageSize);
            
            log.info("获取待审核论文列表: teacherId={}, priority={}, college={}, page={}, pageSize={}", 
                    teacherId, priority, college, page, pageSize);
                    
            return pendingReviewService.getPendingReviews(queryDTO);
        } catch (Exception e) {
            log.error("获取待审核论文列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取待审核论文列表失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 2. 获取统计信息接口
     * GET /api/teacher/pending-reviews/stats
     */
    @GetMapping("/stats")
    @Operation(summary = "获取待审核统计信息", description = "获取待审核论文的统计信息")
    public Result<PendingStatsVO> getPendingStats(
            @Parameter(description = "教师ID") @RequestParam(required = false) String teacherId) {
        
        try {
            // 如果没有传入teacherId，则从当前用户获取
            if (teacherId == null || teacherId.isEmpty()) {
                teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            }
            
            log.info("获取待审核统计信息: teacherId={}", teacherId);
            return pendingReviewService.getPendingStats(teacherId);
        } catch (Exception e) {
            log.error("获取待审核统计信息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取统计信息失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 3. 论文审核接口
     * POST /api/teacher/pending-reviews/review
     */
    @PostMapping("/review")
    @Operation(summary = "论文审核", description = "对论文进行审核操作")
    @OperationLog(type = "paper_review", description = "论文审核操作", recordResult = true)
    public Result<ReviewResultDetailVO> reviewPaper(@RequestBody PaperReviewDTO reviewDTO) {
        try {
            String teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            log.info("教师{}审核论文: paperIds={}, reviewStatus={}", teacherId, reviewDTO.getPaperIds(), reviewDTO.getReviewStatus());
            return pendingReviewService.reviewPaper(teacherId, reviewDTO);
        } catch (Exception e) {
            log.error("论文审核失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "论文审核失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 4. 重新查重检测接口
     * POST /api/teacher/pending-reviews/recheck
     */
    @PostMapping("/recheck")
    @Operation(summary = "重新查重检测", description = "重新对论文进行查重检测")
    @OperationLog(type = "recheck_plagiarism", description = "重新查重检测", recordResult = true)
    public Result<Map<String, Object>> recheckPlagiarism(@RequestBody Map<String, String> request) {
        try {
            String paperId = request.get("paperId");
            if (paperId == null || paperId.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR, "论文ID不能为空");
            }
            
            String teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            log.info("教师{}重新查重检测论文: paperId={}", teacherId, paperId);
            return pendingReviewService.recheckPlagiarism(teacherId, paperId);
        } catch (Exception e) {
            log.error("重新查重检测失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "重新查重检测失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 5. 发送提醒消息接口
     * POST /api/teacher/pending-reviews/reminder
     */
    @PostMapping("/reminder")
    @Operation(summary = "发送提醒消息", description = "向学生发送提醒消息")
    @OperationLog(type = "send_reminder", description = "发送提醒消息", recordResult = true)
    public Result<Map<String, Object>> sendReminder(@RequestBody SendReminderDTO reminderDTO) {
        try {
            String teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            log.info("教师{}发送提醒消息: studentIds={}, message={}", teacherId, reminderDTO.getStudentIds(), reminderDTO.getMessage());
            return pendingReviewService.sendReminder(teacherId, reminderDTO);
        } catch (Exception e) {
            log.error("发送提醒消息失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "发送提醒消息失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 6. 联系学生接口
     * POST /api/teacher/pending-reviews/contact
     */
    @PostMapping("/contact")
    @Operation(summary = "联系学生", description = "与学生进行沟通联系")
    @OperationLog(type = "contact_student", description = "联系学生", recordResult = true)
    public Result<Map<String, Object>> contactStudent(@RequestBody ContactStudentDTO contactDTO) {
        try {
            String teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            log.info("教师{}联系学生: studentId={}, paperId={}, messageType={}", 
                    teacherId, contactDTO.getStudentId(), contactDTO.getPaperId(), contactDTO.getMessageType());
            return pendingReviewService.contactStudent(teacherId, contactDTO);
        } catch (Exception e) {
            log.error("联系学生失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "联系学生失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 7. 下载论文文件接口
     * GET /api/teacher/pending-reviews/download/{paperId}
     */
    @GetMapping("/download/{paperId}")
    @Operation(summary = "下载论文文件", description = "下载指定论文的文件")
    public void downloadPaper(
            @Parameter(description = "论文ID") @PathVariable String paperId,
            HttpServletResponse response) {
        try {
            String teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            log.info("教师{}下载论文文件: paperId={}", teacherId, paperId);
            pendingReviewService.downloadPaper(teacherId, paperId, response);
        } catch (Exception e) {
            log.error("下载论文文件失败: paperId={}", paperId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 8. 获取查重报告接口
     * GET /api/teacher/pending-reviews/report/{paperId}
     */
    @GetMapping("/report/{paperId}")
    @Operation(summary = "获取查重报告", description = "获取论文的查重报告")
    public Result<PlagiarismReportVO> getPlagiarismReport(
            @Parameter(description = "论文ID") @PathVariable String paperId) {
        try {
            String teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            log.info("教师{}获取查重报告: paperId={}", teacherId, paperId);
            return pendingReviewService.getPlagiarismReport(teacherId, paperId);
        } catch (Exception e) {
            log.error("获取查重报告失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取查重报告失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 9. 获取今日审核统计接口
     * GET /api/teacher/pending-reviews/today
     */
    @GetMapping("/today")
    @Operation(summary = "获取今日审核统计", description = "获取教师今日的审核统计信息")
    public Result<TodayReviewedVO> getTodayReviewedCount(
            @Parameter(description = "教师ID") @RequestParam(required = false) String teacherId) {
        try {
            // 如果没有传入teacherId，则从当前用户获取
            if (teacherId == null || teacherId.isEmpty()) {
                teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            }
            
            log.info("获取今日审核统计: teacherId={}", teacherId);
            return pendingReviewService.getTodayReviewedCount(teacherId);
        } catch (Exception e) {
            log.error("获取今日审核统计失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取今日审核统计失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 10. 委托审核接口
     * POST /api/teacher/pending-reviews/delegate
     */
    @PostMapping("/delegate")
    @Operation(summary = "委托审核", description = "将论文审核委托给其他教师")
    @OperationLog(type = "delegate_review", description = "委托审核", recordResult = true)
    public Result<Map<String, Object>> delegateReview(@RequestBody DelegateReviewDTO delegateDTO) {
        try {
            String teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            log.info("教师{}委托审核: paperId={}, delegateTeacherId={}", 
                    teacherId, delegateDTO.getPaperId(), delegateDTO.getDelegateTeacherId());
            return pendingReviewService.delegateReview(teacherId, delegateDTO);
        } catch (Exception e) {
            log.error("委托审核失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "委托审核失败: " + "系统异常，请稍后重试");
        }
    }

    /**
     * 14. 获取教师审核历史统计接口
     * GET /api/teacher/pending-reviews/teacher-statistics
     */
    @GetMapping("/teacher-statistics")
    @Operation(summary = "获取教师审核历史统计", description = "获取教师的审核历史统计数据")
    public Result<TeacherReviewStatisticsDTO> getTeacherReviewStatistics(
            @Parameter(description = "开始日期") @RequestParam(required = false) String startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) String endDate,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            String teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            log.info("教师{}获取审核历史统计: startDate={}, endDate={}, page={}, pageSize={}", 
                    teacherId, startDate, endDate, page, pageSize);
            
            return pendingReviewService.getTeacherReviewStatistics(teacherId, startDate, endDate, page, pageSize);
        } catch (Exception e) {
            log.error("获取教师审核历史统计失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取教师审核历史统计失败: " + "系统异常，请稍后重试");
        }
    }
    @GetMapping("/history/{paperId}")
    @Operation(summary = "获取论文审核历史", description = "获取指定论文的审核历史记录")
    public Result<PaperReviewHistoryDTO> getPaperReviewHistory(
            @Parameter(description = "论文ID") @PathVariable String paperId) {
        try {
            String teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            log.info("教师{}获取论文审核历史: paperId={}", teacherId, paperId);
            return pendingReviewService.getPaperReviewHistory(teacherId, paperId);
        } catch (Exception e) {
            log.error("获取论文审核历史失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文审核历史失败: " + "系统异常，请稍后重试");
        }
    }
    @GetMapping({"/preview-url/{paperId}", "/preview/{paperId}"})
    @Operation(summary = "获取论文预览URL", description = "获取论文的在线预览URL地址")
    public Result<PaperPreviewUrlDTO> getPaperPreviewUrl(
            @Parameter(description = "论文ID") @PathVariable String paperId) {
        try {
            String teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            log.info("教师{}获取论文预览URL: paperId={}", teacherId, paperId);
            return pendingReviewService.getPaperPreviewUrl(teacherId, paperId);
        } catch (Exception e) {
            log.error("获取论文预览URL失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文预览URL失败: " + "系统异常，请稍后重试");
        }
    }
    @GetMapping("/content/{paperId}")
    @Operation(summary = "获取论文原文内容", description = "获取论文的完整内容信息")
    public Result<PaperContentDTO> getPaperContent(
            @Parameter(description = "论文ID") @PathVariable String paperId) {
        try {
            String teacherId = UserBusinessInfoUtils.getCurrentUserId().toString();
            log.info("教师{}获取论文内容: paperId={}", teacherId, paperId);
            return pendingReviewService.getPaperContent(teacherId, paperId);
        } catch (Exception e) {
            log.error("获取论文内容失败: paperId={}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取论文内容失败: " + "系统异常，请稍后重试");
        }
    }
}