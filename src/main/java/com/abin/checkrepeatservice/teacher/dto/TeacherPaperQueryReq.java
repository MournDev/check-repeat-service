package com.abin.checkrepeatservice.teacher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 教师论文查询请求 DTO
 */
@Data
public class TeacherPaperQueryReq {
    /**
     * 学生姓名（模糊查询，可选）
     */
    private String studentName;

    /**
     * 论文标题（模糊查询，可选）
     */
    private String paperTitle;

    /**
     * 论文状态（可选）
     */
    private String paperStatus;

    /**
     * 是否包含已撤回的论文（默认 false）
     */
    private Boolean includeWithdrawn = false;

    /**
     * 当前页码（默认 1）
     */
    @Min(value = 1, message = "页码不能小于 1")
    private Integer currentPage = 1;

    /**
     * 每页条数（默认 10，最大 50）
     */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 50, message = "每页条数不能超过 50")
    private Integer pageSize = 10;
}
