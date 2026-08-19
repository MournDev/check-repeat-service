package com.abin.checkrepeatservice.common.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.VO.FileUploadResponse;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.FilePreviewService;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.common.service.Impl.FileValidationService;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一文件上传控制器
 * 负责所有文件的通用上传，不包含具体业务逻辑
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
public class FileUploadController {

    @Autowired
    private FileService fileService;

    @Autowired
    private FileValidationService fileValidationService;

    @Autowired
    private PaperInfoMapper paperInfoMapper;

    @Autowired
    private CheckReportMapper checkReportMapper;

    @Autowired
    private CheckTaskMapper checkTaskMapper;

    @Value("${file.upload.base-path}")
    private String uploadBasePath;

    @Autowired
    private FilePreviewService filePreviewService;

    /**
     * 通用文件上传接口
     * 只负责文件上传，返回文件ID，业务参数通过其他接口传递
     */
    @PostMapping("/upload")
    public Result<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long userId
            ) {

        try {
            log.info("文件上传请求 - 文件名：{},",
                    file.getOriginalFilename());

            // 1. 文件基础校验
            Result<Void> validationResult = fileValidationService.validateFile(file);
            if (!validationResult.isSuccess()) {
                return Result.error(ResultCode.PARAM_ERROR, validationResult.getMessage());
            }

            // 2. 获取文件字节数组并计算文件 MD5（用于秒传和校验）
            byte[] fileBytes = file.getBytes();
            String fileMd5 = fileService.calculateFileMd5FromBytes(fileBytes);


            // 3. 检查是否已存在相同文件（秒传功能）
            FileInfo existingFile = fileService.getByMd5(fileMd5);
            if (existingFile != null) {
                log.info("文件已存在，使用秒传 - 文件 ID: {}", existingFile.getId());
                FileUploadResponse response = buildFileUploadResponse(existingFile, true);
                return Result.success("文件上传成功（秒传）", response);
            }

            // 4. 执行文件上传
            Long fileId = fileService.uploadFile(file, userId);

            // 5. 获取文件信息
            FileInfo fileInfo = fileService.getById(fileId);
            FileUploadResponse response = buildFileUploadResponse(fileInfo, false);

            log.info("文件上传成功 - 文件 ID: {}, 文件名：{}", fileId, file.getOriginalFilename());
            return Result.success("文件上传成功", response);

        } catch (Exception e) {
            log.error("文件上传失败 - 文件名：{}", file.getOriginalFilename(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 根据MD5查询文件（用于前端秒传检查）
     */
    @GetMapping("/check")
    public Result<FileUploadResponse> checkFileByMd5(@RequestParam String md5) {
        try {
            FileInfo fileInfo = fileService.getByMd5(md5);
            if (fileInfo != null) {
                FileUploadResponse response = buildFileUploadResponse(fileInfo, true);
                return Result.success("文件已存在", response);
            } else {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "文件不存在");
            }
        } catch (Exception e) {
            log.error("文件检查失败 - MD5: {}", md5, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "文件检查失败");
        }
    }

    /**
     * 获取文件信息
     */
    @GetMapping("/info")
    public Result<FileInfo> getFileInfo(@RequestParam Long fileId) {
        try {
            FileInfo fileInfo = fileService.getById(fileId);
            if (fileInfo == null) {
                return Result.error(ResultCode.RESOURCE_NOT_FOUND, "文件不存在");
            }
            return Result.success(fileInfo);
        } catch (Exception e) {
            log.error("获取文件信息失败 - 文件ID: {}", fileId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取文件信息失败");
        }
    }

    /**
     * 文件下载接口
     */
    @GetMapping("/download/{fileId}/{fileName}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId,
            @PathVariable(required = false) String fileName,
            HttpServletRequest request) {

        try {
            // 1. 获取文件信息
            FileInfo fileInfo = fileService.getById(fileId);
            if (fileInfo == null || fileInfo.getStoragePath() == null) {
                log.error("文件不存在或未找到存储路径 - fileId: {}", fileId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            // 2. 使用传入的文件名或数据库中的文件名
            String actualFileName = (fileName != null && !fileName.trim().isEmpty()) ?
                    fileName : fileInfo.getOriginalFilename();

            String fileStoragePath = uploadBasePath + fileInfo.getStoragePath();
            File file = new File(fileStoragePath);

            // 3. 日志打印
            log.info("文件下载请求 - fileId: {}, 文件名: {}, 存储路径: {}",
                    fileId, actualFileName, fileStoragePath);

            if (!file.exists()) {
                log.error("文件不存在 - fileId: {}, 路径: {}", fileId, fileStoragePath);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            // 4. 构建Resource对象
            Resource resource = new FileSystemResource(file);

            // 5. 获取Content-Type
            String contentType = getContentType(file, actualFileName);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // 6. 设置响应头
            String encodedFileName = URLEncoder.encode(actualFileName, StandardCharsets.UTF_8);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + encodedFileName + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);

        } catch (Exception e) {
            log.error("文件下载异常 - fileId: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    private String getContentType(File file, String fileName) {
        try {
            String contentType = Files.probeContentType(file.toPath());
            if (contentType != null) {
                return contentType;
            }
        } catch (IOException e) {
            log.warn("无法探测文件类型: {}", e.getMessage());
        }

        // 根据文件扩展名判断
        String extension = getFileExtension(fileName);
        Map<String, String> mimeTypes = new HashMap<String, String>() {{
            put("pdf", "application/pdf");
            put("doc", "application/msword");
            put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            put("xls", "application/vnd.ms-excel");
            put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            put("ppt", "application/vnd.ms-powerpoint");
            put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
            put("txt", "text/plain");
            put("jpg", "image/jpeg");
            put("jpeg", "image/jpeg");
            put("png", "image/png");
            put("gif", "image/gif");
        }};

        return mimeTypes.getOrDefault(extension.toLowerCase(), "application/octet-stream");
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
    /**
     * KKFileView在线预览接口（通过URL）
     * 保持原有接口不变
     */
    @GetMapping("/onlinePreview")
    public ResponseEntity<byte[]> onlinePreview(@RequestParam String url) {
        log.info("接收KKFileView代理预览请求 - url: {}", url);
        return filePreviewService.onlinePreviewByUrl(url);
    }

    /**
     * 智能预览接口（推荐使用）
     * 自动根据文件类型选择最佳预览方式
     */
    @GetMapping("/smartPreview")
    public ResponseEntity<?> smartPreview(@RequestParam Long fileId) {
        log.info("接收智能预览请求 - fileId: {}", fileId);
        return filePreviewService.smartPreview(fileId);
    }
    /**
     * 构建文件上传响应
     */
    private FileUploadResponse buildFileUploadResponse(FileInfo fileInfo, boolean isFastUpload) {
        FileUploadResponse response = new FileUploadResponse();
        response.setFileId(fileInfo.getId());
        response.setFileName(fileInfo.getOriginalFilename());
        response.setFileSize(fileInfo.getFileSize());
        response.setFileSizeDesc(fileInfo.getFileSizeDesc());
        response.setMd5(fileInfo.getMd5());
        response.setUploadTime(fileInfo.getUploadTime());
        response.setFastUpload(isFastUpload);
        return response;
    }

    /**
     * 智能预览报告接口
     * 自动根据文件类型选择最佳预览方式
     */
    @GetMapping("/smartPreviewReport")
    public ResponseEntity<?> smartPreviewReport(@RequestParam String paperId) {
        log.info("接收智能预览请求 - paperId: {}", paperId);
        return filePreviewService.smartPreviewReport(paperId);
    }

    /**
     * 报告文件下载接口
     */
    @GetMapping("/downloadReport/{reportId}")
    public ResponseEntity<Resource> downloadReport(
            @PathVariable Long reportId,
            HttpServletResponse response) {

        try {
            // 1. 查询报告
            CheckReport checkReport = checkReportMapper.selectById(reportId);
            if (checkReport == null) {
                return ResponseEntity.notFound().build();
            }

            // 2. 验证权限
            CheckTask checkTask = checkTaskMapper.selectById(checkReport.getTaskId());
            if (checkTask == null) {
                return ResponseEntity.notFound().build();
            }

            PaperInfo paper = paperInfoMapper.selectById(checkTask.getPaperId());
            if (paper == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // 3. 检查文件
            String reportPath = checkReport.getReportPath();
            File file = new File(reportPath);

            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            // 4. 准备文件流
            Path path = Paths.get(file.getAbsolutePath());
            Resource resource = new InputStreamResource(Files.newInputStream(path));

            // 5. 设置响应头
            String filename = "相似度报告_" + paper.getPaperTitle() + ".pdf";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + encodedFilename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(file.length())
                    .body(resource);

        } catch (Exception e) {
            log.error("下载报告失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}