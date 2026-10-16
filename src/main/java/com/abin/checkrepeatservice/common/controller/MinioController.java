package com.abin.checkrepeatservice.common.controller;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import io.minio.*;
import io.minio.messages.Item;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MinIO 管理端调试/运维接口。
 *
 * <p>安全修复（P0）：</p>
 * <ul>
 *   <li>原 {@code @RestController("/api/minio")} 把路径写成了 Bean 名，真实路由挂在根路径
 *       （/list、/upload、/delete/{fileName}），且 SecurityConfig 对 /api/minio/** 的
 *       permitAll 规则根本拦不住这些真实路由——任意登录学生即可列举/覆盖/删除全桶对象。
 *       现修正为显式 @RequestMapping("/api/minio")。</li>
 *   <li>类级 @PreAuthorize 仅 ADMIN（含超管）可访问，与 SecurityConfig 双重保险。</li>
 *   <li>上传 objectKey 由服务端生成（UUID + 白名单扩展名），禁止用户文件名直传造成
 *       覆盖/路径穿越；下载、删除的 objectName 做路径字符合法性校验。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/minio")
@PreAuthorize("hasAuthority('ADMIN')")
public class MinioController {

    /** 管理端手工上传允许的扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "doc", "docx", "pdf", "xls", "xlsx", "ppt", "pptx", "txt", "png", "jpg", "jpeg");

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket.main}")
    private String bucketName;

    /**
     * 测试MinIO连接
     */
    @GetMapping("/test-connection")
    public Result<String> testConnection() {
        try {
            boolean exists = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build()
            );

            if (exists) {
                return Result.success("MinIO连接成功", "Bucket '" + bucketName + "' 存在且可访问");
            } else {
                return Result.error(ResultCode.SYSTEM_ERROR, "Bucket '" + bucketName + "' 不存在");
            }
        } catch (Exception e) {
            log.error("MinIO连接测试失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "MinIO连接失败");
        }
    }

    /**
     * 列出存储桶中的所有对象
     */
    @GetMapping("/list")
    public Result<List<Object>> list() {
        try {
            Iterable<io.minio.Result<Item>> myObjects = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucketName)
                    .build());
            Iterator<io.minio.Result<Item>> iterator = myObjects.iterator();
            List<Object> items = new ArrayList<>();

            while (iterator.hasNext()) {
                Item item = iterator.next().get();
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("fileName", item.objectName());
                fileInfo.put("fileSize", item.size());
                fileInfo.put("lastModified", item.lastModified());
                items.add(fileInfo);
            }

            return Result.success("文件列表获取成功", items);
        } catch (Exception e) {
            log.error("列出文件失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "文件列表获取失败");
        }
    }

    /**
     * 上传文件（objectKey 由服务端生成，避免覆盖与路径穿越）
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam(name = "file", required = false) MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return Result.error(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }

        List<String> uploadedFiles = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                String extension = extractAllowedExtension(originalFilename);
                if (extension == null) {
                    return Result.error(ResultCode.PARAM_TYPE_ERROR,
                            "不支持的文件类型，仅允许：" + String.join("/", ALLOWED_EXTENSIONS));
                }
                // 服务端生成 objectKey：统一前缀 + UUID，杜绝用户文件名直传导致的覆盖/穿越
                String objectName = "admin-uploads/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
                try (InputStream in = file.getInputStream()) {
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(in, file.getSize(), -1)
                            .contentType(Optional.ofNullable(file.getContentType()).orElse("application/octet-stream"))
                            .build());
                    uploadedFiles.add(objectName);
                }
                log.info("管理员上传对象: bucket={}, object={}, originalName={}",
                        bucketName, objectName, originalFilename);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("bucketName", bucketName);
            data.put("uploadedFiles", uploadedFiles);
            data.put("count", uploadedFiles.size());

            return Result.success("文件上传成功", data);
        } catch (Exception e) {
            log.error("上传文件失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "文件上传失败");
        }
    }


    /**
     * 下载文件
     */
    @GetMapping("/download/{fileName}")
    public void download(HttpServletResponse response, @PathVariable("fileName") String fileName) {
        if (!isSafeObjectName(fileName)) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, "非法的对象名称");
            return;
        }
        InputStream in = null;
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());

            response.setContentType(stat.contentType());
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            response.setStatus(HttpServletResponse.SC_OK);

            in = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
            IOUtils.copy(in, response.getOutputStream());
        } catch (Exception e) {
            log.error("下载文件失败: {}", fileName, e);
            reject(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "文件下载失败");
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error("关闭输入流失败", e);
                }
            }
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/delete/{fileName}")
    public Result<String> delete(@PathVariable("fileName") String fileName) {
        if (!isSafeObjectName(fileName)) {
            return Result.error(ResultCode.PARAM_ERROR, "非法的对象名称");
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
            log.warn("管理员删除对象: bucket={}, object={}", bucketName, fileName);
            return Result.success("文件删除成功", fileName);
        } catch (Exception e) {
            log.error("删除文件失败: {}", fileName, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "文件删除失败");
        }
    }

    /**
     * objectName 合法性校验：拒绝空、绝对路径、路径穿越、反斜杠与控制字符
     */
    private boolean isSafeObjectName(String objectName) {
        if (objectName == null || objectName.isBlank() || objectName.length() > 255) {
            return false;
        }
        if (objectName.startsWith("/") || objectName.contains("\\") || objectName.contains("..")) {
            return false;
        }
        for (int i = 0; i < objectName.length(); i++) {
            char c = objectName.charAt(i);
            if (c < 0x20) {
                return false;
            }
        }
        return true;
    }

    /**
     * 提取并校验扩展名（白名单），不合法返回 null
     */
    private String extractAllowedExtension(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("..") || filename.contains("/")
                || filename.contains("\\")) {
            return null;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return null;
        }
        String ext = filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.contains(ext) ? ext : null;
    }

    private void reject(HttpServletResponse response, int status, String message) {
        try {
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(Result.error(ResultCode.PARAM_ERROR, message).toString());
        } catch (IOException ioException) {
            log.error("发送错误响应失败", ioException);
        }
    }
}
