package com.abin.checkrepeatservice.pojo.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统参数实体类：存储全局基础配置（单例表，仅1条记录）
 * 对应数据库表：system_param
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("system_param") // 数据库表名映射
public class SystemParam extends BaseEntity{

    /**
     * 论文最大大小（非空，单位：字节，默认209715200=200MB）
     * 对应表字段：max_paper_size
     */
    @TableField("max_paper_size")
    private Long maxPaperSize;

    /**
     * 最大并发查重数（非空，默认10，控制同时执行的查重任务数量）
     * 对应表字段：max_concurrent_check
     */
    @TableField("max_concurrent_check")
    private Integer maxConcurrentCheck;

    /**
     * JWT令牌有效期（非空，单位：毫秒，默认86400000=24小时）
     * 对应表字段：jwt_expiration
     */
    @TableField("jwt_expiration")
    private Long jwtExpiration;
    
    /**
     * 默认重复率阈值（非空，单位：百分比，默认20.00）
     * 对应表字段：default_threshold
     */
    @TableField("default_threshold")
    private Double defaultThreshold;
    
    /**
     * 文件存储类型（非空，LOCAL-本地存储，MINIO-MinIO存储）
     * 对应表字段：storage_type
     */
    @TableField("storage_type")
    private String storageType;
    
    /**
     * 系统维护状态（非空，0-正常运行，1-维护中）
     * 对应表字段：maintenance_status
     */
    @TableField("maintenance_status")
    private Integer maintenanceStatus;
    
    /**
     * 维护开始时间（可为空）
     * 对应表字段：maintenance_start_time
     */
    @TableField("maintenance_start_time")
    private LocalDateTime maintenanceStartTime;
    
    /**
     * 维护结束时间（可为空）
     * 对应表字段：maintenance_end_time
     */
    @TableField("maintenance_end_time")
    private LocalDateTime maintenanceEndTime;
    
    /**
     * 维护公告（可为空）
     * 对应表字段：maintenance_notice
     */
    @TableField("maintenance_notice")
    private String maintenanceNotice;

    /**
     * 是否启用自动备份（0-禁用，1-启用）
     * 对应表字段：backup_enabled
     * 运行时以本字段为准，yml 的 backup.enabled 仅作取不到时的兜底默认值
     */
    @TableField("backup_enabled")
    private Integer backupEnabled;

    /**
     * 备份文件保留天数
     * 对应表字段：backup_retention_days
     */
    @TableField("backup_retention_days")
    private Integer backupRetentionDays;


}
