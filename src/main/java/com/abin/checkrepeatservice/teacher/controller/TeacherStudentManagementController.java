package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.annotation.OperationLog;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.teacher.dto.*;
import com.abin.checkrepeatservice.teacher.service.TeacherStudentManagementService;
import com.abin.checkrepeatservice.user.service.SysUserService;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 教师学生管理控制器
 * 提供学生列表、统计、分配、消息等完整功能
 */
@RestController
@RequestMapping("/api/teacher/students")
@PreAuthorize("hasAuthority('TEACHER')")
@Tag(name = "教师学生管理", description = "教师对学生进行管理的相关接口")
@Slf4j
public class TeacherStudentManagementController {

    @Resource
    private TeacherStudentManagementService studentManagementService;
    
    @Resource
    private PaperInfoMapper paperInfoMapper;
    
    @Resource
    private FileService fileService;
    
    @Resource
    private SysUserService sysUserService;

    /**
     * 1. 获取学生列表接口
     * GET /api/teacher/students
     */
    @GetMapping
    @Operation(summary = "获取学生列表", description = "获取教师指导的学生列表，支持分页和筛选")
    public Result<Object> getStudentList(
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId,
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer pageSize,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String search,
            @Parameter(description = "论文状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "学院筛选") @RequestParam(required = false) String college) {
        
        // 如果没有传入teacherId，则使用当前登录用户的ID
        if (teacherId == null) {
            teacherId = UserBusinessInfoUtils.getCurrentUserId();
        }
        
        log.info("获取学生列表: teacherId={}, page={}, pageSize={}, search={}, status={}, college={}", 
                teacherId, page, pageSize, search, status, college);
        
        StudentListRequestDTO requestDTO = new StudentListRequestDTO();
        requestDTO.setTeacherId(teacherId);
        requestDTO.setPage(page);
        requestDTO.setPageSize(pageSize);
        requestDTO.setSearch(search);
        requestDTO.setStatus(status);
        requestDTO.setCollege(college);
        
        return studentManagementService.getStudentList(requestDTO);
    }

    /**
     * 2. 删除学生接口
     * DELETE /api/teacher/students/{studentId}
     */
    @DeleteMapping("/{studentId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "删除学生", description = "删除指定学生（仅管理员，教师端只保留查看权限）")
    @OperationLog(type = "delete_student", description = "删除学生")
    public Result<String> deleteStudent(
            @Parameter(description = "学生ID") @PathVariable Long studentId) {
        
        log.info("删除学生: studentId={}", studentId);
        boolean result = studentManagementService.deleteStudent(studentId);
        if (result) {
            return Result.success("删除成功");
        } else {
            return Result.error(ResultCode.SYSTEM_ERROR, "删除失败");
        }
    }

    /**
     * 3. 获取统计信息接口
     * GET /api/teacher/students/statistics
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取学生统计信息", description = "获取教师指导学生的各项统计数据")
    public Result<Object> getStudentStats(
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId) {
        
        if (teacherId == null) {
            teacherId = UserBusinessInfoUtils.getCurrentUserId();
        }
        
        log.info("获取学生统计信息: teacherId={}", teacherId);
        return studentManagementService.getStudentStats(teacherId);
    }

    /**
     * 4. 分配导师接口
     * POST /api/teacher/students/{studentId}/assign-advisor
     */
    @PostMapping("/{studentId}/assign-advisor")
    @Operation(summary = "分配导师", description = "为学生分配指导老师")
    @OperationLog(type = "assign_advisor", description = "分配导师")
    public Result<String> assignAdvisor(
            @Parameter(description = "学生ID") @PathVariable Long studentId,
            @Parameter(description = "分配导师请求体", required = true)
            @RequestBody AssignAdvisorDTO assignAdvisorDTO) {
        
        // 数据范围收口：教师只可操作「本人名下」的学生，防止借分配入口改他人指导关系
        String currentRole = UserBusinessInfoUtils.getCurrentUserRoleCode();
        if ("TEACHER".equals(currentRole)) {
            Long currentTeacherId = UserBusinessInfoUtils.getCurrentUserId();
            List<PaperInfo> papers = paperInfoMapper.selectList(new LambdaQueryWrapper<PaperInfo>()
                    .eq(PaperInfo::getStudentId, studentId)
                    .eq(PaperInfo::getIsDeleted, 0));
            boolean ownsStudent = !papers.isEmpty()
                    && papers.stream().allMatch(p -> Objects.equals(p.getTeacherId(), currentTeacherId));
            if (!ownsStudent) {
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "仅可管理本人名下的学生");
            }
        }
        log.info("分配导师: studentId={}, advisorId={}", studentId, assignAdvisorDTO.getAdvisorId());
        boolean result = studentManagementService.assignAdvisor(studentId, assignAdvisorDTO);
        if (result) {
            return Result.success("导师分配成功");
        } else {
            return Result.error(ResultCode.SYSTEM_ERROR, "导师分配失败");
        }
    }

    /**
     * 5. 发送消息接口
     * POST /api/teacher/messages/send
     */
    @PostMapping("/messages/send")
    @Operation(summary = "发送消息", description = "向学生发送消息")
    @OperationLog(type = "send_message", description = "发送消息给学生")
    public Result<String> sendMessage(
            @Parameter(description = "消息发送请求体", required = true)
            @RequestBody SendMessageDTO sendMessageDTO) {
        
        log.info("发送消息: receiverId={}, title={}", sendMessageDTO.getReceiverId(), sendMessageDTO.getTitle());
        boolean result = studentManagementService.sendMessage(sendMessageDTO);
        if (result) {
            return Result.success("消息发送成功");
        } else {
            return Result.error(ResultCode.SYSTEM_ERROR, "消息发送失败");
        }
    }

    /**
     * 6.1 批量分配导师接口
     * POST /api/teacher/students/batch-assign-advisor
     */
    @PostMapping("/batch-assign-advisor")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "批量分配导师", description = "为多个学生批量分配同一指导老师（仅管理员）")
    @OperationLog(type = "batch_assign_advisor", description = "批量分配导师")
    public Result<Object> batchAssignAdvisor(
            @Parameter(description = "批量分配导师请求体", required = true)
            @RequestBody BatchAssignAdvisorDTO batchAssignDTO) {
        
        log.info("批量分配导师: studentIds={}, advisorId={}", 
                batchAssignDTO.getStudentIds(), batchAssignDTO.getAdvisorId());
        BatchOperationResultDTO result = studentManagementService.batchAssignAdvisor(batchAssignDTO);
        return Result.<Object>success("批量分配完成", result);
    }

    /**
     * 6.2 批量发送消息接口
     * POST /api/teacher/messages/batch-send
     */
    @PostMapping("/messages/batch-send")
    @Operation(summary = "批量发送消息", description = "向多个学生批量发送相同消息")
    @OperationLog(type = "batch_send_message", description = "批量发送消息")
    public Result<Object> batchSendMessage(
            @Parameter(description = "批量发送消息请求体", required = true)
            @RequestBody BatchSendMessageDTO batchSendDTO) {
        
        log.info("批量发送消息: receiverIds={}, title={}", 
                batchSendDTO.getReceiverIds(), batchSendDTO.getTitle());
        BatchOperationResultDTO result = studentManagementService.batchSendMessage(batchSendDTO);
        return Result.<Object>success("批量发送完成", result);
    }

    /**
     * 6.3 批量删除学生接口
     * DELETE /api/teacher/students/batch-delete
     */
    @DeleteMapping("/batch-delete")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "批量删除学生", description = "批量删除多个学生（仅管理员）")
    @OperationLog(type = "batch_delete_student", description = "批量删除学生")
    public Result<Object> batchDeleteStudents(
            @Parameter(description = "批量删除请求体", required = true)
            @RequestBody BatchDeleteDTO batchDeleteDTO) {
        
        log.info("批量删除学生: studentIds={}", batchDeleteDTO.getStudentIds());
        BatchOperationResultDTO result = studentManagementService.batchDeleteStudents(batchDeleteDTO);
        return Result.<Object>success("批量删除完成", result);
    }

    /**
     * 7. 数据导出接口
     * GET /api/teacher/students/export
     */
    @GetMapping("/export")
    @Operation(summary = "导出学生数据", description = "导出学生列表数据为Excel文件")
    @OperationLog(type = "export_students", description = "导出学生数据")
    public Result<String> exportStudents(
            @Parameter(description = "教师ID") @RequestParam(required = false) Long teacherId,
            @Parameter(description = "导出格式") @RequestParam(defaultValue = "excel") String format,
            @Parameter(description = "搜索条件") @RequestParam(required = false) String search,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "学院筛选") @RequestParam(required = false) String college) {
        
        if (teacherId == null) {
            teacherId = UserBusinessInfoUtils.getCurrentUserId();
        }
        
        log.info("导出学生数据: teacherId={}, format={}", teacherId, format);
        
        ExportRequestDTO exportRequest = new ExportRequestDTO();
        exportRequest.setTeacherId(teacherId);
        exportRequest.setFormat(format);
        exportRequest.setSearch(search);
        exportRequest.setStatus(status);
        exportRequest.setCollege(college);
        
        String filePath = studentManagementService.exportStudents(exportRequest);
        return Result.success("导出成功", filePath);
    }

    /**
     * 9. 添加学生接口（REST 风格入口）
     * POST /api/teacher/students
     *
     * <p>与 /add 等价，只保留管理员/超管入口；教师角色在此路径上由过滤器链直接返回 403。</p>
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "添加学生", description = "添加新的学生用户（仅管理员）")
    @OperationLog(type = "add_student", description = "添加学生")
    public Result<Object> addStudentRest(
            @Parameter(description = "添加学生请求体", required = true)
            @RequestBody AddStudentDTO addStudentDTO) {
        return addStudent(addStudentDTO);
    }

    /**
     * 9.1 添加学生接口
     * POST /api/teacher/students/add
     */
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "添加学生", description = "添加新的学生用户（仅管理员）")
    @OperationLog(type = "add_student", description = "添加学生")
    public Result<Object> addStudent(
            @Parameter(description = "添加学生请求体", required = true)
            @RequestBody AddStudentDTO addStudentDTO) {
        
        log.info("添加学生: username={}, studentName={}", 
                addStudentDTO.getUsername(), addStudentDTO.getStudentName());
        return studentManagementService.addStudent(addStudentDTO);
    }
    
    /**
     * 8.1 下载学生导入模板（仅管理员）
     * GET /api/teacher/students/import-template
     */
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "下载学生导入模板", description = "下载批量导入学生使用的 Excel 模板（仅管理员）")
    public void downloadImportTemplate(HttpServletResponse response) throws Exception {
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("学号"));
        head.add(List.of("姓名"));
        head.add(List.of("学院"));
        head.add(List.of("专业"));
        head.add(List.of("年级"));
        head.add(List.of("邮箱"));

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=student_import_template.xlsx");
        EasyExcel.write(response.getOutputStream())
                .head(head)
                .sheet("学生导入模板")
                .doWrite(new ArrayList<>());
    }

    /**
     * 8. 数据导入接口
     * POST /api/teacher/students/import
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "导入学生数据", description = "从Excel文件导入学生数据（仅管理员）")
    @OperationLog(type = "import_students", description = "导入学生数据")
    public Result<Object> importStudents(
            @Parameter(description = "学生导入 Excel 文件", required = true)
            @RequestParam("file") MultipartFile file) {
        
        log.info("导入学生数据: fileName={}", file.getOriginalFilename());
        ImportResultDTO result = studentManagementService.importStudents(file);
        return Result.success("导入完成", result);
    }
    
    /**
     * 10. 获取学生论文信息接口（新增）
     * GET /api/teacher/students/{studentId}/paper
     */
    @GetMapping("/{studentId}/paper")
    @Operation(summary = "获取学生论文信息", description = "获取指定学生的最新论文信息")
    public Result<StudentPaperInfoDTO> getStudentPaperInfo(
            @Parameter(description = "学生ID") @PathVariable Long studentId) {
        
        Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
        log.info("获取学生论文信息: teacherId={}, studentId={}", teacherId, studentId);
        
        // 1. 验证学生是否存在且属于当前教师指导
        SysUser student = sysUserService.getById(studentId);
        if (student == null) {
            return Result.error(ResultCode.PARAM_ERROR, "学生不存在");
        }
        
        // 2. 验证指导关系
        PaperInfo latestPaper = paperInfoMapper.selectLatestPaper(studentId);
        if (latestPaper == null) {
            log.warn("学生暂无论文信息: studentId={}", studentId);
            return Result.error(ResultCode.PARAM_ERROR, "该学生暂无论文");
        }
        
        log.info("指导关系验证 - 当前教师ID: {}, 论文指导教师ID: {}, 学生ID: {}, 论文ID: {}", 
                teacherId, latestPaper.getTeacherId(), studentId, latestPaper.getId());
        
        if (!latestPaper.getTeacherId().equals(teacherId) && !sysUserService.isAdmin(teacherId)) {
            log.warn("权限验证失败 - 教师无权查看学生论文: 当前教师ID={}, 论文指导教师ID={}, 学生ID={}", 
                    teacherId, latestPaper.getTeacherId(), studentId);
            return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权限查看此学生的论文信息");
        }
        
        // 3. 构造返回数据
        StudentPaperInfoDTO paperInfoDTO = new StudentPaperInfoDTO();
        paperInfoDTO.setPaperId(latestPaper.getId());
        paperInfoDTO.setPaperTitle(latestPaper.getPaperTitle());
        paperInfoDTO.setFileId(latestPaper.getFileId());
        paperInfoDTO.setPaperStatus(latestPaper.getPaperStatus());
        paperInfoDTO.setSubmitTime(latestPaper.getSubmitTime() != null ? 
            latestPaper.getSubmitTime().toString() : null);
        paperInfoDTO.setSimilarity(latestPaper.getSimilarityRate() != null ? 
            latestPaper.getSimilarityRate().doubleValue() : 0.0);
        
        // 4. 获取文件详细信息
        if (latestPaper.getFileId() != null) {
            FileInfo fileInfo = fileService.getById(latestPaper.getFileId());
            if (fileInfo != null) {
                paperInfoDTO.setFileName(fileInfo.getOriginalFilename());
                paperInfoDTO.setFileSize(fileInfo.getFileSizeDesc());
            }
        }
        
        log.info("获取学生论文信息成功: teacherId={}, studentId={}, paperId={}", 
                teacherId, studentId, latestPaper.getId());
        return Result.success("获取成功", paperInfoDTO);
    }
    
    /**
     * 学生论文信息DTO
     */
    public static class StudentPaperInfoDTO {
        private Long paperId;
        private String paperTitle;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long fileId;
        private String fileName;
        private String fileSize;
        private String submitTime;
        private String paperStatus;
        private Double similarity;
        
        // Getters and Setters
        public Long getPaperId() { return paperId; }
        public void setPaperId(Long paperId) { this.paperId = paperId; }
        
        public String getPaperTitle() { return paperTitle; }
        public void setPaperTitle(String paperTitle) { this.paperTitle = paperTitle; }
        
        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public String getFileSize() { return fileSize; }
        public void setFileSize(String fileSize) { this.fileSize = fileSize; }
        
        public String getSubmitTime() { return submitTime; }
        public void setSubmitTime(String submitTime) { this.submitTime = submitTime; }
        
        public String getPaperStatus() { return paperStatus; }
        public void setPaperStatus(String paperStatus) { this.paperStatus = paperStatus; }
        
        public Double getSimilarity() { return similarity; }
        public void setSimilarity(Double similarity) { this.similarity = similarity; }
    }
}