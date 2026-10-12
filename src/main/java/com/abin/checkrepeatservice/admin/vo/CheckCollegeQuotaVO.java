package com.abin.checkrepeatservice.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 学院月度查重额度 VO（论文全生命周期 P5）：管理端列表展示用，补学院名称。
 */
@Data
public class CheckCollegeQuotaVO {

    /**
     * 额度记录ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 学院ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long collegeId;

    /**
     * 学院名称（关联 college 表）
     */
    private String collegeName;

    /**
     * 额度月份（yyyy-MM）
     */
    private String quotaMonth;

    /**
     * 当月总额度（次）
     */
    private Integer totalQuota;

    /**
     * 当月已用额度（次）
     */
    private Integer usedQuota;
}
