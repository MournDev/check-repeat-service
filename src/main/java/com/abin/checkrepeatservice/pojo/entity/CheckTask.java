package com.abin.checkrepeatservice.pojo.entity;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
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
     * 论文版本号（M3.1）：创建任务时快照 paper_info.version，
     * 使报告可反查到对应论文版本；存量任务为 null
     */
    @TableField("paper_version")
    private Integer paperVersion;

    /**
     * 论文提交记录ID（M3.1）：创建任务时取该论文最新一次 paper_submit.id，
     * 使报告可反查到对应提交记录；存量任务为 null
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("submit_id")
    private Long submitId;

    /**
     * 是否已扣减学院月度额度（M4.1）：1-已扣，0-未扣/已返还。
     * 失败返还时按 1 -> 0 原子翻转，保证同一任务最多返还一次；重试重新扣减并置 1。
     */
    @TableField("quota_charged")
    private Integer quotaCharged;

    /**
     * 乐观锁版本号（P0 并发控制）：实体级更新（updateById）会带上 version 条件并自增；
     * 状态流转则走「状态条件 UPDATE + version = version + 1」，两种更新方式共用同一版本轴。
     * 注意：该列必须在部署新代码前先执行 DDL，否则 MyBatis-Plus 自动 SQL 会引用不存在的列。
     */
    @Version
    @TableField("version")
    private Integer version;

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
     * P5 起新任务口径升级为"总相似比"（非引用命中句字数/正文字数），存量行保留旧口径（整篇最大余弦）
     */
    @TableField("check_rate")
    private BigDecimal checkRate;

    /**
     * 单篇最大相似比（P5：最大单篇来源命中字数占正文字数百分比）
     */
    @TableField("max_per_source")
    private BigDecimal maxPerSource;

    /**
     * 引用率（P5：句尾带 [n] 引注的命中句字数占正文字数百分比，不计入总相似比）
     */
    @TableField("citation_rate")
    private BigDecimal citationRate;

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
