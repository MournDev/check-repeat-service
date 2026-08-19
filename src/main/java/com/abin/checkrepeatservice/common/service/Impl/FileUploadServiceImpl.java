package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.common.service.FileUploadService;
import com.abin.checkrepeatservice.common.utils.JwtUtils;
import com.abin.checkrepeatservice.pojo.base.FileBaseParam;
import com.abin.checkrepeatservice.pojo.base.FileBusinessBindParam;
import com.abin.checkrepeatservice.pojo.base.FileUploadResp;
import com.abin.checkrepeatservice.pojo.entity.PaperInfo;
import com.abin.checkrepeatservice.student.mapper.PaperInfoMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class FileUploadServiceImpl implements FileUploadService {
    @Value("${file.upload.base-path}")
    private String baseLocalPath; // 本地存储根路径
    @Value("${file.upload.storage-type}") // 配置切换存储类型（LOCAL/OSS）
    private String storageType;

    @Resource
    private LocalFileStorageService localFileStorageService;
    @Resource
    private OssFileStorageService ossFileStorageService;
    @Resource
    private PaperInfoMapper paperInfoMapper;

    @Resource
    private JwtUtils jwtUtils;

    @Override
    public FileUploadResp uploadFile(FileBaseParam baseParam, FileBusinessBindParam businessParam,Long loginUserId) throws IOException {
        // 1. 基础校验（文件非空、大小、类型）
        validateBaseParam(baseParam);
        // 2. MD5查重（避免重复上传）
        Long existingFileId = checkFileExists(baseParam.getFileMd5());
        if (existingFileId != null) {
            return buildExistResp(existingFileId);
        }
        // 3. 按配置选择存储方式（本地/OSS）
        String storagePath = "LOCAL".equals(storageType)
                ? localFileStorageService.storeFile(baseParam.getFile(), businessParam)
                : ossFileStorageService.storeFile(baseParam.getFile(), businessParam);
        // 4. 记录文件元数据到paper_info表
        PaperInfo paperInfo = buildSysAttachment(baseParam, businessParam, storagePath, loginUserId);
        paperInfoMapper.insert(paperInfo);
        // 5. 封装标准化响应
        return buildUploadResp(paperInfo);
    }

    /**
     * 组装新上传文件的响应VO
     * @param paperInfo 通用文件实体（paper_info表记录）
     * @return 标准化文件上传响应
     */
    private FileUploadResp buildUploadResp(PaperInfo paperInfo) {
        FileUploadResp resp = new FileUploadResp();

        // 1. 核心标识信息（前端关联业务的关键）
        resp.setId(paperInfo.getId()); // 文件ID（主键，用于下载/预览/关联业务）
//        resp.setOriginalFilename(paperInfo.getFileName()); // 原始文件名

        // 2. 文件属性信息（格式化展示，提升前端体验）
        // 文件大小格式化：字节→MB（保留1位小数，小于1MB显示KB）
//        double fileSizeMb = paperInfo.getFileSize() / 1024.0 / 1024.0;
//        resp.setFileSizeDesc(fileSizeMb >= 1
//                ? String.format("%.1fMB", fileSizeMb)
//                : String.format("%.0fKB", paperInfo.getFileSize() / 1024.0));

        // 3. 存储与访问信息
//        resp.setStoragePath(paperInfo.getFilePath()); // 存储路径（本地路径/OSS URL）
        // 若为OSS存储，直接将storagePath作为访问URL；本地存储可后续通过下载接口拼接访问路径
//        resp.setAccessUrl("OSS".equals(storageType) ? paperInfo.getStoragePath() : null);

        // 4. 审计与业务关联信息
        resp.setUploadTime(paperInfo.getCreateTime()); // 上传时间（取自实体的创建时间）

        return resp;
    }

    /**
     * 组装已存在文件的响应VO（防重复上传）
     * @param existingFileId 已存在文件的ID（sys_attachment.id）
     * @return 标准化文件上传响应（复用已有文件信息）
     */
    private FileUploadResp buildExistResp(Long existingFileId) {
        // 1. 查询已存在的文件实体
        PaperInfo existPaper = paperInfoMapper.selectById(existingFileId);
        if (existPaper == null || existPaper.getIsDeleted() == 1) {
            // 极端情况：ID存在但文件已删除，返回空（触发重新上传）
            return null;
        }

        // 2. 复用 buildUploadResp 逻辑，避免重复编码
        FileUploadResp resp = buildUploadResp(existPaper);
        // 补充“文件已存在”的提示（前端可展示“文件已存在，已复用”）
        resp.setRemark("文件已存在，无需重复上传");

        return resp;
    }

    /**
     * 通过文件MD5值查询文件是否已存在（防重复上传）
     * @param fileMd5 文件MD5值（前端计算传入）
     * @return 已存在文件的ID（无则返回null）
     */
    @Override
    public Long checkFileExists(String fileMd5) {
        if (fileMd5 == null || fileMd5.trim().length() != 32) {
            // MD5格式非法（非32位），直接返回null（视为不存在）
            return null;
        }

        // 查询条件：MD5匹配 + 未删除（逻辑删除标记=0）
        QueryWrapper<PaperInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("file_md5", fileMd5)
                .eq("is_deleted", 0)
                .last("LIMIT 1"); // 仅取一条（MD5唯一索引，理论上仅一条）

        PaperInfo existingAttachment = paperInfoMapper.selectOne(queryWrapper);
        // 存在则返回文件ID，不存在返回null
        return existingAttachment != null ? existingAttachment.getId() : null;
    }

    private void validateBaseParam(FileBaseParam baseParam) {
    // 检查基础参数对象是否为空
    if (baseParam == null) {
        throw new IllegalArgumentException("文件基础参数不能为空");
    }

    // 检查文件是否为空
    if (baseParam.getFile() == null || baseParam.getFile().isEmpty()) {
        throw new IllegalArgumentException("上传文件不能为空");
    }

    // 检查文件大小（示例限制为10MB）
    long maxSize = 50 * 1024 * 1024; // 50MB
    if (baseParam.getFile().getSize() > maxSize) {
        throw new IllegalArgumentException("文件大小不能超过10MB");
    }

    // 检查文件类型（根据实际需求调整允许的类型）
    String contentType = baseParam.getFile().getContentType();
    if (contentType == null || !isValidFileType(contentType)) {
        throw new IllegalArgumentException("不支持的文件类型: " + contentType);
    }

    // 检查文件MD5值
    if (baseParam.getFileMd5() == null || baseParam.getFileMd5().trim().isEmpty()) {
        throw new IllegalArgumentException("文件MD5值不能为空");
    }
}

/**
 * 验证文件类型是否合法
 * @param contentType 文件内容类型
 * @return 是否为合法的文件类型
 */
private boolean isValidFileType(String contentType) {
    // 定义允许的文件类型
    Set<String> allowedTypes = Set.of(
        "image/jpeg",
        "image/png",
        "image/gif",
        "application/pdf",
        "text/plain",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    return allowedTypes.contains(contentType);
}

    /**
     * 核心辅助方法：组装 PaperInfo 实体（文件元数据）
     * 作用：将上传参数转换为数据库实体，存入 sys_attachment 表
     * @param baseParam 通用文件参数（文件流、MD5、文件名等）
     * @param businessParam 业务绑定参数（业务类型、业务ID等）
     * @param storagePath 文件存储路径（本地路径/OSS URL)
     * @param loginUserId 登录用户ID
     * @return 组装完成的 PaperInfo 实体
     */
    private PaperInfo buildSysAttachment(
            FileBaseParam baseParam,
            FileBusinessBindParam businessParam,
            String storagePath,
            Long loginUserId
    ) {
        MultipartFile file = baseParam.getFile();
        // 1. 初始化 SysAttachment 实体（继承 BaseEntity，审计字段自动填充）
        PaperInfo paperInfo = new PaperInfo();

        // 2. 填充通用文件元数据（来自 FileBaseParam）
//        paperInfo.setFileName(
//                StringUtils.hasText(baseParam.getOriginalFilename())
//                        ? baseParam.getOriginalFilename()
//                        : file.getOriginalFilename() // 优先用自定义文件名，无则用原文件名
//        );
//        paperInfo.setFilePath(storagePath); // 文件存储路径（本地/OSS）
//        paperInfo.setFileType(file.getContentType()); // 文件MIME类型（如 application/pdf）
//        paperInfo.setFileSize(file.getSize()); // 文件大小（单位：字节）
        paperInfo.setFileMd5(baseParam.getFileMd5()); // 文件MD5（防重复+完整性校验）

        // 3. 填充业务关联信息（来自 FileBusinessBindParam）
//        paperInfo.setFileType(businessParam.getBusinessType()); // 业务类型（如 PAPER_ATTACH）
        paperInfo.setId(businessParam.getBusinessId()); // 业务ID（如论文ID）

        // 4. 填充审计字段（部分由 MyBatis-Plus 自动填充，此处补充手动设置项）
        paperInfo.setCreateBy(loginUserId); // 上传人ID（创建人）
        paperInfo.setUpdateBy(loginUserId); // 更新人ID（初始与创建人一致）
        paperInfo.setCreateTime(LocalDateTime.now()); // 创建时间（可由自动填充替代）
        paperInfo.setUpdateTime(LocalDateTime.now()); // 更新时间（可由自动填充替代）
        paperInfo.setIsDeleted(0); // 逻辑删除标记（0=未删除）

        return paperInfo;
    }

    // 其他辅助方法：参数校验、MD5查重、响应封装...
}