package com.abin.checkrepeatservice.admin.vo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 系统参数配置请求DTO
 */
@Data
public class SystemParamReq {
    /**
     * 论文最大大小（字节，必传，≥10485760=10MB）
     */
    @NotNull(message = "论文最大大小不能为空")
    @DecimalMin(value = "10485760", message = "论文最大大小不能小于10MB")
    private Long maxPaperSize; // 前端传参：maxPaperSize=209715200（200MB）

    /**
     * 最大并发查重数（必传，≥1）
     */
    @NotNull(message = "最大并发查重数不能为空")
    @DecimalMin(value = "1", message = "最大并发查重数不能小于1")
    private Integer maxConcurrentCheck; // 前端传参：maxConcurrentCheck=10

    /**
     * JWT令牌有效期（毫秒，必传，≥3600000=1小时）
     */
    @NotNull(message = "JWT有效期不能为空")
    @DecimalMin(value = "3600000", message = "JWT有效期不能小于1小时")
    private Long jwtExpiration; // 前端传参：jwtExpiration=86400000（24小时）
}
