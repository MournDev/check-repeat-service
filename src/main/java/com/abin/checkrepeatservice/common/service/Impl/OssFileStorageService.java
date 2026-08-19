package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.pojo.base.FileBaseParam;
import com.abin.checkrepeatservice.pojo.base.FileBusinessBindParam;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class OssFileStorageService {
    // 注入OSS配置（从application.yml读取）
    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId; // 修复：注入AccessKeyId

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret; // 修复：注入AccessKeySecret

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    @Value("${aliyun.oss.access-prefix}")
    private String accessPrefix;

    public String storeFile(MultipartFile file, FileBusinessBindParam businessParam) throws IOException {
        // 初始化OSS客户端
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            // 构建OSS存储路径
            String objectName = String.format("upload/%s/%s/%s",
                    businessParam.getBusinessType(), businessParam.getBusinessId(), System.currentTimeMillis() +
                            FilenameUtils.getExtension(file.getOriginalFilename()));
            // 上传文件流
            ossClient.putObject(bucketName, objectName, file.getInputStream());
            // 生成访问URL
            return String.format("https://%s.%s/%s", bucketName, endpoint, objectName);
        } finally {
            ossClient.shutdown();
        }
    }
}