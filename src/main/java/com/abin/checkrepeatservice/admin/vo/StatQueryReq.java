package com.abin.checkrepeatservice.admin.vo;


import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 统计查询通用请求DTO：所有统计接口的筛选条件基类
 */
@Data
public class StatQueryReq {

    @ApiModelProperty(value = "统计维度（DAY-按日，WEEK-按周，MONTH-按月）", required = true)
    @NotBlank(message = "统计维度不能为空")
    private String statDimension; // 前端传参：statDimension=DAY

    @ApiModelProperty(value = "开始日期（格式：yyyy-MM-dd）", required = true)
    @NotBlank(message = "开始日期不能为空")
    private String startDate; // 前端传参：startDate=2024-09-01

    @ApiModelProperty(value = "结束日期（格式：yyyy-MM-dd）", required = true)
    @NotBlank(message = "结束日期不能为空")
    private String endDate; // 前端传参：endDate=2024-09-30

    @ApiModelProperty(value = "专业ID（可选，不传则统计全部专业）")
    private Long majorId; // 前端传参：majorId=1546278765432123481（可选）

    @ApiModelProperty(value = "年级（可选，不传则统计全部年级，如2021）")
    private Integer grade; // 前端传参：grade=2021（可选）

    @ApiModelProperty(value = "教师ID（仅审核效率统计用，可选）")
    private Long teacherId; // 前端传参：teacherId=1546278765432123491（可选）
}
