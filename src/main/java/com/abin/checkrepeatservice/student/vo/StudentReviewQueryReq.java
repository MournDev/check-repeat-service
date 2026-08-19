package com.abin.checkrepeatservice.student.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 学生端审核结果查询请求DTO
 */
@Data
public class StudentReviewQueryReq {
    /**
     * 论文状态（可选：2-待审核，3-审核通过，4-审核不通过）
     */
    private Integer paperStatus; // 前端传参：paperStatus=3

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
