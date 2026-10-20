package com.abin.checkrepeatservice.student.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.constant.DictConstants;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.dto.ReportPreviewDTO;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.student.service.CheckReportService;
import com.abin.checkrepeatservice.student.vo.ReportDownloadReq;
import com.abin.checkrepeatservice.common.utils.PdfReportGenerator;
import com.abin.checkrepeatservice.common.utils.ReportContentProcessor;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 查重报告服务实现类
 */
@Service
@Slf4j
public class CheckReportServiceImpl extends ServiceImpl<CheckReportMapper, CheckReport> implements CheckReportService {

    @Resource
    private CheckTaskMapper checkTaskMapper;

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private ReportContentProcessor reportContentProcessor;

    @Resource
    private PdfReportGenerator pdfReportGenerator;

    // 报告存储根路径（从配置文件获取）
    @Value("${report.storage.base-path}")
    private String reportBasePath;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckReport generateReport(Long taskId, Double checkRate, String repeatDetails) {
        CheckTask checkTask = checkTaskMapper.selectById(taskId);
        if (checkTask == null || checkTask.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "关联的查重任务不存在");
        }

        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        if (paperInfo == null || paperInfo.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "关联的论文不存在");
        }

        // 1. 先生成PDF文件
        String taskNo = checkTask.getTaskNo();
        String reportNo = "REPORT" + taskNo.substring(5);
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String reportFileName = reportNo + ".pdf";
        String reportPath = reportBasePath + dateDir + "/" + reportFileName;

        // 调试：打印路径信息
        log.info("开始生成PDF报告，路径: {}", reportPath);

        try {
            // 先创建目录
            File reportDir = new File(reportBasePath + dateDir);
            if (!reportDir.exists()) {
                boolean created = reportDir.mkdirs();
                log.info("创建报告目录: {}, 成功: {}", reportDir.getAbsolutePath(), created);
            }

            // 临时创建报告对象用于生成预览
            CheckReport tempReport = new CheckReport();
            tempReport.setReportNo(reportNo);
            tempReport.setRepeatDetails(repeatDetails);

            ReportPreviewDTO previewDTO = reportContentProcessor.buildReportPreviewDTO(tempReport, checkTask);

            // 生成PDF文件
            pdfReportGenerator.generatePdfToFile(previewDTO, reportPath);

            // 验证文件是否生成成功
            File generatedFile = new File(reportPath);
            if (!generatedFile.exists() || generatedFile.length() == 0) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "PDF文件生成失败，文件不存在或为空");
            }

            log.info("PDF文件生成成功，大小: {} bytes", generatedFile.length());

        } catch (Exception e) {
            log.error("PDF报告生成失败（任务ID：{}）：", taskId, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "报告文件生成失败: " + "系统异常，请稍后重试");
        }

        // 2. 文件生成成功后再保存数据库记录
        CheckReport checkReport = new CheckReport();
        checkReport.setTaskId(taskId);
        checkReport.setReportNo(reportNo);
        checkReport.setRepeatDetails(repeatDetails);
        checkReport.setReportPath(reportPath);
        checkReport.setReportType("pdf");
        UserBusinessInfoUtils.setAuditField(checkReport, true);
        save(checkReport);

        log.info("查重报告生成完成 - 报告ID: {}, 文件路径: {}", checkReport.getId(), reportPath);

        return checkReport;
    }
    @Override
    public Result<ReportPreviewDTO> previewReport(Long reportId) {
        // 1. 查询报告信息
        CheckReport checkReport = getById(reportId);
        if (checkReport == null || checkReport.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "查重报告不存在或已删除");
        }

        // 2. 查询关联任务
        CheckTask checkTask = checkTaskMapper.selectById(checkReport.getTaskId());
        if (checkTask == null || checkTask.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "报告关联的查重任务不存在");
        }

        // 3. 权限校验（学生查自己的，教师查指导的，管理员查所有）
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        SysUser currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        boolean isStudentOwner = paperInfo.getStudentId().equals(currentUser.getId());
        boolean isTeacherOwner = Objects.equals(paperInfo.getTeacherId(), currentUser.getId());
        boolean isAdmin = UserBusinessInfoUtils.isAdmin();
        if (!isStudentOwner && !isTeacherOwner && !isAdmin) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限预览该报告");
        }

        // 4. 构建预览DTO
        ReportPreviewDTO previewDTO = reportContentProcessor.buildReportPreviewDTO(checkReport, checkTask);
        return Result.success("报告预览成功", previewDTO);
    }

    @Override
    public void downloadReport(ReportDownloadReq downloadReq, HttpServletResponse response) {
        Long reportId = downloadReq.getReportId();
        String format = downloadReq.getFormat().toLowerCase();

        // 1. 校验报告存在性与权限
        CheckReport checkReport = getById(reportId);
        if (checkReport == null || checkReport.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "查重报告不存在或已删除");
        }
        // 权限校验（复用previewReport的逻辑，此处简化）
        CheckTask checkTask = checkTaskMapper.selectById(checkReport.getTaskId());
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());
        SysUser currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        boolean hasPermission = Objects.equals(paperInfo.getStudentId(), currentUser.getId())
                || Objects.equals(paperInfo.getTeacherId(), currentUser.getId())
                || UserBusinessInfoUtils.isAdmin();
        if (!hasPermission) {
            throw new BusinessException(ResultCode.PERMISSION_NO_ACCESS, "无权限下载该报告");
        }

        // 2. 校验格式（仅支持pdf/html）
        if (!"pdf".equals(format) && !"html".equals(format)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持pdf和html格式的报告下载");
        }

        // 3. 设置响应头（触发浏览器下载）
        try {
            String reportNo = checkReport.getReportNo();
            String fileName = reportNo + "." + format;
            // 处理中文文件名编码（避免乱码）
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());
            // 设置响应头
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);

            // 4. 生成并输出报告文件
            if ("pdf".equals(format)) {
                // 若已存在PDF文件，直接读取；否则重新生成
                if (java.nio.file.Files.exists(java.nio.file.Paths.get(checkReport.getReportPath()))) {
                    // 直接读取文件输出（简化逻辑，实际需用流读取）
                    java.nio.file.Files.copy(
                            java.nio.file.Paths.get(checkReport.getReportPath()),
                            response.getOutputStream()
                    );
                } else {
                    // 重新生成PDF并输出
                    CheckTask task = checkTaskMapper.selectById(checkReport.getTaskId());
                    ReportPreviewDTO previewDTO = reportContentProcessor.buildReportPreviewDTO(checkReport, task);
                    pdfReportGenerator.generatePdf(previewDTO, response.getOutputStream());
                }
            } else if ("html".equals(format)) {
                // HTML格式（后续扩展，此处简化为返回提示）
                response.setContentType("text/html; charset=UTF-8");
                response.getWriter().write("<h1>HTML格式报告下载功能待扩展</h1>");
            }

            // 5. 刷新输出流
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.error("报告下载失败（报告ID：" + reportId + "）：" , e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "报告下载异常，文件输出失败");
        }
    }

    @Override
    public Result<List<CheckReport>> getMyReportList(Long paperId) {
        SysUser currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        LambdaQueryWrapper<CheckReport> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CheckReport::getIsDeleted, 0)
                .orderByDesc(CheckReport::getCreateTime);

        // 1. 按角色过滤（关联任务与论文）
        // S11 安全加固：inSql 字符串拼接改为 apply {0} 参数绑定（统一模式，防复制扩散）
        if (UserBusinessInfoUtils.isStudent()) {
            // 学生：仅查询自己论文的报告
            queryWrapper.apply("task_id IN (SELECT id FROM check_task WHERE paper_id IN (" +
                    "SELECT id FROM paper_info WHERE student_id = {0} AND is_deleted = 0) AND is_deleted = 0)",
                    currentUser.getId());
        } else if (UserBusinessInfoUtils.isTeacher()) {
            // 教师：仅查询自己指导论文的报告
            queryWrapper.apply("task_id IN (SELECT id FROM check_task WHERE paper_id IN (" +
                    "SELECT id FROM paper_info WHERE teacher_id = {0} AND is_deleted = 0) AND is_deleted = 0)",
                    currentUser.getId());
        }

        // 2. 按论文ID过滤（可选）
        if (paperId != null) {
            queryWrapper.apply("task_id IN (SELECT id FROM check_task WHERE paper_id = {0} AND is_deleted = 0)", paperId);
        }

        List<CheckReport> reportList = list(queryWrapper);
        return Result.success("历史报告列表查询成功", reportList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> deleteReport(Long reportId) {
        // 1. 查询报告与关联任务
        CheckReport checkReport = getById(reportId);
        if (checkReport == null || checkReport.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "查重报告不存在或已删除");
        }
        CheckTask checkTask = checkTaskMapper.selectById(checkReport.getTaskId());
        if (checkTask == null || checkTask.getIsDeleted() == 1) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "报告关联的查重任务不存在");
        }
        PaperInfo paperInfo = paperInfoMapper.selectById(checkTask.getPaperId());

        // 2. 权限校验（仅学生本人或管理员可删除）
        SysUser currentUser = UserBusinessInfoUtils.getCurrentSysUser();
        boolean isStudentOwner = paperInfo.getStudentId().equals(currentUser.getId());
        boolean isAdmin = UserBusinessInfoUtils.isAdmin();
        if (!isStudentOwner && !isAdmin) {
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限删除该报告");
        }

        // 3. 状态校验（仅关联任务未审核的报告可删除）
        if (!Objects.equals(paperInfo.getPaperStatus(), DictConstants.PaperStatus.PENDING)) { // 2-待审核，3-审核通过，4-审核不通过
            return Result.error(ResultCode.PERMISSION_NOT_STATUS, "已进入审核流程的报告不允许删除");
        }

        // 4. 软删除报告
        removeById(reportId);

        // 5. 物理删除报告文件（可选）
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get(checkReport.getReportPath());
            if (java.nio.file.Files.exists(filePath)) {
                java.nio.file.Files.delete(filePath);
                log.info("报告文件删除成功：{}", checkReport.getReportPath());
            }
        } catch (IOException e) {
            log.error("报告文件删除失败：", e);
            // 文件删除失败不影响数据库删除结果
        }

        // 6. 更新任务的reportId为null
        checkTask.setReportId(null);
        checkTaskMapper.updateById(checkTask);

        return Result.success("报告删除成功");
    }
}
