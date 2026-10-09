package com.abin.checkrepeatservice.student.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PaperQueryRequest {
    /**
     * 学生ID
     */
    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    /**
     * 论文状态
     */
    private String paperStatus;

    /**
     * 论文类型筛选（PaperTypeEnum 的 code），可空。
     *
     * 学生端「查重历史」页的类型下拉一直在传这个参数，但此前本类没有这个字段，
     * 值被 Jackson 静默丢弃 —— 筛选看起来能点、实际毫无作用。
     * 取值域由 PaperTypeEnum 决定，前端下拉的选项也来自它。
     */
    private String paperType;

    /**
     * 论文名称（模糊搜索）
     */
    private String paperTitle;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startTime;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endTime;

    /**
     * 当前页码
     */
    private Integer pageNum;

    /**
     * 每页大小
     */
    private Integer pageSize;

}
