package com.abin.checkrepeatservice.student.controller;


import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.PaperInfoService;
import com.abin.checkrepeatservice.student.service.StudentReviewService;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.monitor.service.BusinessMetricsService;
import com.abin.checkrepeatservice.student.vo.PaperQueryRequest;
import com.abin.checkrepeatservice.student.vo.PaperSubmitRequest;
import com.abin.checkrepeatservice.student.vo.PaperReSubmitReq;
import com.abin.checkrepeatservice.student.dto.*;
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
        try {
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

        } catch (Exception e) {
            log.error("论文提交失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "论文提交失败: " + "系统异常，请稍后重试");
        }
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
        try {
            Page<PaperInfo> result = paperInfoService.getStudentPaperPage(request);
            return Result.success(result);
        } catch (BusinessException e) {
            log.warn("论文列表分页查询业务异常：{}", e.getMessage());
            return Result.error(ResultCode.SYSTEM_ERROR, "论文列表分页查询错误");
        }
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
        // 2. 重提闭环字段由服务端计算（canResubmit / nextAction），前端不再猜状态
        paperInfoService.enrichResubmitInfo(paperInfo);
        return Result.success("论文详情查询成功", paperInfo);
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
        try {
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

        } catch (Exception e) {
            log.error("论文删除失败 - 论文ID: {}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "论文删除失败: " + "系统异常，请稍后重试");
        }
    }
    @DeleteMapping("/delete/file")
    public Result<String> deleteFile(@RequestParam Long fileId) {
        if (fileId == null) {
            return Result.error(ResultCode.PARAM_ERROR, "文件 ID 不能为空");
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
        try {
            return studentReviewService.reSubmitPaper(reSubmitReq);
        } catch (Exception e) {
            log.error("论文重新提交失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "论文重新提交失败: " + "系统异常，请稍后重试");
        }
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
        try {
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
        } catch (Exception e) {
            log.error("论文撤回失败 - 论文 ID: {}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "论文撤回失败：" + "系统异常，请稍后重试");
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
        try {
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
        } catch (BusinessException e) {
            log.error("撤回后重新提交失败 - 论文 ID: {}", paperId, e);
            return Result.error(ResultCode.BUSINESS_NO_SAFE, e.getMessage());
        } catch (Exception e) {
            log.error("撤回后重新提交失败 - 论文 ID: {}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "重新提交失败：" + "系统异常，请稍后重试");
        }
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
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("申请修改论文请求 - 学生ID: {}, 论文ID: {}, 原因: {}", studentId, paperId, request.getReason());
            
            boolean success = paperInfoService.requestPaperModification(paperId, studentId, request.getReason());
            if (success) {
                return Result.success("申请已提交，等待导师审核");
            } else {
                return Result.error(ResultCode.BUSINESS_NO_SAFE, "申请提交失败");
            }
        } catch (Exception e) {
            log.error("申请修改论文失败 - 论文ID: {}", paperId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "申请提交失败: " + "系统异常，请稍后重试");
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
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("批量下载论文请求 - 学生ID: {}, 论文数量: {}", studentId, request.getPaperIds().size());
            
            paperInfoService.batchDownloadPapers(request.getPaperIds(), studentId, response);
        } catch (Exception e) {
            log.error("批量下载论文失败", e);
            // 在实际应用中应该返回适当的错误响应
        }
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
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("批量删除论文请求 - 学生ID: {}, 论文数量: {}", studentId, request.getPaperIds().size());
            
            Map<String, Object> result = paperInfoService.batchDeletePapers(request.getPaperIds(), studentId);
            return Result.success("批量删除成功", result);
        } catch (Exception e) {
            log.error("批量删除论文失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "批量删除失败: " + "系统异常，请稍后重试");
        }
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
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            PaperVersionDTO version = paperInfoService.getPaperVersion(paperId, versionId, studentId);
            return Result.success(version);
        } catch (Exception e) {
            log.error("获取论文版本详情失败 - 论文ID: {}, 版本ID: {}", paperId, versionId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取版本详情失败: " + "系统异常，请稍后重试");
        }
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
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            VersionCompareResult result = paperInfoService.comparePaperVersions(request.getPaperId(), 
                request.getVersionIds(), studentId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("论文版本对比失败 - 论文ID: {}", request.getPaperId(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "版本对比失败: " + "系统异常，请稍后重试");
        }
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
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            paperInfoService.downloadVersionCompareReport(request.getPaperId(), request.getVersionIds(), 
                studentId, response);
        } catch (Exception e) {
            log.error("下载版本对比报告失败 - 论文ID: {}", request.getPaperId(), e);
        }
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
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            paperInfoService.downloadPaperVersion(versionId, studentId, response);
        } catch (Exception e) {
            log.error("下载论文版本失败 - 版本ID: {}", versionId, e);
        }
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
        try {
            Long studentId = UserBusinessInfoUtils.getCurrentUserId();
            paperInfoService.downloadAttachment(attachmentId, studentId, response);
        } catch (Exception e) {
            log.error("下载附件失败 - 附件ID: {}", attachmentId, e);
        }
    }
}
