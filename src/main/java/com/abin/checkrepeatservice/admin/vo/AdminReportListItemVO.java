package com.abin.checkrepeatservice.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理员报告列表项VO
 * 说明：check_report 表本身不含标题/姓名/相似度/状态，字段均来自关联表，
 * 由 AdminReportMapper.xml 的四表关联查询填充。
 */
@Data
public class AdminReportListItemVO {

    /**
     * 报告ID（关联check_report.id，前端用作 reportId 调用详情与导出）
     * 雪花ID序列化为字符串，避免前端 Number 精度丢失
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 报告编号
     */
    private String reportNo;

    /**
     * 论文标题（来自paper_info.paper_title）
     */
    private String paperTitle;

    /**
     * 学生姓名（来自sys_user.real_name）
     */
    private String studentName;

    /**
     * 相似度（来自check_task.check_rate）
     */
    private BigDecimal similarity;

    /**
     * 查重时间（来自check_task.end_time）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkTime;

    /**
     * 查重状态（来自check_task.check_status：pending/checking/completed/failure）
     */
    private String status;
}
