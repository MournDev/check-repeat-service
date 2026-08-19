package com.abin.checkrepeatservice.common.service.Impl;
import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileValidationService {

    private static final long MAX_FILE_SIZE = 200 * 1024 * 1024; // 200MB
    private static final String[] ALLOWED_EXTENSIONS = {"doc", "docx", "pdf"};

    public Result<Void> validateFile(MultipartFile file) {
        // 检查文件是否为空
        if (file.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR, "论文文件不能为空");
        }

        // 检查文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            return Result.error(ResultCode.PARAM_ERROR, "论文文件大小不能超过200MB");
        }

        // 检查文件扩展名
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileExtension(originalFilename).toLowerCase();

        boolean isValidType = false;
        for (String allowedExtension : ALLOWED_EXTENSIONS) {
            if (allowedExtension.equals(fileType)) {
                isValidType = true;
                break;
            }
        }

        if (!isValidType) {
            return Result.error(ResultCode.PARAM_ERROR, "仅支持doc、docx、pdf格式的论文文件");
        }

        return Result.success(null); // 无错误表示验证通过
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}

