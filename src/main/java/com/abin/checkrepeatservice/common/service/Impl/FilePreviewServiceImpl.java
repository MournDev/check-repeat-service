package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.common.service.FilePreviewService;
import com.abin.checkrepeatservice.common.service.FileService;
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
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    @Value("${app.host:192.168.1.149}")
    private String serverHost;

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${kkfileview.base-url}")
    private String kkfileviewUrl;

    @Value("${server.servlet.context-path}")
    private String appContext;

    @Value("${file.upload.base-path}")
    private String uploadBasePath;


    private final RestTemplate restTemplate;

    public FilePreviewServiceImpl() {
        this.restTemplate = new RestTemplate();
        // 设置连接超时和读取超时
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000); // 5秒连接超时
        requestFactory.setReadTimeout(30000);   // 30秒读取超时
        restTemplate.setRequestFactory(requestFactory);
    }

    @Override
    public ResponseEntity<Resource> directPreview(Long fileId) {
        try {
            FileInfo fileInfo = fileService.getById(fileId);
            if (fileInfo == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            String fileStoragePath = fileInfo.getStoragePath();
            File file = new File(uploadBasePath + fileStoragePath);

            if (!file.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            Resource resource = new FileSystemResource(file);
            String contentType = getContentType(file, fileInfo.getOriginalFilename());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + URLEncoder.encode(fileInfo.getOriginalFilename(), StandardCharsets.UTF_8) + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("直接预览失败 - fileId: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private String getContentType(File file, String originalFilename) {
        try {
            return Files.probeContentType(file.toPath());
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    @Override
    public ResponseEntity<byte[]> onlinePreviewByUrl(String url) {
        log.info("执行KKFileView代理预览 - url: {}", url);

        try {
            // 直接代理到KKFileView
            return proxyToKKFileViewForUrl(url);
        } catch (Exception e) {
            log.error("KKFileView代理预览失败 - url: {}", url, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("代理预览失败: " + e.getMessage()).getBytes());
        }
    }
    /**
     * 代理请求到KKFileView（基于URL）
     * KKFileView要求URL中必须包含文件名以便正确识别文件类型
     */
    private ResponseEntity<byte[]> proxyToKKFileViewForUrl(String url) {
        try {
            // 1. Base64编码URL（URL安全）
            String encodedUrl = Base64.getUrlEncoder().encodeToString(url.getBytes(StandardCharsets.UTF_8));

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
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("文件不存在");
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

        } catch (Exception e) {
            log.error("智能预览失败 - fileId: {}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("预览失败: " + e.getMessage());
        }
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
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("未找到已完成的查重任务");
            }

            // 2. 获取查重报告
            CheckReport checkReport = checkReportMapper.selectById(checkTask.getReportId());
            if (checkReport == null) {
                log.error("查重报告不存在 - reportId: {}", checkTask.getReportId());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("查重报告不存在");
            }

            // 3. 获取报告文件信息
            String reportPath = checkReport.getReportPath();
            File reportFile = new File(reportPath);
            if (!reportFile.exists()) {
                log.error("报告文件不存在 - path: {}", reportPath);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("报告文件不存在");
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

            // Office文档类型：使用KKFileView
            if (isOfficeFile(fileExtension)) {
                log.info("Office报告文档使用KKFileView预览 - paperId: {}", paperId);
                return proxyReportToKKFileView(reportPath, fileName);
            }

            // 其他类型：尝试直接预览
            log.info("其他类型报告文件尝试直接预览 - paperId: {}", paperId);
            return directPreviewReport(reportPath, fileName);

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
            File file = new File(reportPath);
            if (!file.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            Resource resource = new FileSystemResource(file);
            String contentType = getContentType(file, fileName);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("直接预览报告失败 - path: {}", reportPath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * 代理报告到KKFileView预览
     */
    private ResponseEntity<?> proxyReportToKKFileView(String reportPath, String fileName) {
        try {
            // 构建文件访问URL
            String fileUrl = "file://" + reportPath;

            // Base64编码（URL安全）
            String encodedUrl = Base64.getUrlEncoder().encodeToString(fileUrl.getBytes(StandardCharsets.UTF_8));

            // 构建KKFileView预览URL
            String kkFileViewPreviewUrl = String.format("%s/onlinePreview?url=%s",
                    kkfileviewUrl, encodedUrl);

            log.info("KKFileView报告预览URL: {}", kkFileViewPreviewUrl);

            // 测试KKFileView服务是否可用
            try {
                ResponseEntity<String> testResponse = restTemplate.getForEntity(
                        kkfileviewUrl + "/index", String.class);

                if (!testResponse.getStatusCode().is2xxSuccessful()) {
                    log.warn("KKFileView服务不可用，状态码: {}", testResponse.getStatusCode());
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body("预览服务暂时不可用");
                }
            } catch (Exception e) {
                log.warn("KKFileView服务连接失败: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("预览服务暂时不可用");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(kkFileViewPreviewUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
            log.error("代理报告到KKFileView失败 - path: {}", reportPath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("预览失败: " + e.getMessage());
        }
    }

    /**
     * 代理请求到 KKFileView
     */
    private ResponseEntity<?> proxyToKKFileView(Long fileId, FileInfo fileInfo) {
        try {
            // 1. 构建文件访问URL（包含文件名）
            String fileUrl = String.format("http://%s:%s%s/api/file/download/%s/%s",
                    serverHost,
                    serverPort,
                    appContext,
                    fileId,
                    URLEncoder.encode(fileInfo.getOriginalFilename(), StandardCharsets.UTF_8));

            log.info("构建KKFileView文件URL: {}", fileUrl);

            // 2. Base64编码（URL安全）
            String encodedUrl = Base64.getUrlEncoder().encodeToString(fileUrl.getBytes());

            // 3. 构建KKFileView预览URL
            String kkFileViewPreviewUrl = String.format("%s/onlinePreview?url=%s",
                    kkfileviewUrl, encodedUrl);

            log.info("KKFileView预览URL: {}", kkFileViewPreviewUrl);

            // 4. 测试KKFileView服务是否可用
            try {
                ResponseEntity<String> testResponse = restTemplate.getForEntity(
                        kkfileviewUrl + "/index", String.class);

                if (!testResponse.getStatusCode().is2xxSuccessful()) {
                    log.warn("KKFileView服务不可用，状态码: {}", testResponse.getStatusCode());
                    return fallbackToDownload(fileInfo);
                }
            } catch (Exception e) {
                log.warn("KKFileView服务连接失败: {}", e.getMessage());
                return fallbackToDownload(fileInfo);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(kkFileViewPreviewUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        } catch (Exception e) {
            log.error("代理到KKFileView失败 - fileId: {}", fileId, e);
            return fallbackToDownload(fileInfo);
        }
    }

    /**
     * 构建文件下载 URL
     */
    private String buildFileDownloadUrl(Long fileId) {
        try {
            FileInfo fileInfo = fileService.getById(fileId);
            // 使用宿主机的IP地址，而不是localhost
            String fileUrl = String.format("http://%s:%s%s/api/file/download/%s/%s",
                    serverHost,
                    serverPort,
                    fileId,
                    URLEncoder.encode(fileInfo.getOriginalFilename(), StandardCharsets.UTF_8));

            log.info("构建文件下载URL（宿主机地址）: {}", fileUrl);
            return fileUrl;

        } catch (Exception e) {
            log.error("构建下载URL失败", e);
            throw new RuntimeException("构建下载URL失败", e);
        }
    }

    private ResponseEntity<?> fallbackToDownload(FileInfo fileInfo) {
        // 降级方案：返回下载链接
        String downloadUrl = String.format("/api/file/download/%s/%s",
                fileInfo.getId(),
                URLEncoder.encode(fileInfo.getOriginalFilename(), StandardCharsets.UTF_8));

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
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > -1 ? filename.substring(lastDotIndex + 1) : "";
    }

    /**
     * 创建配置好的RestTemplate
     */
    private RestTemplate createRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // 设置连接超时
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 10秒连接超时
        factory.setReadTimeout(30000);     // 30秒读取超时
        restTemplate.setRequestFactory(factory);

        // 添加请求拦截器
        restTemplate.setInterceptors(Collections.singletonList(new RestTemplateLoggingInterceptor()));

        return restTemplate;
    }

    /**
     * RestTemplate日志拦截器
     */
    private static class RestTemplateLoggingInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            long startTime = System.currentTimeMillis();

            try {
                ClientHttpResponse response = execution.execute(request, body);
                long duration = System.currentTimeMillis() - startTime;

                log.debug("HTTP请求: {} {}, 耗时: {}ms, 状态: {}",
                        request.getMethod(), request.getURI(), duration, response.getStatusCode());

                return response;
            } catch (IOException e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("HTTP请求失败: {} {}, 耗时: {}ms",
                        request.getMethod(), request.getURI(), duration, e);
                throw e;
            }
        }
    }
}
