package com.abin.checkrepeatservice.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 学院月度查重额度保存请求（论文全生命周期 P5）：
 * 管理端 POST /api/admin/config/check-quota/save，按 (college_id, quota_month) upsert。
 */
@Data
public class CheckQuotaSaveReq {

    /**
     * 学院ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @NotNull(message = "学院ID不能为空")
    private Long collegeId;

    /**
     * 当月总额度（次）
     */
    @NotNull(message = "当月总额度不能为空")
    @Min(value = 0, message = "当月总额度不能小于0")
    private Integer totalQuota;

    /**
     * 额度月份（yyyy-MM），不传默认当前月
     */
    private String quotaMonth;
}
