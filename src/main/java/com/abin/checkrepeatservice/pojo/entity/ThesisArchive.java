package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 论文归档实体（P4）：对应 thesis_archive 表。
 *
 * <p>定位：定稿（finalized）→ 归档（archived）时落一条证据快照，冻结论文、学生、
 * 导师、学院专业与查重报告关键字段，形成可追溯的学位授予证据链。一个论文版本
 * 只允许一条归档记录（uk_ta_paper_version），重新归档需产生新版本。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("thesis_archive")
public class ThesisArchive extends BaseEntity {

    /**
     * 归档编号（唯一，如 ARC20261022153000123）
     */
    @TableField("archive_no")
    private String archiveNo;

    /**
     * 论文ID（关联 paper_info.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("paper_id")
    private Long paperId;

    /**
     * 归档时的论文版本号
     */
    @TableField("paper_version")
    private Integer paperVersion;

    /**
     * 归档时对应的提交记录ID（paper_submit.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("submit_id")
    private Long submitId;

    @TableField("paper_title")
    private String paperTitle;

    @TableField("author")
    private String author;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("student_id")
    private Long studentId;

    /**
     * 学号（快照，取 sys_user.username）
     */
    @TableField("student_no")
    private String studentNo;

    @TableField("student_name")
    private String studentName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("teacher_id")
    private Long teacherId;

    @TableField("teacher_name")
    private String teacherName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("college_id")
    private Long collegeId;

    @TableField("college_name")
    private String collegeName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("major_id")
    private Long majorId;

    @TableField("major_name")
    private String majorName;

    @TableField("degree_type")
    private String degreeType;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("file_id")
    private Long fileId;

    @TableField("file_path")
    private String filePath;

    @TableField("file_md5")
    private String fileMd5;

    /**
     * 归档时采用的已完成查重任务ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("check_task_id")
    private Long checkTaskId;

    /**
     * 归档时采用的查重报告ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("check_report_id")
    private Long checkReportId;

    @TableField("report_no")
    private String reportNo;

    /**
     * 报告验真哈希（check_report.content_hash 快照）
     */
    @TableField("report_hash")
    private String reportHash;

    @TableField("check_rate")
    private BigDecimal checkRate;

    @TableField("max_per_source")
    private BigDecimal maxPerSource;

    @TableField("citation_rate")
    private BigDecimal citationRate;

    @TableField("check_source")
    private String checkSource;

    @TableField("archive_time")
    private LocalDateTime archiveTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("archive_by")
    private Long archiveBy;

    @TableField("archive_remark")
    private String archiveRemark;
}
