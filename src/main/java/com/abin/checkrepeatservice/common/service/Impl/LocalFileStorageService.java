package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.pojo.base.FileBusinessBindParam;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class LocalFileStorageService {

    // 修复：注入本地存储根路径（从配置文件读取）
    @Value("${file.upload.base-path}")
    private String baseLocalPath; // 本地存储根路径（如 D:/upload/ 或 /home/upload/）
    public String storeFile(MultipartFile file, FileBusinessBindParam businessParam) throws IOException {
        // 按“业务类型+业务ID+日期”分目录，避免文件名冲突
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String targetDir = String.format("%s/%s/%s/%s/",
                baseLocalPath, businessParam.getBusinessType(), businessParam.getBusinessId(), dateDir);
        File dir = new File(targetDir);
        if (!dir.exists()) dir.mkdirs();
        // 生成唯一文件名（业务ID_时间戳_原文件名）
        String uniqueFileName = businessParam.getBusinessId() + "_" + System.currentTimeMillis() +
                FilenameUtils.getExtension(file.getOriginalFilename());
        // 写入本地磁盘
        File targetFile = new File(targetDir + uniqueFileName);
        file.transferTo(targetFile);
        return targetFile.getAbsolutePath();
    }
}
