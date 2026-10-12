package com.abin.checkrepeatservice.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端导师评价实名分页行（论文全生命周期 P6a）。
 * 管理端实名可见 studentName（防滥用追溯）；tags 已解析为 dict_value 列表。
 */
@Data
public class AdvisorEvalPageItemVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    private String paperTitle;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long studentId;

    private String studentName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long teacherId;

    private String teacherName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long collegeId;

    private String collegeName;

    private Integer scoreTimeliness;

    private Integer scoreQuality;

    private Integer scoreAttitude;

    private Integer scoreProfessional;

    private Integer scoreOverall;

    /**
     * 标签（dict_value 列表，XML 查出 JSON 串后由 Java 侧解析）
     */
    private List<String> tags;

    /**
     * 标签 JSON 原始串（仅 XML 查询映射用，Java 侧解析进 tags；不对外序列化）
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String tagsJson;

    private String comment;

    private LocalDateTime createTime;
}
