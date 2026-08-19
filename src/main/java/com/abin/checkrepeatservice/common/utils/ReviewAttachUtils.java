package com.abin.checkrepeatservice.common.utils;


import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 审核附件处理工具类：上传、存储、XSS清洗
 */
@Slf4j
@Component
public class ReviewAttachUtils {
    // 审核附件存储根路径（从配置文件获取）
    @Value("${review.attach.base-path}")
    private String attachBasePath;

    // 附件最大大小（字节，50MB）
    @Value("${review.attach.max-size}")
    private String attachMaxSize;

    // 允许的附件格式（从配置文件获取）
    @Value("${review.attach.allowed-types}")
    private String allowedTypes;

    // 允许的附件格式集合（初始化时加载）
    private Set<String> allowedTypeSet;

    @PostConstruct
    public void initAllowedTypes() {
        if (allowedTypes == null || allowedTypes.trim().isEmpty()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "审核附件允许格式未配置");
        }
        // 分割为集合（全小写）
        allowedTypeSet = Stream.of(allowedTypes.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    /**
     * 上传审核附件（返回存储路径与附件信息）
     * @param attachFile 附件文件
     * @param teacherId 审核教师ID（用于分目录）
     * @return 附件信息Map（含path/name/size/type）
     */
    public AttachInfo uploadReviewAttach(MultipartFile attachFile, Long teacherId) {
        // 1. 校验文件合法性
        if (attachFile == null || attachFile.isEmpty()) {
            return null; // 无附件，返回null
        }
        // 校验文件大小
        long maxSizeBytes = parseSizeToBytes(attachMaxSize);
        if (attachFile.getSize() > maxSizeBytes) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    String.format("审核附件大小不能超过%s（当前：%.2fMB）",
                            attachMaxSize, attachFile.getSize() / 1024.0 / 1024.0));
        }
        // 校验文件格式
        String originalFilename = attachFile.getOriginalFilename();
        String fileSuffix = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!allowedTypeSet.contains(fileSuffix)) {
            throw new BusinessException(ResultCode.PARAM_TYPE_ERROR,
                    String.format("审核附件仅支持%s格式（当前：%s）",
                            String.join(",", allowedTypeSet), fileSuffix));
        }

        // 2. 处理存储路径（按教师ID+日期分目录）
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String teacherDir = "teacher_" + teacherId;
        Path attachDirPath = Paths.get(attachBasePath, teacherDir, dateDir);
        File attachDir = attachDirPath.toFile();
        if (!attachDir.exists() && !attachDir.mkdirs()) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "创建审核附件目录失败");
        }

        // 3. 生成唯一文件名（避免覆盖）
        String uniqueFileName = UUID.randomUUID().toString() + "." + fileSuffix;
        Path attachFilePath = Paths.get(attachDirPath.toString(), uniqueFileName);

        // 4. 保存文件
        try {
            attachFile.transferTo(attachFilePath);
            log.info("审核附件上传成功：{}，大小：{}字节", attachFilePath, attachFile.getSize());
        } catch (IOException e) {
            log.error("审核附件保存失败：", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "审核附件上传异常");
        }

        // 5. 封装附件信息
        AttachInfo attachInfo = new AttachInfo();
        attachInfo.setAttachPath(attachFilePath.toString());
        attachInfo.setAttachName(originalFilename);
        attachInfo.setAttachSize(attachFile.getSize());
        attachInfo.setAttachType(fileSuffix);
        return attachInfo;
    }

    /**
     * 清洗富文本审核意见（防XSS攻击）
     * @param rawOpinion 原始富文本意见
     * @return 清洗后的富文本
     */
    public String cleanReviewOpinion(String rawOpinion) {
        if (rawOpinion == null || rawOpinion.trim().isEmpty()) {
            return "";
        }
        // 使用Jsoup清洗XSS：允许常见标签（p/br/strong/em等），过滤script/style等危险标签
        return Jsoup.clean(
                rawOpinion,
                Safelist.relaxed() // 宽松模式，允许常见富文本标签
                        .addTags("p", "br", "strong", "em", "u", "span", "ul", "li", "ol")
                        .addAttributes("span", "style")
                        .addProtocols("a", "href", "http", "https")
        );
    }

    /**
     * 删除审核附件（用于审核记录删除时）
     * @param attachPath 附件存储路径
     */
    public void deleteReviewAttach(String attachPath) {
        if (attachPath == null || attachPath.trim().isEmpty()) {
            return;
        }
        Path path = Paths.get(attachPath);
        if (Files.exists(path)) {
            try {
                Files.delete(path);
                log.info("审核附件删除成功：{}", attachPath);
            } catch (IOException e) {
                log.error("审核附件删除失败：", e);
                // 不抛出异常，仅日志记录
            }
        }
    }

    // ------------------------------ 私有辅助方法 ------------------------------
    /**
     * 解析大小字符串为字节数（如50MB→52428800字节）
     */
    private long parseSizeToBytes(String sizeStr) {
        sizeStr = sizeStr.trim().toUpperCase();
        long size;
        if (sizeStr.endsWith("KB")) {
            size = Long.parseLong(sizeStr.substring(0, sizeStr.length() - 2)) * 1024;
        } else if (sizeStr.endsWith("MB")) {
            size = Long.parseLong(sizeStr.substring(0, sizeStr.length() - 2)) * 1024 * 1024;
        } else if (sizeStr.endsWith("GB")) {
            size = Long.parseLong(sizeStr.substring(0, sizeStr.length() - 2)) * 1024 * 1024 * 1024;
        } else {
            size = Long.parseLong(sizeStr); // 默认字节
        }
        return size;
    }

    // ------------------------------ 附件信息内部类 ------------------------------
    @Data
    public static class AttachInfo {
        private String attachPath; // 存储路径
        private String attachName; // 原文件名
        private Long attachSize; // 大小（字节）
        private String attachType; // 格式（如pdf）
    }
}
