package com.abin.checkrepeatservice.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评阅任务分配请求（论文全生命周期 P2，管理端 POST /api/admin/review-assign/allocate）。
 *
 * assignType=manual 时 reviewerIds 必填；assignType=auto 时 count 必填（系统挑选候选教师）。
 */
@Data
public class ReviewAllocateReq {

    @NotNull(message = "论文ID不能为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long paperId;

    /**
     * 分配方式：auto-自动 / manual-手动
     */
    @Pattern(regexp = "auto|manual", message = "分配方式只能是 auto 或 manual")
    private String assignType;

    /**
     * 评阅教师ID列表（manual 必填）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private List<Long> reviewerIds;

    /**
     * 自动分配人数（auto 必填，1~10）
     */
    @Min(value = 1, message = "自动分配人数至少为1")
    @Max(value = 10, message = "自动分配人数最多为10")
    private Integer count;

    /**
     * 评阅类型：advisor-导师评阅 / peer-交叉评阅 / blind-盲审（默认 peer）
     */
    @Pattern(regexp = "advisor|peer|blind", message = "评阅类型只能是 advisor、peer 或 blind")
    private String reviewType;

    /**
     * 评阅方式：online-线上 / offline-线下（默认 online）
     */
    @Pattern(regexp = "online|offline", message = "评阅方式只能是 online 或 offline")
    private String method;

    /**
     * 评阅截止时间（可空；为空时系统按默认天数计算）
     */
    private LocalDateTime deadline;

    /**
     * 评阅针对的论文版本号（可空；默认取该论文最新提交版本）
     */
    private Integer submitVersion;

    /**
     * 评阅意见备注（可空，仅管理端备注用途）
     */
    @Size(max = 200, message = "备注长度不能超过200字符")
    private String remark;
}
