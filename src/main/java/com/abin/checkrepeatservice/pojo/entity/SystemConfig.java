package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体类
 */
@Data
@TableName("system_config")
public class SystemConfig {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 配置键
     */
    @TableField("config_key")
    private String configKey;
    
    /**
     * 配置值（JSON格式存储）
     */
    @TableField("config_value")
    private String configValue;
    
    /**
     * 配置描述
     */
    @TableField("description")
    private String description;
    
    /**
     * 创建时间（由库的 DEFAULT CURRENT_TIMESTAMP 填充，调用方【不要】再 setCreateTime）
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    /**
     * 是否删除 0-未删除 1-已删除
     */
    @TableField("is_deleted")
    @TableLogic
    private Integer isDeleted;
}