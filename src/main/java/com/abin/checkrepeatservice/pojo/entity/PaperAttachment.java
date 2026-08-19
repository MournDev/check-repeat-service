package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 论文附件实体类
 * 存储论文相关的各种附件文件信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("paper_attachment")
public class PaperAttachment extends BaseEntity {

    /**
     * 论文ID（关联paper_info.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("paper_id")
    private Long paperId;

    /**
     * 学生ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("student_id")
    private Long studentId;

    /**
     * 导师ID（关联sys_user.id，可为空）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("advisor_id")
    private Long advisorId;

    /**
     * 原始文件名
     */
    @TableField("original_filename")
    private String originalFilename;

    /**
     * 存储路径
     */
    @TableField("storage_path")
    private String storagePath;

    /**
     * 文件类型（如：pdf, doc, docx, txt等）
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 文件MD5值（用于去重）
     */
    @TableField("file_md5")
    private String fileMd5;

    /**
     * 附件类型（MAIN-主文档, SUPPORT-支撑材料, OTHER-其他）
     */
    @TableField("attachment_type")
    private String attachmentType;

    // 冗余字段 - 便于查询显示
    /**
     * 学生姓名
     */
    @TableField(exist = false)
    private String studentName;

    /**
     * 导师姓名
     */
    @TableField(exist = false)
    private String advisorName;

    /**
     * 论文标题
     */
    @TableField(exist = false)
    private String paperTitle;

    /**
     * 文件大小描述（如：1.2MB）
     */
    @TableField(exist = false)
    private String fileSizeDesc;
}