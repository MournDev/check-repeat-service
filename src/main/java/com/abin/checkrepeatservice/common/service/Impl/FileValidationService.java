package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 论文文件校验（阶段 5 安全加固）。
 *
 * <p>不再只信任原始文件名后缀和前端 Content-Type：
 * 用 Apache Tika 读取文件头做真实魔数探测，扩展名与检测出的 MIME 必须同时命中白名单，
 * 防止把脚本/可执行文件改名为 .pdf / .docx 上传。</p>
 */
@Service
public class FileValidationService {

    private static final long MAX_FILE_SIZE = 200 * 1024 * 1024; // 200MB

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("doc", "docx", "pdf");

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    /** Tika 线程安全，可复用 */
    private static final Tika TIKA = new Tika();

    public Result<Void> validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "论文文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error(ResultCode.PARAM_ERROR, "论文文件大小不能超过200MB");
        }

        String originalFilename = file.getOriginalFilename();
        String fileType = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(fileType)) {
            return Result.error(ResultCode.PARAM_ERROR, "仅支持doc、docx、pdf格式的论文文件");
        }

        String detectedMime;
        try (InputStream inputStream = file.getInputStream()) {
            // name 只作为弱提示，Tika 以真实文件头为准
            detectedMime = TIKA.detect(inputStream, originalFilename);
        } catch (IOException e) {
            return Result.error(ResultCode.PARAM_ERROR, "文件类型检测失败，请重新上传");
        }

        if (detectedMime == null || !ALLOWED_MIME_TYPES.contains(detectedMime)) {
            return Result.error(ResultCode.PARAM_ERROR,
                    "文件内容与扩展名不匹配（检测类型：" + detectedMime + "），仅支持 doc/docx/pdf");
        }

        return Result.success(null);
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
