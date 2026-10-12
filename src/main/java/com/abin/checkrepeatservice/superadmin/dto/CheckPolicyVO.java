package com.abin.checkrepeatservice.superadmin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检测策略列表项 VO（P5 超管端）：
 * id 按 JACKSON 字符串化输出，避免雪花 ID 前端精度丢失
 */
@Data
public class CheckPolicyVO {

    /**
     * 策略ID（雪花ID，字符串化）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 学位类型：undergraduate/master/doctor
     */
    private String degreeType;

    /**
     * 合格阈值（总相似比百分比）
     */
    private BigDecimal threshold;

    /**
     * 单篇最大相似比上限（百分比）
     */
    private BigDecimal maxPerSource;

    /**
     * 每篇论文最多查重次数（可空=沿用全局配置）
     */
    private Integer maxCheckCount;

    /**
     * 两次查重间隔秒数（可空=沿用全局配置）
     */
    private Integer checkInterval;

    /**
     * 是否启用（1-启用 0-停用）
     */
    private Integer enabled;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
