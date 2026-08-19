package com.abin.checkrepeatservice.admin.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.List;

/**
 * 教师审核效率统计DTO
 */
@Data
public class AuditEfficiencyStatDTO {

    @ApiModelProperty(value = "教师轴（如[张老师, 李老师,...]）")
    private List<String> teacherAxis;

    @ApiModelProperty(value = "各教师待审核任务数")
    private List<Integer> pendingAuditCount;

    @ApiModelProperty(value = "各教师已审核任务数")
    private List<Integer> completedAuditCount;

    @ApiModelProperty(value = "各教师平均审核耗时（分钟，保留1位小数）")
    private List<Double> avgAuditTime;

    @ApiModelProperty(value = "统计周期内总待审核任务数")
    private Integer totalPendingCount;

    @ApiModelProperty(value = "统计周期内总已审核任务数")
    private Integer totalCompletedCount;
}
