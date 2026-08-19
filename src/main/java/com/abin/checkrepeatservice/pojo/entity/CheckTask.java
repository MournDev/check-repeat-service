package com.abin.checkrepeatservice.pojo.entity;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 查重任务实体：对应check_task表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_task") // 绑定数据库表名
public class CheckTask extends BaseEntity {
    /**
     * 对应的论文ID（关联paper_info.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("paper_id")
    @NotNull(message = "论文ID不能为空")
    private Long paperId;

    /**
     * 对应的论文文件 ID（关联 file_info.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("file_id")
    @NotNull(message = "文件 ID 不能为空")
    private Long fileId;
    /**
     * 任务编号（唯一，如：CHECK20240512001）
     */
    @TableField("task_no")
    @NotBlank(message = "任务编号不能为空")
    @Size(max = 50, message = "任务编号长度不能超过50字符")
    private String taskNo;

    /**
     * 查重重复率（如：15.32表示15.32%，任务失败时可为空）
     */
    @TableField("check_rate")
    private BigDecimal checkRate;

    /**
     * 任务开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 任务结束时间
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 任务状态（pending-待查重，checking-查重中，completed-查重成功，failure-查重失败）
     */
    @TableField("check_status")
    private String checkStatus;


    /**
     * 任务失败原因（如：文件解析错误、算法执行超时，任务成功时可为空）
     */
    @TableField("fail_reason")
    private String failReason;
    
    /**
     * 任务耗时（秒，任务完成后计算）
     */
    @TableField(exist = false)
    private Long durationSeconds;

    /**
     * 对应的查重报告ID（关联check_report.id，任务成功时非空，失败时可为空）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("report_id")
    private Long reportId;

    /**
     * 对应的查重报告路径（如：/report/20240512001.pdf，任务成功时非空，失败时可为空）
     */
    @TableField("report_path")
    private String reportPath;
    
    /**
     * 论文标题（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String paperTitle;
    
    /**
     * 学生ID（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private Long studentId;
    
    /**
     * 学生姓名（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String studentName;
    
    /**
     * 学生学号（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String studentNo;
    
    /**
     * 文件原始名称（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private String originalFileName;
    
    /**
     * 文件大小（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private Long fileSize;
    
    /**
     * 文件字数（冗余字段，便于查询显示）
     */
    @TableField(exist = false)
    private Integer wordCount;
}
