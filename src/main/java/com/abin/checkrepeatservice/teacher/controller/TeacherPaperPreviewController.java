package com.abin.checkrepeatservice.teacher.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.service.FilePreviewService;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.abin.checkrepeatservice.user.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 教师论文预览控制器
 * 提供教师在线预览指导学生论文原文的功能
 */
@Slf4j
@RestController
@RequestMapping("/api/teacher/papers")
@PreAuthorize("hasAuthority('TEACHER')")
@Tag(name = "教师论文预览", description = "教师在线预览指导学生论文原文")
public class TeacherPaperPreviewController {

    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private FilePreviewService filePreviewService;
    
    @Resource
    private FileService fileService;
    
    @Resource
    private SysUserService sysUserService;

    /**
     * 教师在线预览论文原文
     * @param paperId 论文ID
     * @return 预览响应
     */
    @GetMapping("/{paperId}/preview")
    @Operation(summary = "在线预览论文原文", description = "教师在线预览指导学生的论文原文")
    public ResponseEntity<?> previewPaper(
            @Parameter(description = "论文ID") @PathVariable Long paperId) {
        
        try {
            Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("教师请求预览论文原文 - 教师ID: {}, 论文ID: {}", teacherId, paperId);

            // 1. 验证论文存在性和权限
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null) {
                log.warn("论文不存在 - 论文ID: {}", paperId);
                return ResponseEntity.notFound().build();
            }

            // 2. 验证教师权限（只能预览自己指导的学生论文）
            if (!paperInfo.getTeacherId().equals(teacherId)) {
                log.warn("无权限预览论文 - 教师ID: {}, 论文ID: {}, 论文指导教师ID: {}", 
                        teacherId, paperId, paperInfo.getTeacherId());
                return ResponseEntity.status(403).body("无权限预览此论文");
            }

            // 3. 验证论文状态（确保论文已提交且未删除）
            if (paperInfo.getIsDeleted() == 1) {
                log.warn("论文已被删除 - 论文ID: {}", paperId);
                return ResponseEntity.notFound().build();
            }

            // 4. 验证文件存在性
            Long fileId = paperInfo.getFileId();
            if (fileId == null) {
                log.warn("论文文件不存在 - 论文 ID: {}", paperId);
                return ResponseEntity.notFound().build();
            }

            // 5. 调用智能预览服务
            ResponseEntity<?> previewResponse = filePreviewService.smartPreview(fileId);
            
            log.info("论文原文预览成功 - 教师ID: {}, 论文ID: {}, 文件ID: {}", 
                    teacherId, paperId, fileId);
            
            return previewResponse;

        } catch (Exception e) {
            log.error("论文原文预览失败 - 论文ID: {}", paperId, e);
            return ResponseEntity.status(500).body("预览服务暂时不可用");
        }
    }

    /**
     * 获取论文预览信息
     * @param paperId 论文ID
     * @return 论文预览信息
     */
    @GetMapping("/{paperId}/preview-info")
    @Operation(summary = "获取论文预览信息", description = "获取论文的基本信息用于预览界面展示")
    public Result<PaperPreviewInfoDTO> getPaperPreviewInfo(
            @Parameter(description = "论文ID") @PathVariable Long paperId) {
        
        try {
            Long teacherId = UserBusinessInfoUtils.getCurrentUserId();
            log.info("获取论文预览信息 - 教师ID: {}, 论文ID: {}", teacherId, paperId);

            // 1. 验证论文存在性和权限
            PaperInfo paperInfo = paperInfoMapper.selectById(paperId);
            if (paperInfo == null) {
                return Result.error(404, "论文不存在");
            }

            // 2. 验证教师权限
            if (!paperInfo.getTeacherId().equals(teacherId)) {
                return Result.error(403, "无权限查看此论文");
            }

            // 3. 构造预览信息DTO
            PaperPreviewInfoDTO previewInfo = new PaperPreviewInfoDTO();
            previewInfo.setPaperId(paperInfo.getId());
            previewInfo.setPaperTitle(paperInfo.getPaperTitle());
            previewInfo.setStudentId(paperInfo.getStudentId());
            previewInfo.setPaperStatus(paperInfo.getPaperStatus());
            previewInfo.setSubmitTime(paperInfo.getSubmitTime());
            previewInfo.setFileId(paperInfo.getFileId());
            previewInfo.setHasFile(paperInfo.getFileId() != null);
            
            // 4. 补充文件详细信息（如果文件存在）
            if (previewInfo.getHasFile()) {
                try {
                    FileInfo fileInfo = fileService.getById(paperInfo.getFileId());
                    if (fileInfo != null) {
                        previewInfo.setFileName(fileInfo.getOriginalFilename());
                        previewInfo.setFileSize(fileInfo.getFileSize());
                        previewInfo.setFileSizeDesc(fileInfo.getFileSizeDesc());
                        previewInfo.setFileType(getFileExtension(fileInfo.getOriginalFilename()));
                        previewInfo.setWordCount(fileInfo.getWordCount());
                        previewInfo.setUploadTime(fileInfo.getUploadTime());
                    }
                } catch (Exception e) {
                    log.warn("获取文件详细信息失败: {}", e.getMessage());
                }
            }
            
            // 5. 获取学生姓名
            try {
                SysUser student = sysUserService.getById(paperInfo.getStudentId());
                if (student != null) {
                    previewInfo.setStudentName(student.getRealName());
                    previewInfo.setStudentNo(student.getUsername());
                }
            } catch (Exception e) {
                log.warn("获取学生信息失败: {}", e.getMessage());
            }
            
            log.info("获取论文预览信息成功 - 教师ID: {}, 论文ID: {}, 文件ID: {}", 
                    teacherId, paperId, paperInfo.getFileId());
            return Result.success("获取预览信息成功", previewInfo);

        } catch (Exception e) {
            log.error("获取论文预览信息失败 - 论文ID: {}", paperId, e);
            return Result.error(500, "获取预览信息失败: " + e.getMessage());
        }
    }

    /**
     * 论文预览信息DTO
     */
    public static class PaperPreviewInfoDTO {
        private Long paperId;
        private String paperTitle;
        private Long studentId;
        private String studentName;
        private String studentNo;
        private String paperStatus;
        private java.time.LocalDateTime submitTime;
        private Long fileId;
        private Boolean hasFile;
        private String fileName;
        private Long fileSize;
        private String fileSizeDesc;
        private String fileType;
        private Integer wordCount;
        private java.time.LocalDateTime uploadTime;


        public Long getPaperId() { return paperId; }
        public void setPaperId(Long paperId) { this.paperId = paperId; }
        
        public String getPaperTitle() { return paperTitle; }
        public void setPaperTitle(String paperTitle) { this.paperTitle = paperTitle; }
        
        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        
        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }
        
        public String getStudentNo() { return studentNo; }
        public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
        
        public String getPaperStatus() { return paperStatus; }
        public void setPaperStatus(String paperStatus) { this.paperStatus = paperStatus; }
        
        public java.time.LocalDateTime getSubmitTime() { return submitTime; }
        public void setSubmitTime(java.time.LocalDateTime submitTime) { this.submitTime = submitTime; }
        
        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        
        public Boolean getHasFile() { return hasFile; }
        public void setHasFile(Boolean hasFile) { this.hasFile = hasFile; }
        
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        
        public String getFileSizeDesc() { return fileSizeDesc; }
        public void setFileSizeDesc(String fileSizeDesc) { this.fileSizeDesc = fileSizeDesc; }
        
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        
        public Integer getWordCount() { return wordCount; }
        public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }
        
        public java.time.LocalDateTime getUploadTime() { return uploadTime; }
        public void setUploadTime(java.time.LocalDateTime uploadTime) { this.uploadTime = uploadTime; }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}