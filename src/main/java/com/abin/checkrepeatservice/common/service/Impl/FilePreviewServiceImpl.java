package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.component.ReportPathResolver;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.jwt.PreviewSignedUrlService;
import com.abin.checkrepeatservice.common.service.FilePreviewService;
import com.abin.checkrepeatservice.common.service.FileService;
import com.abin.checkrepeatservice.common.service.KkFileViewHealthService;
import com.abin.checkrepeatservice.common.utils.UserBusinessInfoUtils;
import com.abin.checkrepeatservice.pojo.entity.CheckReport;
import com.abin.checkrepeatservice.pojo.entity.CheckTask;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import com.abin.checkrepeatservice.student.mapper.CheckReportMapper;
import com.abin.checkrepeatservice.student.mapper.CheckTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@Service
@Slf4j
public class FilePreviewServiceImpl implements FilePreviewService {

    @Autowired
    private FileService fileService;

    @Autowired
    private CheckTaskMapper checkTaskMapper;

    @Autowired
    private CheckReportMapper checkReportMapper;

    // 安全修复（S2 配套）：kkFileView 回调走签名取文件端点
    @Autowired
    private PreviewSignedUrlService previewSignedUrlService;

    @Value("${app.host:192.168.1.149}")
    private String serverHost;

    /** 回源协议：内网容器间默认 http，可配置为 https（不再硬编码） */
    @Value("${app.scheme:http}")
    private String serverScheme;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${kkfileview.base-url}")
    private String kkfileviewUrl;

    @Value("${server.servlet.context-path}")
    private String appContext;

    // 报告路径统一解析：库里存相对路径，按配置根目录解析到绝对路径
    @Autowired
    private ReportPathResolver reportPathResolver;

    // kkFileView 可用性缓存：请求线程只读，不发起网络探测
    @Autowired
    private KkFileViewHealthService kkFileViewHealthService;

    @Override
    public ResponseEntity<Resource> directPreview(Long fileId) {
        try {
            FileInfo fileInfo = fileService.getById(fileId);
            if (fileInfo == null) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "文件不存在或已被清理", null);
            }

            File file = fileService.resolveStoredFile(fileInfo.getStoragePath());
            if (file == null || !file.exists()) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "文件不存在或已被清理", null);
            }

            Resource resource = new FileSystemResource(file);
            String contentType = getContentType(file, fileInfo.getOriginalFilename());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + URLEncoder.encode(fileInfo.getOriginalFilename(), StandardCharsets.UTF_8) + "\"")
                    .body(resource);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("直接预览失败 - fileId: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * 探测文件类型。
     *
     * <p>{@link Files#probeContentType} 依赖宿主机的 /etc/mime.types，精简镜像里
     * 常年返回 null —— PDF 因此丢掉 application/pdf，浏览器内置 PDF 阅读器会直接
     * 拒绝加载（表现为「该文件可能已被移至别处、修改或删除。」）。故按扩展名兜底，
     * 与 FileUploadController 里的同名方法保持一致。</p>
     */
    private String getContentType(File file, String originalFilename) {
        try {
            String contentType = Files.probeContentType(file.toPath());
            if (contentType != null) {
                return contentType;
            }
        } catch (IOException e) {
            log.warn("无法探测文件类型: {}", e.getMessage());
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        Map<String, String> mimeTypes = Map.ofEntries(
                Map.entry("pdf", "application/pdf"),
                Map.entry("doc", "application/msword"),
                Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                Map.entry("xls", "application/vnd.ms-excel"),
                Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                Map.entry("ppt", "application/vnd.ms-powerpoint"),
                Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                Map.entry("txt", "text/plain"),
                Map.entry("html", "text/html"),
                Map.entry("htm", "text/html"),
                Map.entry("xml", "application/xml"),
                Map.entry("json", "application/json"),
                Map.entry("jpg", "image/jpeg"),
                Map.entry("jpeg", "image/jpeg"),
                Map.entry("png", "image/png"),
                Map.entry("gif", "image/gif"),
                Map.entry("bmp", "image/bmp"),
                Map.entry("webp", "image/webp"),
                Map.entry("svg", "image/svg+xml"));

        return mimeTypes.getOrDefault(extension, "application/octet-stream");
    }

    @Autowired
    private com.abin.checkrepeatservice.common.utils.SafePreviewUrlValidator safePreviewUrlValidator;

    @Override
    public ResponseEntity<byte[]> onlinePreviewByUrl(String url) {
        log.info("执行KKFileView代理预览 - url: {}", url);

        try {
            safePreviewUrlValidator.validate(url);
            // 直接代理到KKFileView
            return proxyToKKFileViewForUrl(url);
        } catch (IllegalArgumentException e) {
            log.warn("在线预览 URL 校验失败: {}, url: {}", e.getMessage(), url);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("KKFileView代理预览失败 - url: {}", url, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("代理预览失败".getBytes(StandardCharsets.UTF_8));
        }
    }
    /**
     * 代理请求到KKFileView（基于URL）
     * KKFileView要求URL中必须包含文件名以便正确识别文件类型
     */
    private ResponseEntity<byte[]> proxyToKKFileViewForUrl(String url) {
        try {
            // 1. Base64编码URL（标准 Base64 + percent 编码，见 encodePreviewUrlParam）
            String encodedUrl = encodePreviewUrlParam(url);

            // 2. 构建KKFileView预览URL
            String kkFileViewPreviewUrl = String.format("%s/onlinePreview?url=%s",
                    kkfileviewUrl, encodedUrl);

            log.info("KKFileView代理预览URL: {}", kkFileViewPreviewUrl);

            // 3. 重定向到KKFileView
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(kkFileViewPreviewUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);

        } catch (Exception e) {
            log.error("代理到KKFileView失败 - url: {}", url, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("代理预览失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }


    @Override
    public ResponseEntity<?> smartPreview(Long fileId) {
        try {
            // 1. 获取文件信息
            FileInfo fileInfo = fileService.getById(fileId);
            if (fileInfo == null) {
                log.error("文件不存在 - fileId: {}", fileId);
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "文件不存在或已被清理", null);
            }

            String fileName = fileInfo.getOriginalFilename();
            String fileExtension = getFileExtension(fileName).toLowerCase();

            log.info("智能预览 - fileId: {}, 文件名: {}, 扩展名: {}",
                    fileId, fileName, fileExtension);

            // 2. 根据文件类型选择预览方式
            // 图片类型：直接返回文件流
            if (isImageFile(fileExtension)) {
                log.info("图片文件直接预览 - fileId: {}", fileId);
                return directPreview(fileId);
            }

            // 可直接预览的文档类型：PDF、文本
            if (isDirectPreviewFile(fileExtension)) {
                log.info("可直接预览的文档 - fileId: {}", fileId);
                return directPreview(fileId);
            }

            // Office文档类型：使用KKFileView
            if (isOfficeFile(fileExtension)) {
                log.info("Office文档使用KKFileView预览 - fileId: {}", fileId);
                return proxyToKKFileView(fileId, fileInfo);
            }

            // 其他类型：尝试直接预览
            log.info("其他类型文件尝试直接预览 - fileId: {}", fileId);
            return directPreview(fileId);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("智能预览失败 - fileId: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("预览失败: " + e.getMessage());
        }
    }

    /**
     * 取文件预览地址（登录态入口）。前端把返回的 url 直接交给 iframe 加载。
     *
     * <p>为什么不直接复用 {@link #smartPreview}：那条路对 office 文档 302 到 kkFileView，
     * 前端若用 axios 跟着 302 把响应拉成 blob 再塞进 iframe，文档基址会变成
     * {@code blob:...}，kkFileView 预览页里的 js/css/图片等相对资源全部 404
     * （表现为整页破图）。改成这里只返回地址、由浏览器自己请求，页面就以真实地址
     * 为基址渲染，相对资源随之恢复正常。</p>
     */
    @Override
    public Map<String, String> getPreviewInfo(Long fileId) {
        FileInfo fileInfo = fileService.getById(fileId);
        if (fileInfo == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "文件不存在或已被清理", null);
        }
        // 签发预览地址前先校验物理文件，避免前端拿到 200 的地址后再看到 404 白屏
        if (fileInfo.getStoragePath() == null || fileInfo.getStoragePath().isBlank()) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "文件不存在或已被清理", null);
        }
        File physicalFile = fileService.resolveStoredFile(fileInfo.getStoragePath());
        if (physicalFile == null || !physicalFile.exists()) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "文件不存在或已被清理", null);
        }

        String fileExtension = getFileExtension(fileInfo.getOriginalFilename()).toLowerCase();
        // 令牌绑定签发时的登录用户（P0）：归属校验已在 Controller 完成，这里取用户仅用于签名
        Long userId = requireCurrentUserId();

        // office 文档：交给 kkFileView 转换，返回其浏览器可达的预览页地址
        if (isOfficeFile(fileExtension)) {
            return Map.of("mode", "kkfileview",
                    "url", buildKKFileViewUrl(fileId, fileInfo, userId));
        }

        // 图片 / PDF / 文本等由后端直出，签名取文件地址浏览器可直接打开（不带 JWT 也放行）
        return Map.of("mode", "direct", "url", buildSignedFetchUrl(fileId, fileInfo, userId));
    }

    /**
     * 取当前登录用户 ID；签发预览令牌必须绑定用户，取不到即视为签发失败。
     */
    private Long requireCurrentUserId() {
        Long userId = UserBusinessInfoUtils.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("签发预览令牌失败：当前登录用户为空");
        }
        return userId;
    }

    /**
     * 直出类文件的预览地址：走匿名可达的签名取文件端点。
     *
     * <p>与 kkFileView 回源地址不同，这里的请求方是浏览器、不会二次编码，
     * 因此可以带原始文件名（只做常规百分号编码）。</p>
     */
    private String buildSignedFetchUrl(Long fileId, FileInfo fileInfo, Long userId) {
        String originalFilename = fileInfo.getOriginalFilename() == null
                ? "preview"
                : fileInfo.getOriginalFilename();
        return String.format("%s/api/file/preview-fetch/%s/%s?pt=%s",
                appContext,
                fileId,
                URLEncoder.encode(originalFilename, StandardCharsets.UTF_8),
                previewSignedUrlService.sign(fileId, userId));
    }

    @Override
    public ResponseEntity<?> smartPreviewReport(String paperId) {
        try {
            // 1. 查找最新的已完成查重任务
            LambdaQueryWrapper<CheckTask> taskQueryWrapper = new LambdaQueryWrapper<>();
            taskQueryWrapper.eq(CheckTask::getPaperId, paperId)
                    .eq(CheckTask::getIsDeleted, 0)
                    .eq(CheckTask::getCheckStatus, "completed")
                    .orderByDesc(CheckTask::getCreateTime)
                    .last("LIMIT 1");

            CheckTask checkTask = checkTaskMapper.selectOne(taskQueryWrapper);
            if (checkTask == null) {
                log.error("未找到已完成的查重任务 - paperId: {}", paperId);
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "查重报告不存在或已被清理", null);
            }

            // 2. 获取查重报告
            CheckReport checkReport = checkReportMapper.selectById(checkTask.getReportId());
            if (checkReport == null) {
                log.error("查重报告不存在 - reportId: {}", checkTask.getReportId());
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "查重报告不存在或已被清理", null);
            }

            // 3. 获取报告文件信息（report_path 可能是存量绝对路径或新相对路径）
            String reportPath = checkReport.getReportPath();
            File reportFile = reportPathResolver.toFile(reportPath);
            if (reportFile == null || !reportFile.exists()) {
                log.error("报告文件不存在 - path: {}", reportPath);
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "报告文件不存在或已被清理", null);
            }

            String fileName = reportFile.getName();
            String fileExtension = getFileExtension(fileName).toLowerCase();

            log.info("智能预览查重报告 - paperId: {}, 文件名: {}, 扩展名: {}",
                    paperId, fileName, fileExtension);

            // 4. 根据文件类型选择预览方式
            // 图片类型：直接返回文件流
            if (isImageFile(fileExtension)) {
                log.info("图片报告直接预览 - paperId: {}", paperId);
                return directPreviewReport(reportPath, fileName);
            }

            // 可直接预览的文档类型：PDF、文本
            if (isDirectPreviewFile(fileExtension)) {
                log.info("可直接预览的报告文档 - paperId: {}", paperId);
                return directPreviewReport(reportPath, fileName);
            }

            // Office 类型报告：交给 kkFileView 转换。回源地址必须是用
            // kkFileView 容器可达的 HTTP 端点（不能是 file://宿主路径，见
            // proxyReportToKKFileView 注释）；当前报告均由 pdfReportGenerator
            // 统一生成为 PDF，走不到这里，保留分支以支持后续其它报告类型
            if (isOfficeFile(fileExtension)) {
                log.info("Office报告文档使用KKFileView预览 - paperId: {}", paperId);
                return proxyReportToKKFileView(checkReport.getId(), fileName);
            }

            // 其他类型：尝试直接预览
            log.info("其他类型报告文件尝试直接预览 - paperId: {}", paperId);
            return directPreviewReport(reportPath, fileName);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("智能预览查重报告失败 - paperId: {}", paperId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("预览失败: " + e.getMessage());
        }
    }

    /**
     * 直接预览报告文件
     */
    private ResponseEntity<Resource> directPreviewReport(String reportPath, String fileName) {
        try {
            File file = reportPathResolver.toFile(reportPath);
            if (file == null || !file.exists()) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "报告文件不存在或已被清理", null);
            }

            Resource resource = new FileSystemResource(file);
            String contentType = getContentType(file, fileName);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"")
                    .body(resource);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("直接预览报告失败 - path: {}", reportPath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * 按报告ID流式返回报告文件（仅 kkFileView 服务端回调使用，签名在 Controller 校验）
     */
    @Override
    public ResponseEntity<Resource> serveReport(Long reportId, String fileName) {
        CheckReport checkReport = checkReportMapper.selectById(reportId);
        if (checkReport == null || Integer.valueOf(1).equals(checkReport.getIsDeleted())
                || checkReport.getReportPath() == null) {
            log.warn("报告不存在或已删除 - reportId: {}", reportId);
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "报告不存在或已被清理", null);
        }
        String reportPath = checkReport.getReportPath();
        File file = reportPathResolver.toFile(reportPath);
        if (file == null || !file.exists()) {
            log.error("报告文件不存在 - reportId: {}, path: {}", reportId, reportPath);
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "报告文件不存在或已被清理", null);
        }
        log.info("签名取报告文件 - reportId: {}", reportId);
        String actualFileName = (fileName != null && !fileName.trim().isEmpty()) ? fileName : file.getName();
        return directPreviewReport(reportPath, actualFileName);
    }

    /**
     * 代理报告到 KKFileView 预览
     *
     * <p>回源地址必须是 kkFileView 容器能访问到的 HTTP 端点：原实现传 {@code file://宿主路径}，
     * 但 kkFileView 与后端是两个独立容器，读不到对方的文件系统。这里复用文件预览的思路——
     * 后端签发绑定 reportId、10 分钟有效的 HMAC 令牌，让 kkFileView 回调
     * preview-fetch-report 端点取字节。</p>
     */
    private ResponseEntity<?> proxyReportToKKFileView(Long reportId, String fileName) {
        try {
            Long userId = requireCurrentUserId();
            // 文件名段只保留 ASCII 安全字符，理由同 proxyToKKFileView（kkFileView 会再编码一次）
            String fileExtension = getFileExtension(fileName).toLowerCase();
            String callbackFileName = (fileName != null && fileName.matches("[A-Za-z0-9._-]+"))
                    ? fileName
                    : (fileExtension.isEmpty() ? "preview" : "preview." + fileExtension);
            String fileUrl = String.format("%s://%s:%s%s/api/file/preview-fetch-report/%s/%s?pt=%s",
                    serverScheme,
                    serverHost,
                    serverPort,
                    appContext,
                    reportId,
                    callbackFileName,
                    previewSignedUrlService.sign("report:" + reportId, userId));

            // 安全修复（P0）：回源 URL 含签名令牌，禁止落日志；只留定位所需的非敏感字段
            log.info("构建KKFileView报告回源地址 - reportId: {}, host: {}, port: {}",
                    reportId, serverHost, serverPort);

            String encodedUrl = encodePreviewUrlParam(fileUrl);

            String kkFileViewPreviewUrl = String.format("%s/onlinePreview?url=%s",
                    kkfileviewUrl, encodedUrl);

            log.info("KKFileView报告预览地址已生成 - reportId: {}, base: {}", reportId, kkfileviewUrl);

            // 性能修复（H8）：只读后台缓存的健康状态，请求线程不再同步 ping /index
            if (!kkFileViewHealthService.isAvailable()) {
                log.warn("KKFileView缓存状态为不可用，报告预览降级 - reportId: {}", reportId);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("预览服务暂时不可用");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(kkFileViewPreviewUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
            log.error("代理报告到KKFileView失败 - reportId: {}", reportId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("预览失败: " + e.getMessage());
        }
    }

    /**
     * 代理请求到 KKFileView
     */
    private ResponseEntity<?> proxyToKKFileView(Long fileId, FileInfo fileInfo) {
        // 先取用户：签发令牌必须绑定用户，取不到直接失败（归属校验已在 Controller 完成）
        Long userId = requireCurrentUserId();
        try {
            // 1. 构建 kkFileView 预览页地址（含绑定 fileId、10 分钟有效的回源签名）
            String kkFileViewPreviewUrl = buildKKFileViewUrl(fileId, fileInfo, userId);

            // 2. 只读后台缓存的健康状态，不再在请求线程里同步探测（最多 30s）
            if (!kkFileViewHealthService.isAvailable()) {
                log.warn("KKFileView缓存状态为不可用，文件预览降级为下载 - fileId: {}", fileId);
                return fallbackToDownload(fileInfo, userId);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(kkFileViewPreviewUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
            log.error("代理到KKFileView失败 - fileId: {}", fileId, e);
            return fallbackToDownload(fileInfo, userId);
        }
    }

    /**
     * 构建 kkFileView 预览页地址（浏览器最终要打开的地址）。
     */
    private String buildKKFileViewUrl(Long fileId, FileInfo fileInfo, Long userId) {
        String fileUrl = String.format("%s://%s:%s%s/api/file/preview-fetch/%s/%s?pt=%s",
                serverScheme,
                serverHost,
                serverPort,
                appContext,
                fileId,
                safePreviewFileName(fileInfo.getOriginalFilename()),
                previewSignedUrlService.sign(fileId, userId));

        // 安全修复（P0）：回源 URL 含签名令牌，禁止落日志；只留定位所需的非敏感字段
        log.info("构建KKFileView回源地址 - fileId: {}, host: {}, port: {}",
                fileId, serverHost, serverPort);

        // Base64 编码（标准 Base64 + percent 编码，见 encodePreviewUrlParam）
        String encodedUrl = encodePreviewUrlParam(fileUrl);
        String kkFileViewPreviewUrl = String.format("%s/onlinePreview?url=%s",
                kkfileviewUrl, encodedUrl);

        log.info("KKFileView预览地址已生成 - fileId: {}, base: {}", fileId, kkfileviewUrl);
        return kkFileViewPreviewUrl;
    }

    /**
     * kkFileView 回源路径里的文件名段：只保留 ASCII 安全字符。
     *
     * <p>kkFileView 会对该 URL 再做一次百分号编码，若这里预先编码中文名，会形成
     * %25xx 双重编码，被 Spring Security 的 StrictHttpFirewall 以 400 拒绝
     * （请求根本到不了后端）。非 ASCII 名退回 preview.&lt;扩展名&gt;，
     * 扩展名用于 kkFileView 选择转换器。</p>
     */
    private String safePreviewFileName(String originalFilename) {
        String fileExtension = getFileExtension(originalFilename).toLowerCase();
        return (originalFilename != null && originalFilename.matches("[A-Za-z0-9._-]+"))
                ? originalFilename
                : (fileExtension.isEmpty() ? "preview" : "preview." + fileExtension);
    }

    private ResponseEntity<?> fallbackToDownload(FileInfo fileInfo, Long userId) {
        // 降级方案：返回下载链接（签名端点：浏览器新标签打开不带 JWT，用短期签名令牌授权）
        String downloadUrl = String.format("/api/file/preview-fetch/%s/%s?pt=%s",
                fileInfo.getId(),
                URLEncoder.encode(fileInfo.getOriginalFilename(), StandardCharsets.UTF_8),
                previewSignedUrlService.sign(fileInfo.getId(), userId));

        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "success", false,
                        "message", "预览服务暂时不可用，请下载文件查看",
                        "downloadUrl", downloadUrl
                ));
    }

    // 工具方法
    private boolean isImageFile(String extension) {
        return Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg").contains(extension);
    }

    private boolean isDirectPreviewFile(String extension) {
        return Arrays.asList("pdf", "txt", "html", "htm", "xml", "json").contains(extension);
    }

    private boolean isOfficeFile(String extension) {
        return Arrays.asList("doc", "docx", "xls", "xlsx", "ppt", "pptx").contains(extension);
    }

    /**
     * 构建 kkFileView 的 url 查询参数值。
     *
     * <p>两个约束缺一不可：</p>
     * <ol>
     *   <li>必须用<b>标准</b> Base64：kkFileView 的 TrustHostFilter 用 Spring 的
     *       {@code Base64Utils.decodeFromString} 解析该参数（标准解码器），
     *       URL-safe 编码产生的 {@code -}/{@code _} 会抛
     *       {@code Illegal base64 character 5f}，表现为 kkFileView 返回 500；</li>
     *   <li>标准 Base64 含 {@code +}/{@code /}/{@code =}，直接放进查询串会被服务端
     *       按表单规则解析（{@code +} 被当空格），因此还要再 percent 编码一次，
     *       等价于 kkFileView 文档示例里的 {@code encodeURIComponent(base64Encode(url))}。</li>
     * </ol>
     */
    private String encodePreviewUrlParam(String rawUrl) {
        String base64 = Base64.getEncoder().encodeToString(rawUrl.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(base64, StandardCharsets.UTF_8);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > -1 ? filename.substring(lastDotIndex + 1) : "";
    }

}
