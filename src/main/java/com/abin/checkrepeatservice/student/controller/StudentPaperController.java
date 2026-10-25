package com.abin.checkrepeatservice.student.controller;


import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.ReviewAssignment;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.PaperInfoService;
import com.abin.checkrepeatservice.student.service.StudentReviewService;
import com.abin.checkrepeatservice.teacher.mapper.ReviewAssignmentMapper;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.monitor.service.BusinessMetricsService;
import com.abin.checkrepeatservice.student.vo.PaperQueryRequest;
import com.abin.checkrepeatservice.student.vo.PaperSubmitRequest;
import com.abin.checkrepeatservice.student.vo.PaperReSubmitReq;
import com.abin.checkrepeatservice.student.dto.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生论文控制器：处理论文上传、查询、删除等学生端接口
 */
@Slf4j
@RestController
@RequestMapping("/api/papers")
public class StudentPaperController {

    @Resource
    private PaperInfoService paperInfoService;

    @Resource
    private StudentReviewService studentReviewService;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private FileService fileService;

    @Resource
    private ReviewAssignmentMapper reviewAssignmentMapper;

    @Resource
    private BusinessMetricsService businessMetricsService;

    /**
     * 1. 论文提交接口（推荐方式）- 只接收文件ID，不处理文件上传
     *
     * @param request 论文提交请求（包含文件ID）
     * @return 论文提交结果
     */
    @PostMapping("/submit")
    @OperationLog(type = "student_paper_submit", description = "学生提交论文", recordResult = true)
    public Result<PaperInfo> submitPaper(@RequestBody @Valid PaperSubmitRequest request) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("论文提交请求 - 学生ID: {}, 请求参数: {}", studentId, request);

        // 参数校验
        if (!validatePaperRequest(request)) {
            return Result.error(ResultCode.PARAM_ERROR, "论文信息不完整");
        }

        // 验证文件是否存在
        FileInfo fileInfo = fileService.getById(request.getFileId());
        if (fileInfo == null) {
            return Result.error(ResultCode.PARAM_ERROR, "文件不存在或已被删除");
        }

        // 安全修复（P0）：只允许使用自己上传的文件提交论文。
        // 否则可用可枚举的 fileId 把他人的论文挂到自己名下，再借 deletePaper
        // 顺手删掉对方的文件记录（删文件接口的归属校验也就被绕过了）。
        if (fileInfo.getUploadUserId() == null
                || !fileInfo.getUploadUserId().equals(studentId.toString())) {
            log.warn("论文提交越权使用文件拦截 - studentId: {}, fileId: {}",
                    studentId, request.getFileId());
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权使用该文件提交论文");
        }

        // 验证MD5（如果提供了MD5）
        if (StringUtils.hasText(request.getFileMd5()) &&
                StringUtils.hasText(fileInfo.getMd5()) &&
                !request.getFileMd5().equals(fileInfo.getMd5())) {
            return Result.error(ResultCode.PARAM_ERROR, "文件校验失败，MD5不匹配");
        }

        // 调用文件ID提交逻辑
        PaperInfo paperInfo = paperInfoService.submitPaperByFileId(
                request.getSubjectCode(),
                request.getPaperTitle(),
                request.getPaperAbstract(),
                request.getCollegeId(),
                request.getMajorId(),
                request.getPaperType(),
                request.getDegreeType(),
                request.getFileId(),
                request.getFileMd5(),
                studentId
        );
        // 埋点刻意放在 controller 层而非 service 内：
        // submitPaperByFileId 里的 asyncAllocateTeacherAndCheck 是自调用，
        // @Async 代理被绕过，实际同步跑在提交事务内，service 层无法区分是否真的提交成功。
        businessMetricsService.record(BusinessMetricsService.Event.PAPER_SUBMIT,
                BusinessMetricsService.Outcome.SUCCESS);
        return Result.success("论文提交成功", paperInfo);
    }
    /**
     * 验证论文请求参数
     */
    private boolean validatePaperRequest(PaperSubmitRequest request) {
        return StringUtils.hasText(request.getPaperTitle()) &&
                request.getCollegeId() != null &&
                request.getMajorId() != null &&
                StringUtils.hasText(request.getPaperType()) &&
                StringUtils.hasText(request.getPaperAbstract()) &&
                request.getFileId() != null; // 必须提供文件 ID
    }

    /**
     * 分页查询学生论文列表
     */
    @PostMapping("/page")
    public Result<Page<PaperInfo>> getStudentPaperPage(@RequestBody PaperQueryRequest request) {
        Page<PaperInfo> result = paperInfoService.getStudentPaperPage(request);
        return Result.success(result);
    }

    /**
     * 3. 学生查询论文详情接口
     *
     * @param paperId 论文ID
     * @return 论文详情
     */
    @GetMapping("/detail")
    public Result<PaperInfo> getPaperDetail(@RequestParam Long paperId) {
        PaperInfo paperInfo = paperInfoService.getById(paperId);
        if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在或已删除");
        }
        // ：论文详情按 ID 直达，必须做归属校验，否则任意登录用户
        // 传一个 paperId 就能读到他人论文的摘要、查重结果、导师等信息。
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        PaperAccess access = resolvePaperAccess(paperInfo, currentUserId);
        if (!access.allowed()) {
            log.warn("论文详情越权访问拦截 - paperId: {}, userId: {}", paperId, currentUserId);
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权查看该论文", null);
        }
        // 2. 重提闭环字段由服务端计算（canResubmit / nextAction），前端不再猜状态
        paperInfoService.enrichResubmitInfo(paperInfo);
        // 3. 盲审评阅人只能拿到匿名视图（脱敏在 enrich 之后做，避免影响内部状态计算）
        if (access.maskIdentity()) {
            log.info("盲审论文详情已脱敏 - paperId: {}, reviewerId: {}", paperId, currentUserId);
            maskBlindIdentity(paperInfo);
        }
        return Result.success("论文详情查询成功", paperInfo);
    }

    /** 论文详情访问决策：是否放行、是否需要盲审脱敏 */
    private record PaperAccess(boolean allowed, boolean maskIdentity) {
    }

    /**
     * 论文详情访问策略：学生本人 / 指导教师 / 被分配的评阅教师 / 管理员。
     *
     * <p>评阅教师单独判定：评阅任务分配后 {@code paper.teacher_id} 仍是原导师，
     * 只看导师字段会把合法的评阅人挡在门外。已超时转派（status=3）的任务不再授权，
     * 避免改派后原评阅人仍能持续查看。</p>
     *
     * <p>盲审脱敏：仅通过“盲审评阅任务”获得访问权的教师才脱敏；若该教师同时还有
     * 非盲审（导师评阅/交叉评阅）任务，说明其本就有实名查看权，不脱敏。</p>
     */
    private PaperAccess resolvePaperAccess(PaperInfo paper, Long currentUserId) {
        if (currentUserId == null) {
            return new PaperAccess(false, false);
        }
        if (UserBusinessInfoUtils.isAdmin()) {
            return new PaperAccess(true, false);
        }
        if (Objects.equals(paper.getStudentId(), currentUserId)) {
            return new PaperAccess(true, false);
        }
        if (!UserBusinessInfoUtils.isTeacher()) {
            return new PaperAccess(false, false);
        }
        // 指导教师（实名可见）
        if (Objects.equals(paper.getTeacherId(), currentUserId)) {
            return new PaperAccess(true, false);
        }
        // 被分配的评阅教师（待评阅 / 评阅中 / 已提交均保留查看权，超时转派的不保留）
        List<ReviewAssignment> activeAssignments = reviewAssignmentMapper.selectList(
                new LambdaQueryWrapper<ReviewAssignment>()
                        .eq(ReviewAssignment::getPaperId, paper.getId())
                        .eq(ReviewAssignment::getReviewerId, currentUserId)
                        .ne(ReviewAssignment::getStatus, ReviewAssignment.STATUS_TIMEOUT));
        if (activeAssignments.isEmpty()) {
            return new PaperAccess(false, false);
        }
        boolean onlyBlind = activeAssignments.stream()
                .allMatch(a -> ReviewAssignment.TYPE_BLIND.equals(a.getReviewType()));
        return new PaperAccess(true, onlyBlind);
    }

    /**
     * 盲审匿名视图：清空可定位作者/导师的身份字段。
     *
     * <p>除显式身份字段外，还要清 {@code filePath} 与审计用户 ID——
     * 它们由上传人 userId 拼出（{@code {userId}/yyyy/MM/dd/...}），
     * 留着等于把学生 ID 直接交给盲审人。</p>
     */
    private void maskBlindIdentity(PaperInfo paper) {
        paper.setStudentId(null);
        paper.setStudentName(null);
        paper.setStudentUsername(null);
        paper.setStudentGrade(null);
        paper.setStudentMajor(null);
        paper.setStudentCollege(null);
        paper.setAuthor("盲审论文");
        paper.setTeacherId(null);
        paper.setTeacherName(null);
        paper.setTeacherRealName(null);
        paper.setCreateBy(null);
        paper.setUpdateBy(null);
        paper.setFilePath(null);
    }

    /**
     * 学生删除论文接口（支持软删除）
     * 只有待处理状态的论文可以删除，已进入查重流程的论文不允许删除
     *
     * @param paperId 论文ID
     * @return 删除结果
     */
    @PostMapping("/delete")
    public Result<String> deletePaper(@RequestParam Long paperId) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("删除论文请求 - 学生ID: {}, 论文ID: {}", studentId, paperId);

        // 1. 权限与状态校验
        PaperInfo paperInfo = paperInfoService.getById(paperId);
        if (paperInfo == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "论文不存在");
        }

        // 验证论文归属
        if (!paperInfo.getStudentId().equals(studentId)) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限删除他人论文");
        }

        // 验证论文状态：只有待处理状态的论文可以删除
        if (!DictConstants.PaperStatus.PENDING.equals(paperInfo.getPaperStatus())) {
            String statusLabel = paperInfoService.getPaperStatusLabel(paperInfo.getPaperStatus());
            return Result.error(ResultCode.PERMISSION_NOT_STATUS,
                    "当前论文状态为【" + statusLabel + "】，不允许删除");
        }
        // 先删除附件
        Long fileId = paperInfo.getFileId();
        if (fileId != null) {
            fileService.deleteFile(fileId);
        }
        // 2. 调用服务层执行删除
        boolean deleteSuccess = paperInfoService.deletePaper(paperId, studentId);
        if (!deleteSuccess) {
            return Result.error(ResultCode.BUSINESS_NO_SAFE, "论文删除失败，请重试");
        }

        log.info("论文删除成功 - 论文ID: {}, 学生ID: {}", paperId, studentId);
        return Result.success("论文删除成功");
    }
    @DeleteMapping("/delete/file")
    public Result<String> deleteFile(@RequestParam Long fileId) {
        if (fileId == null) {
            return Result.error(ResultCode.PARAM_ERROR, "文件 ID 不能为空");
        }

        FileInfo fileInfo = fileService.getById(fileId);
        if (fileInfo == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "文件不存在或已被删除");
        }

        // 安全修复（P0）：删除是写操作，必须校验归属。原实现只看 fileId，
        // 任意登录用户可删除他人上传的论文/附件记录（fileId 可枚举）。
        // 规则：上传人本人或管理员；教师即使能查看也不能删除他人文件。
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        boolean owner = fileInfo.getUploadUserId() != null
                && fileInfo.getUploadUserId().equals(currentUserId.toString());
        if (!owner && !UserBusinessInfoUtils.isAdmin()) {
            log.warn("文件删除越权访问拦截 - fileId: {}, userId: {}", fileId, currentUserId);
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权删除该文件", null);
        }

        boolean deleteSuccess = fileService.deleteFile(fileId);
        if (!deleteSuccess) {
            return Result.error(ResultCode.BUSINESS_NO_SAFE, "文件删除失败，请重试");
        }else {
            return Result.success("文件删除成功");
        }
    }

    /**
     * 重新提交论文接口（审核不通过后）
     * @param reSubmitReq 重新提交请求参数
     * @return 重新提交结果
     */
    @PostMapping("/resubmit")
    @OperationLog(type = "student_paper_resubmit", description = "学生重新提交论文", recordResult = true)
    public Result<Map<String, Object>> reSubmitPaper(@RequestBody PaperReSubmitReq reSubmitReq) {
        return studentReviewService.reSubmitPaper(reSubmitReq);
    }
    
    /**
     * 4. 论文撤回接口
     * 允许撤回已提交但未进入审核流程的论文
     *
     * @param request 撤回请求参数
     * @return 撤回结果
     */
    @PostMapping("/{paperId}/withdraw")
    @OperationLog(type = "student_paper_withdraw", description = "学生撤回论文", recordResult = true)
    public Result<String> withdrawPaper(
        @PathVariable Long paperId, 
        @RequestBody @Valid PaperWithdrawRequest request
    ) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("撤回论文请求 - 学生 ID: {}, 论文 ID: {}, 原因类型：{}, 详细描述：{}", 
            studentId, paperId, request.getWithdrawReasonType(), request.getReasonDetail());
            
        boolean success = paperInfoService.withdrawPaper(paperId, studentId, 
            String.format("[%s] %s", request.getWithdrawReasonType(), request.getReasonDetail() != null ? request.getReasonDetail() : ""));
        if (success) {
            return Result.success("论文撤回成功");
        } else {
            return Result.error(ResultCode.BUSINESS_NO_SAFE, "论文撤回失败");
        }
    }
    
    /**
     * 撤回后重新提交论文接口
     */
    @PostMapping("/{paperId}/resubmit-after-withdraw")
    @OperationLog(type = "student_paper_resubmit", description = "撤回后重新提交", recordResult = true)
    public Result<PaperInfo> resubmitAfterWithdraw(
        @PathVariable Long paperId,
        @RequestBody @Valid PaperReSubmitAfterWithdrawRequest request
    ) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("撤回后重新提交请求 - 学生 ID: {}, 论文 ID: {}", studentId, paperId);
        
        // 验证论文状态必须是 WITHDRAWN
        PaperInfo paper = paperInfoMapper.selectById(paperId);
        if (paper == null) {
            return Result.error(ResultCode.PARAM_ERROR, "论文不存在");
        }
        
        if (!DictConstants.PaperStatus.WITHDRAWN.equals(paper.getPaperStatus())) {
            return Result.error(ResultCode.PARAM_ERROR, "只有已撤回的论文才能重新提交，当前状态：" + paper.getPaperStatus());
        }
        
        // 验证论文归属
        if (!paper.getStudentId().equals(studentId)) {
            return Result.error(ResultCode.RESOURCE_NO_PERMISSION, "无权操作他人论文");
        }
        
        // 调用服务层重新提交
        PaperInfo updatedPaper = paperInfoService.resubmitAfterWithdraw(paperId, request, studentId);
        return Result.success("重新提交成功", updatedPaper);
    }
    
    /**
     * 5. 申请修改已通过论文接口
     * 对已通过审核的论文申请修改
     *
     * @param paperId 论文ID
     * @param request 修改申请参数
     * @return 申请结果
     */
    @PostMapping("/{paperId}/modify-request")
    @OperationLog(type = "student_paper_modify_request", description = "学生申请修改论文", recordResult = true)
    public Result<String> requestModification(@PathVariable Long paperId, @RequestBody PaperModifyRequest request) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("申请修改论文请求 - 学生ID: {}, 论文ID: {}, 原因: {}", studentId, paperId, request.getReason());
        
        boolean success = paperInfoService.requestPaperModification(paperId, studentId, request.getReason());
        if (success) {
            return Result.success("申请已提交，等待导师审核");
        } else {
            return Result.error(ResultCode.BUSINESS_NO_SAFE, "申请提交失败");
        }
    }
    
    /**
     * 6. 批量下载论文接口
     * 批量下载多篇论文（打包成ZIP）
     *
     * @param request 批量操作请求
     * @param response HTTP响应
     */
    @PostMapping("/batch-download")
    @OperationLog(type = "student_paper_batch_download", description = "学生批量下载论文", recordResult = true)
    public void batchDownloadPapers(@RequestBody BatchOperationRequest request, HttpServletResponse response) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("批量下载论文请求 - 学生ID: {}, 论文数量: {}", studentId, request.getPaperIds().size());
        
        paperInfoService.batchDownloadPapers(request.getPaperIds(), studentId, response);
    }
    
    /**
     * 7. 批量删除论文接口
     * 批量删除多篇论文
     *
     * @param request 批量操作请求
     * @return 删除结果
     */
    @DeleteMapping("/batch-delete")
    @OperationLog(type = "student_paper_batch_delete", description = "学生批量删除论文", recordResult = true)
    public Result<Map<String, Object>> batchDeletePapers(@RequestBody BatchOperationRequest request) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("批量删除论文请求 - 学生ID: {}, 论文数量: {}", studentId, request.getPaperIds().size());
        
        Map<String, Object> result = paperInfoService.batchDeletePapers(request.getPaperIds(), studentId);
        return Result.success("批量删除成功", result);
    }
    
    /**
     * 7.5 获取论文版本历史列表接口
     * 学生端「我的论文」展开版本历史时间轴调用
     *
     * @param paperId 论文ID
     * @return 版本列表（按提交版本号升序）
     */
    @GetMapping("/{paperId}/versions")
    public Result<List<PaperVersionItemDTO>> getPaperVersions(@PathVariable Long paperId) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        List<PaperVersionItemDTO> versions = paperInfoService.getPaperVersions(paperId, studentId);
        return Result.success(versions);
    }

    /**
     * 8. 获取论文版本详情接口
     * 获取指定版本的详细信息
     *
     * @param paperId 论文ID
     * @param versionId 版本ID
     * @return 版本详情
     */
    @GetMapping("/{paperId}/versions/{versionId}")
    public Result<PaperVersionDTO> getPaperVersion(@PathVariable Long paperId, @PathVariable Long versionId) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        PaperVersionDTO version = paperInfoService.getPaperVersion(paperId, versionId, studentId);
        return Result.success(version);
    }
    
    /**
     * 9. 版本对比接口
     * 对比两个版本的差异
     *
     * @param request 版本对比请求
     * @return 对比结果
     */
    @PostMapping("/compare-versions")
    public Result<VersionCompareResult> comparePaperVersions(@RequestBody VersionCompareRequest request) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        VersionCompareResult result = paperInfoService.comparePaperVersions(request.getPaperId(), 
            request.getVersionIds(), studentId);
        return Result.success(result);
    }
    
    /**
     * 10. 下载版本对比报告接口
     * 下载两个版本的对比报告（PDF格式）
     *
     * @param request 版本对比请求
     * @param response HTTP响应
     */
    @PostMapping("/download-version-compare")
    public void downloadVersionCompareReport(@RequestBody VersionCompareRequest request, HttpServletResponse response) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        paperInfoService.downloadVersionCompareReport(request.getPaperId(), request.getVersionIds(), 
            studentId, response);
    }
    
    /**
     * 11. 下载单个版本接口
     * 下载指定版本的论文文件
     *
     * @param versionId 版本ID
     * @param response HTTP响应
     */
    @GetMapping("/versions/{versionId}/download")
    public void downloadPaperVersion(@PathVariable Long versionId, HttpServletResponse response) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        paperInfoService.downloadPaperVersion(versionId, studentId, response);
    }
    
    /**
     * 13. 直接下载论文接口
     * 直接下载指定论文的文件
     *
     * @param paperId 论文ID
     * @param response HTTP响应
     */
    @GetMapping("/{paperId}/download")
    public void downloadPaper(@PathVariable Long paperId, HttpServletResponse response) {
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("学生{}请求下载论文{}", studentId, paperId);
            
            // 调用服务层方法下载论文
            paperInfoService.downloadPaper(paperId, studentId, response);
            
        } catch (Exception e) {
            log.error("下载论文失败 - 论文ID: {}", paperId, e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("论文下载失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("设置错误响应失败", ex);
            }
        }
    }
    
    /**
     * 12. 下载附件接口
     * 下载论文附件
     *
     * @param attachmentId 附件ID
     * @param response HTTP响应
     */
    @GetMapping("/attachments/{attachmentId}/download")
    public void downloadAttachment(@PathVariable String attachmentId, HttpServletResponse response) {
        Long studentId = UserBusinessInfoUtils.getCurrentUserId();
        paperInfoService.downloadAttachment(attachmentId, studentId, response);
    }
}
