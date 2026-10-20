package com.abin.checkrepeatservice.common.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.VO.FileUploadResponse;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.service.FilePreviewService;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.common.service.Impl.FileValidationService;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
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

    // 安全修复（S2 配套）：kkFileView 服务端回调无法携带 JWT，改用绑定单文件、10 分钟有效的签名 URL
    @Autowired
    private com.abin.checkrepeatservice.common.jwt.PreviewSignedUrlService previewSignedUrlService;

    /**
     * 通用文件上传接口
     * 只负责文件上传，返回文件ID，业务参数通过其他接口传递
     */
    @PostMapping("/upload")
    public Result<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @Deprecated @RequestParam(required = false) Long userId
            ) {

        try {
            // 安全修复（P0）：文件归属人一律以 JWT 当前登录人为准，忽略前端传入的 userId，
            // 防止匿名/越权用户把文件挂到他人名下。该接口已由 SecurityConfig 要求登录。
            Long actualUserId = UserBusinessInfoUtils.getCurrentUserId();
            if (actualUserId == null) {
                return Result.error(ResultCode.NOT_LOGIN, "登录状态已过期，请重新登录");
            }

            log.info("文件上传请求 - 文件名：{}, 上传用户ID：{}",
                    file.getOriginalFilename(), actualUserId);

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

            // 4. 执行文件上传（归属人取 JWT 当前登录人，忽略前端 userId）
            Long fileId = fileService.uploadFile(file, actualUserId);

            // 5. 获取文件信息
            FileInfo fileInfo = fileService.getById(fileId);
            FileUploadResponse response = buildFileUploadResponse(fileInfo, false);

            log.info("文件上传成功 - 文件 ID: {}, 文件名：{}", fileId, file.getOriginalFilename());
            return Result.success("文件上传成功", response);

        } catch (Exception e) {
            log.error("文件上传失败 - 文件名：{}", file.getOriginalFilename(), e);
            return Result.error(ResultCode.SYSTEM_ERROR, "文件上传失败：" + "系统异常，请稍后重试");
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
            // 安全修复（P0）：文件元数据归属校验，修复 fileId 可枚举导致的信息泄露
            if (!canAccessFile(fileInfo)) {
                log.warn("文件信息越权访问拦截 - fileId: {}, userId: {}",
                        fileId, UserBusinessInfoUtils.getCurrentUserId());
                return Result.error(ResultCode.PERMISSION_NO_ACCESS, "无权访问该文件");
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

            // 安全修复（P0）：下载归属校验，修复仅凭可枚举 fileId 即可下载任意论文/附件的 IDOR
            if (!canAccessFile(fileInfo)) {
                log.warn("文件下载越权访问拦截 - fileId: {}, userId: {}",
                        fileId, UserBusinessInfoUtils.getCurrentUserId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            return serveInlineFile(fileInfo, fileName);

        } catch (Exception e) {
            log.error("文件下载异常 - fileId: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * 预览签名取文件接口（仅 kkFileView 服务端回调使用，SecurityConfig 单独 permitAll）。
     * 访问凭据是绑定 fileId、10 分钟有效的 HMAC 令牌 pt；鉴权入口 smartPreview
     * 仍要求登录并通过归属校验，因此这里不构成新的匿名 IDOR 面。
     */
    @GetMapping("/preview-fetch/{fileId}/{fileName}")
    public ResponseEntity<Resource> previewFetch(
            @PathVariable Long fileId,
            @PathVariable(required = false) String fileName,
            @RequestParam("pt") String previewToken) {

        if (!previewSignedUrlService.verify(fileId, previewToken)) {
            log.warn("预览签名校验失败 - fileId: {}", fileId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        FileInfo fileInfo = fileService.getById(fileId);
        if (fileInfo == null || fileInfo.getStoragePath() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        log.info("预览签名取文件 - fileId: {}", fileId);
        return serveInlineFile(fileInfo, fileName);
    }

    /**
     * 报告预览签名取文件接口（仅 kkFileView 服务端回调使用，SecurityConfig 单独 permitAll）。
     * 与 preview-fetch 同构：凭据是绑定 reportId、10 分钟有效的 HMAC 令牌 pt；
     * 鉴权入口 smartPreviewReport 仍要求登录，故不构成新的匿名 IDOR 面。
     */
    @GetMapping("/preview-fetch-report/{reportId}/{fileName}")
    public ResponseEntity<Resource> previewReportFetch(
            @PathVariable Long reportId,
            @PathVariable(required = false) String fileName,
            @RequestParam("pt") String previewToken) {

        if (!previewSignedUrlService.verify("report:" + reportId, previewToken)) {
            log.warn("报告预览签名校验失败 - reportId: {}", reportId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }
        return filePreviewService.serveReport(reportId, fileName);
    }

    /**
     * 取预览地址（登录态入口）。返回 {mode, url}：
     * <ul>
     *   <li>direct     —— 图片/PDF/文本等直出类文件，url 是带签名令牌的文件流地址；</li>
     *   <li>kkfileview —— office 文档，url 是浏览器可达的 kkFileView 预览页地址。</li>
     * </ul>
     *
     * <p>前端必须把这个地址直接交给 iframe 加载，不要用 axios 拉成 blob：
     * kkFileView 返回的是整张 HTML 预览页，包成 blob 后文档基址变成 {@code blob:...}，
     * 页内 js/css/图片的相对路径全部 404（表现为整页破图）。</p>
     */
    @GetMapping("/preview-url/{fileId}")
    public Result<Map<String, String>> getPreviewUrl(@PathVariable Long fileId) {
        try {
            return Result.success(filePreviewService.getPreviewInfo(fileId));
        } catch (IllegalArgumentException e) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("获取预览地址失败 - fileId: {}", fileId, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "获取预览地址失败");
        }
    }

    /**
     * 以 inline 方式流式返回本地文件（下载与签名预览共用）
     */
    private ResponseEntity<Resource> serveInlineFile(FileInfo fileInfo, String fileName) {
        String actualFileName = (fileName != null && !fileName.trim().isEmpty()) ?
                fileName : fileInfo.getOriginalFilename();

        String fileStoragePath = uploadBasePath + fileInfo.getStoragePath();
        File file = new File(fileStoragePath);

        log.info("文件读取请求 - fileId: {}, 文件名: {}, 存储路径: {}",
                fileInfo.getId(), actualFileName, fileStoragePath);

        if (!file.exists()) {
            log.error("文件不存在 - fileId: {}, 路径: {}", fileInfo.getId(), fileStoragePath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        Resource resource = new FileSystemResource(file);

        String contentType = getContentType(file, actualFileName);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        String encodedFileName = URLEncoder.encode(actualFileName, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + encodedFileName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(resource);
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
        // 安全修复（P0）：预览同样做文件归属校验
        FileInfo fileInfo = fileService.getById(fileId);
        if (fileInfo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("文件不存在");
        }
        if (!canAccessFile(fileInfo)) {
            log.warn("文件预览越权访问拦截 - fileId: {}, userId: {}",
                    fileId, UserBusinessInfoUtils.getCurrentUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权访问该文件");
        }
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
        // 安全修复（P0）：报告预览做论文归属校验（学生本人 / 指导教师 / 管理员）
        Long paperIdLong;
        try {
            paperIdLong = Long.parseLong(paperId);
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("论文ID格式非法");
        }
        PaperInfo paper = paperInfoMapper.selectById(paperIdLong);
        if (paper == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("论文不存在");
        }
        if (!canAccessPaper(paper)) {
            log.warn("报告预览越权访问拦截 - paperId: {}, userId: {}",
                    paperId, UserBusinessInfoUtils.getCurrentUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("无权访问该报告");
        }
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

            // 安全修复（P0）：原注释写着「验证权限」但只验存在性，任何人知道 reportId
            // （雪花 ID 随业务接口暴露）即可下载任意查重报告 PDF。补真实归属校验：
            // 学生本人 / 该论文指导教师 / 管理员（含超管）
            if (!canAccessPaper(paper)) {
                log.warn("报告下载越权访问拦截 - reportId: {}, paperId: {}, userId: {}",
                        reportId, paper.getId(), UserBusinessInfoUtils.getCurrentUserId());
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

    // ==================== 资源归属校验（安全修复 P0，纵深防御） ====================

    /**
     * 判断当前登录用户能否访问指定文件：
     * 文件上传人本人、教师（评阅/指导工作需要访问学生论文与附件）、管理员（含超管）放行。
     */
    private boolean canAccessFile(FileInfo fileInfo) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        if (currentUserId == null) {
            return false;
        }
        if (UserBusinessInfoUtils.isAdmin()) {
            return true;
        }
        if (UserBusinessInfoUtils.isTeacher()) {
            return true;
        }
        return fileInfo.getUploadUserId() != null
                && fileInfo.getUploadUserId().equals(currentUserId.toString());
    }

    /**
     * 判断当前登录用户能否访问指定论文相关资源（查重报告等）：
     * 论文所属学生本人、该论文的指导教师、管理员（含超管）放行。
     * 教师不做全量放行——只能访问自己名下指导的论文，避免教师间越权。
     */
    private boolean canAccessPaper(PaperInfo paper) {
        Long currentUserId = UserBusinessInfoUtils.getCurrentUserId();
        if (currentUserId == null) {
            return false;
        }
        if (UserBusinessInfoUtils.isAdmin()) {
            return true;
        }
        if (currentUserId.equals(paper.getStudentId())) {
            return true;
        }
        return UserBusinessInfoUtils.isTeacher()
                && paper.getTeacherId() != null
                && currentUserId.equals(paper.getTeacherId());
    }
}