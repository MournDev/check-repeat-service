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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@RestController("/api/minio")
public class MinioController {
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
            return Result.error(ResultCode.SYSTEM_ERROR, "MinIO连接失败: " + e.getMessage());
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
            return Result.error(ResultCode.SYSTEM_ERROR, "文件列表获取失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam(name = "file", required = false) MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return Result.error(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }

        List<String> uploadedFiles = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                String fileName = file.getOriginalFilename();
                try (InputStream in = file.getInputStream()) {
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(in, in.available(), -1)
                            .build());
                    uploadedFiles.add(fileName);
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("bucketName", bucketName);
            data.put("uploadedFiles", uploadedFiles);
            data.put("count", uploadedFiles.size());

            return Result.success("文件上传成功", data);
        } catch (Exception e) {
            log.error("上传文件失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR, "文件上传失败: " + e.getMessage());
        }
    }


    /**
     * 下载文件
     */
    @GetMapping("/download/{fileName}")
    public void download(HttpServletResponse response, @PathVariable("fileName") String fileName) {
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
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write(Result.error(ResultCode.RESOURCE_NOT_FOUND, "文件下载失败: " + e.getMessage()).toString());
            } catch (IOException ioException) {
                log.error("发送错误响应失败", ioException);
            }
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
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileName)
                    .build());
            return Result.success("文件删除成功", fileName);
        } catch (Exception e) {
            log.error("删除文件失败: {}", fileName, e);
            return Result.error(ResultCode.SYSTEM_ERROR, "文件删除失败: " + e.getMessage(), fileName);
        }
    }
}
