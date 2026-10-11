package com.abin.checkrepeatservice.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 学生入批请求（论文全生命周期 P3，管理端 POST /api/admin/defense/batch/{id}/enroll）。
 *
 * 约束：
 *  - 论文当前状态须为 reviewing（评阅全部完成）或 mid_defense（中期通过，准备终期），
 *    由 Service 按批次类型经阶段门推进到 mid_defense / final_defense；
 *  - 同一 (batch_id, paper_id) 唯一索引兜底重复入册；
 *  - defense_order 可空，未指定时由系统按入册顺序自动编排。
 */
@Data
public class DefenseEnrollReq {

    /**
     * 批次ID（路径参数，校验防漏传）
     */
    @NotNull(message = "批次ID不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long batchId;

    /**
     * 论文ID列表（至少 1 篇）
     */
    @NotEmpty(message = "论文列表不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private List<Long> paperIds;
}
