package com.abin.checkrepeatservice.teacher.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 待审核论文查询请求DTO
 */
@Data
public class ReviewQueryReq {
    /**
     * 学生姓名（模糊查询，可选）
     */
    private String studentName; // 前端传参：studentName=周

    /**
     * 论文标题（模糊查询，可选）
     */
    private String paperTitle; // 前端传参：paperTitle=查重

    /**
     * 审核结果（可选）：3-审核通过，4-审核不通过。
     * 仅用于已审核列表筛选，待审核列表忽略该参数。
     */
    private Integer reviewStatus;

    /**
     * 当前页码（默认1）
     */
    @Min(value = 1, message = "页码不能小于1")
    private Integer currentPage = 1; // 前端传参：currentPage=1

    /**
     * 每页条数（默认10，最大20）
     */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 20, message = "每页条数不能超过20")
    private Integer pageSize = 10; // 前端传参：pageSize=10
}
