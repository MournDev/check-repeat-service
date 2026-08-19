package com.abin.checkrepeatservice.admin.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.List;

/**
 * 学生论文提交趋势统计结果DTO
 */
@Data
public class SubmitTrendStatDTO {

    @ApiModelProperty(value = "时间轴（如[2024-09-01, 2024-09-02,...]）")
    private List<String> timeAxis; // 按统计维度生成的时间列表

    @ApiModelProperty(value = "每日提交量（与时间轴一一对应）")
    private List<Integer> dailySubmitCount; // 对应时间的提交人数

    @ApiModelProperty(value = "每日未提交量（与时间轴一一对应）")
    private List<Integer> dailyUnsubmitCount; // 对应时间的未提交人数

    @ApiModelProperty(value = "统计周期内总提交人数")
    private Integer totalSubmitCount;

    @ApiModelProperty(value = "统计周期内总未提交人数")
    private Integer totalUnsubmitCount;

    @ApiModelProperty(value = "总体提交率（保留2位小数，如85.50）")
    private Double submitRate;
}
