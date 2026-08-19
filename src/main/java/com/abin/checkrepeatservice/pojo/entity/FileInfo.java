package com.abin.checkrepeatservice.pojo.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("file_info")
public class FileInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("original_filename")
    private String originalFilename;

    @TableField("file_size")
    private Long fileSize;

    @TableField("file_size_desc")
    private String fileSizeDesc;

    @TableField("storage_path")
    private String storagePath;

    @TableField("access_url")
    private String accessUrl;

    @TableField("md5")
    private String md5;

    @TableField("word_count")
    private Integer wordCount;

    @TableField("upload_time")
    private LocalDateTime uploadTime;

    @TableField("upload_user_id")
    private String uploadUserId;

    @TableField("biz_type")
    private String bizType;

    @TableField("biz_id")
    private String bizId;

    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    /**
     * 文件类型（如：pdf, doc, docx等）
     */
    @TableField(exist = false)
    private String fileType;
    
    /**
     * 上传用户姓名（冗余字段，便于显示）
     */
    @TableField(exist = false)
    private String uploadUserName;
    
    /**
     * 业务关联对象名称（冗余字段，便于显示）
     */
    @TableField(exist = false)
    private String bizObjectName;
    
    /**
     * 文件访问完整URL（冗余字段，便于前端直接使用）
     */
    @TableField(exist = false)
    private String fullAccessUrl;}