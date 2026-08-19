package com.abin.checkrepeatservice.admin.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.List;

/**
 * 论文查重结果分析DTO（按专业/年级维度）
 */
@Data
public class CheckResultStatDTO {

    @ApiModelProperty(value = "分组轴（如专业名[计算机科学与技术, 软件工程,...]或年级[2021,2022,...]）")
    private List<String> groupAxis;

    @ApiModelProperty(value = "各组合格人数（重复率≤阈值）")
    private List<Integer> qualifiedCount;

    @ApiModelProperty(value = "各组不合格人数（重复率>阈值）")
    private List<Integer> unqualifiedCount;

    @ApiModelProperty(value = "各组平均重复率（保留2位小数）")
    private List<Double> avgRepeatRate;

    @ApiModelProperty(value = "总体合格率（保留2位小数，如92.30）")
    private Double totalQualifiedRate;
}
