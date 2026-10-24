package com.abin.checkrepeatservice.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端超期论文列表项（Wave 3）。
 */
@Data
public class OverduePaperVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    private String paperTitle;

    private String studentName;

    /** 学号（sys_user.username） */
    private String studentNo;

    private String advisorName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long teacherId;

    /** 超期天数（按 submit_time + SLA 计算，已过滤为 ≥1） */
    private Integer overdueDays;

    /** 判定用截止时间（submit_time + SLA） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    private String paperStatus;

    /** 最近 24 小时内是否已催办 */
    private Boolean reminded;

    /** 最近一次催办时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastRemindTime;
}
