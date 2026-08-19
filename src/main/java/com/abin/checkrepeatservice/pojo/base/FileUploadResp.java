package com.abin.checkrepeatservice.pojo.base;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件上传通用响应（所有上传场景统一返回）
 */
@Data
@ApiModel("文件上传通用响应")
public class FileUploadResp {
    /**
     * 文件ID（附件表主键，唯一标识）
     * 作用：业务层关联文件的核心ID（如论文表存储此ID关联附件）
     */
    @ApiModelProperty(value = "文件ID（附件表主键）", example = "456")
    private Long id;

    /**
     * 原始文件名
     */
    @ApiModelProperty(value = "原始文件名", example = "论文初稿V2.pdf")
    private String originalFilename;

    /**
     * 文件大小（单位：MB，格式化后）
     */
    @ApiModelProperty(value = "文件大小（MB）", example = "2.5")
    private String fileSizeDesc;

    /**
     * 存储路径（本地路径/云存储URL）
     * 作用：下载/预览时使用
     */
    @ApiModelProperty(value = "文件存储路径（本地/云URL）", example = "https://oss.xxx.com/upload/paper/202411/123_456.pdf")
    private String storagePath;

    /**
     * 访问URL（可选，云存储场景返回临时预览URL）
     */
    @ApiModelProperty(value = "文件访问URL（临时预览用，可选）", example = "https://oss.xxx.com/upload/paper/202411/123_456.pdf?sign=xxx")
    private String accessUrl;

    /**
     * 上传时间
     */
    @ApiModelProperty(value = "上传时间", example = "2024-11-10 15:30:00")
    private LocalDateTime uploadTime;

    /**
     * 备注（可选）
     */
    @ApiModelProperty(value = "备注（可选）", example = "上传时填写的备注信息")
    private String remark;
}
