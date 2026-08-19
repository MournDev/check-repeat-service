package com.abin.checkrepeatservice.pojo.entity;


import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 论文提交实体类（对应数据库表：paper_submit）
 * 核心作用：存储用户提交论文的基础信息、文件信息、审核状态等
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("paper_submit") // 与数据库表名严格对应（确保MyBatis映射无异常）
public class PaperSubmit extends BaseEntity {

    /**
     * 论文ID（非空，唯一标识论文）
     */
    @NotNull(message = "论文ID不能为空")
    @TableField("paper_id")
    private Long paperId;

    /**
     * 学生ID（标识“谁提交的论文”）
     */
    @NotNull(message = "学生ID不能为空")
    @TableField("student_id")
    private Long studentId;

    /**
     * 论文提交版本号（核心流程字段，用于记录论文的提交次数，如“第1次提交，第2次提交”）
     */
    @TableField("submit_version")
    private Integer submitVersion;

    /**
     * 文件 ID（非空，标识“论文提交文件”）
     */
    @NotNull(message = "文件 ID 不能为空")
    @TableField("file_id")
    private Long fileId;

    /**
     * 文件MD5码（非空，用于校验文件完整性）
     */
    @NotBlank(message = "文件MD5码不能为空")
    @TableField("file_md5")
    private String fileMd5;

    /**
     * 提交时间（非空，标识“论文提交时间”）
     */
    @TableField("submit_time")
    private LocalDateTime submitTime;

    /**
     * 备注（用于记录论文的提交备注）
     */
    @TableField("remark")
    private String remark;

    // ==================== 以下字段已废弃 ====================
    // 原因：这些字段已在PaperInfo实体中维护，避免数据冗余
    // 保留PaperSubmit仅用于版本历史追踪

    /*
    @TableField("major_id")
    private Long majorId;
    
    @TableField("check_status")
    private String checkStatus;
    
    @TableField("similarity_rate")
    private BigDecimal similarityRate;
    
    @TableField("check_result")
    private String checkResult;
    
    @TableField("check_report_id")
    private Long checkReportId;
    
    @TableField("check_sim_hash")
    private String checkSimHash;
    
    @TableField("check_time")
    private LocalDateTime checkTime;
    
    @TableField("advisor_suggestion")
    private String advisorSuggestion;
    
    @TableField("status")
    private Integer status;
    
    @TableField("advisor_id")
    private Long advisorId;
    */
}
