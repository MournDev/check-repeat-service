package com.abin.checkrepeatservice.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 性能配置数据传输对象
 */
@Data
public class PerformanceConfigDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 最大并发数 (1-100)
     */
    @Min(value = 1, message = "最大并发数不能小于1")
    @Max(value = 100, message = "最大并发数不能大于100")
    private Integer maxConcurrent = 20;
    
    /**
     * 查重队列大小 (10-1000)
     */
    @Min(value = 10, message = "队列大小不能小于10")
    @Max(value = 1000, message = "队列大小不能大于1000")
    private Integer queueSize = 100;
    
    /**
     * 缓存策略 ('lru'|'fifo'|'ttl')
     */
    @NotBlank(message = "缓存策略不能为空")
    @Pattern(regexp = "^(lru|fifo|ttl)$", message = "缓存策略只能是lru、fifo或ttl")
    private String cacheStrategy = "lru";
    
    /**
     * 缓存大小 (100-10000) MB
     */
    @Min(value = 100, message = "缓存大小不能小于100MB")
    @Max(value = 10000, message = "缓存大小不能大于10000MB")
    private Integer cacheSize = 1024;
    
    /**
     * 自动清理开关
     */
    private Boolean autoCleanup = true;
    
    /**
     * 清理周期 (1-168) 小时
     */
    @Min(value = 1, message = "清理周期不能小于1小时")
    @Max(value = 168, message = "清理周期不能大于168小时")
    private Integer cleanupInterval = 24;
}