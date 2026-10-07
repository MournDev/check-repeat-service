package com.abin.checkrepeatservice.common.service;

import io.minio.MinioClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 论文内容MinIO存储服务
 * 负责将论文正文和IK分词结果存储到MinIO对象存储中
 */
@Service
@Slf4j
public class PaperContentMinioService {

    @Resource
    private MinioClient minioClient;

    @Value("${minio.bucket.paper_content:paper-content}")
    private String bucketName;

    /**
     * 拼存库路径用的地址，必须是浏览器可达的。
     * 不能用 {@code minio.endpoint}：那是 SDK 的连接地址，容器部署下是网络别名，
     * 存进库的 URL 浏览器打不开。未配置 public-endpoint 时回退成 endpoint。
     */
    @Value("${minio.public-endpoint:${minio.endpoint}}")
    private String minioPublicEndpoint;

    /**
     * 存储论文正文内容到MinIO
     * @param content 论文正文内容
     * @param paperId 论文ID（用于生成唯一路径）
     * @return MinIO存储路径
     */
    public String storePaperContent(String content, Long paperId) {
        try {
            // 验证参数
            if (content == null || content.isEmpty()) {
                throw new IllegalArgumentException("论文内容不能为空");
            }
            if (paperId == null) {
                throw new IllegalArgumentException("论文ID不能为空");
            }
            
            String fileName = "papers/content/" + paperId + "/" + UUID.randomUUID().toString() + ".txt";
            
            // 检查MinIO连接
            testMinioConnection();
            
            try (InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                minioClient.putObject(io.minio.PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(inputStream, content.getBytes(StandardCharsets.UTF_8).length, -1)
                        .contentType("text/plain")
                        .build());
            }
            
            String fullPath = minioPublicEndpoint + "/" + bucketName + "/" + fileName;
            log.info("论文正文存储成功: paperId={}, path={}", paperId, fullPath);
            return fullPath;
        } catch (Exception e) {
            log.error("存储论文正文失败: paperId={}", paperId, e);
            throw new RuntimeException("存储论文正文失败: " + e.getMessage(), e);
        }
    }

    /**
     * 存储IK分词结果到MinIO
     * @param segmentedText IK分词后的文本
     * @param paperId 论文ID
     * @return MinIO存储路径
     */
    public String storeSegmentedText(String segmentedText, Long paperId) {
        try {
            String fileName = "papers/segmented/" + paperId + "/" + UUID.randomUUID().toString() + ".txt";
            
            try (InputStream inputStream = new ByteArrayInputStream(segmentedText.getBytes(StandardCharsets.UTF_8))) {
                minioClient.putObject(io.minio.PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(inputStream, segmentedText.getBytes(StandardCharsets.UTF_8).length, -1)
                        .contentType("text/plain")
                        .build());
            }
            
            String fullPath = minioPublicEndpoint + "/" + bucketName + "/" + fileName;
            log.info("论文分词结果存储成功: paperId={}, path={}", paperId, fullPath);
            return fullPath;
        } catch (Exception e) {
            log.error("存储论文分词结果失败: paperId={}", paperId, e);
            throw new RuntimeException("存储论文分词结果失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取论文正文内容
     * @param contentPath MinIO存储路径
     * @return 论文正文内容
     */
    public String readPaperContent(String contentPath) {
        try {
            String objectName = extractObjectName(contentPath);
            if (objectName == null) {
                throw new IllegalArgumentException("无效的存储路径: " + contentPath);
            }
            
            try (InputStream inputStream = minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build())) {
                
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("读取论文正文失败: path={}", contentPath, e);
            throw new RuntimeException("读取论文正文失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取IK分词结果
     * @param segmentedPath MinIO存储路径
     * @return 分词结果内容
     */
    public String readSegmentedText(String segmentedPath) {
        try {
            String objectName = extractObjectName(segmentedPath);
            if (objectName == null) {
                throw new IllegalArgumentException("无效的存储路径: " + segmentedPath);
            }
            
            try (InputStream inputStream = minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build())) {
                
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("读取论文分词结果失败: path={}", segmentedPath, e);
            throw new RuntimeException("读取论文分词结果失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除论文相关内容（正文和分词结果）
     * @param paperId 论文ID
     */
    public void deletePaperContent(Long paperId) {
        try {
            // 删除正文内容目录
            String contentPrefix = "papers/content/" + paperId + "/";
            deleteObjectsByPrefix(contentPrefix);
            
            // 删除分词结果目录
            String segmentedPrefix = "papers/segmented/" + paperId + "/";
            deleteObjectsByPrefix(segmentedPrefix);
            
            log.info("论文相关内容删除成功: paperId={}", paperId);
        } catch (Exception e) {
            log.error("删除论文相关内容失败: paperId={}", paperId, e);
            throw new RuntimeException("删除论文相关内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据前缀删除对象
     */
    private void deleteObjectsByPrefix(String prefix) {
        try {
            Iterable<io.minio.Result<io.minio.messages.Item>> results = 
                minioClient.listObjects(io.minio.ListObjectsArgs.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .build());
            
            for (io.minio.Result<io.minio.messages.Item> result : results) {
                io.minio.messages.Item item = result.get();
                minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(item.objectName())
                        .build());
                log.debug("删除对象: {}", item.objectName());
            }
        } catch (Exception e) {
            log.warn("删除前缀对象失败: prefix={}", prefix, e);
        }
    }

    /**
     * 从完整URL中提取对象名称
     */
    private String extractObjectName(String fullPath) {
        if (fullPath == null || !fullPath.contains(bucketName + "/")) {
            return null;
        }
        return fullPath.substring(fullPath.indexOf(bucketName + "/") + bucketName.length() + 1);
    }
    
    /**
     * 测试MinIO连接
     */
    private void testMinioConnection() {
        try {
            // 简单的连接测试
            minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
            log.debug("MinIO连接测试通过");
        } catch (Exception e) {
            log.error("MinIO连接测试失败", e);
            throw new RuntimeException("MinIO服务连接失败: " + e.getMessage(), e);
        }
    }
}