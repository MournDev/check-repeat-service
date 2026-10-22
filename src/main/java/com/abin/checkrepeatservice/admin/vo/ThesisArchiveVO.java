package com.abin.checkrepeatservice.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 论文归档记录 VO（P4）：归档证据快照出参
 */
@Data
public class ThesisArchiveVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String archiveNo;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    private Integer paperVersion;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long submitId;

    private String paperTitle;

    private String author;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long studentId;

    private String studentNo;

    private String studentName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long teacherId;

    private String teacherName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long collegeId;

    private String collegeName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long majorId;

    private String majorName;

    private String degreeType;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long fileId;

    private String filePath;

    private String fileMd5;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long checkTaskId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long checkReportId;

    private String reportNo;

    private String reportHash;

    private BigDecimal checkRate;

    private BigDecimal maxPerSource;

    private BigDecimal citationRate;

    private String checkSource;

    private LocalDateTime archiveTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long archiveBy;

    private String archiveRemark;
}
