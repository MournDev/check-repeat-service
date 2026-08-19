package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 比对库实体类：存储查重用的数据源信息（本地库/远程库）
 * 对应数据库表：compare_lib
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("compare_lib") // 数据库表名映射
public class CompareLib extends BaseEntity{

    /**
     * 比对库名称（非空，如“校内本科论文库”）
     * 对应表字段：lib_name
     */
    @TableField("lib_name")
    private String libName;

    /**
     * 比对库编码（非空、唯一，如“CAMPUS_UNDERGRADUATE”）
     * 对应表字段：lib_code
     */
    @TableField("lib_code")
    private String libCode;

    /**
     * 比对库类型（非空，枚举值：LOCAL-本地库，REMOTE-远程库）
     * 对应表字段：lib_type
     */
    @TableField("lib_type")
    private String libType;

    /**
     * 库地址（非空，本地库存路径/远程库URL）
     * 对应表字段：lib_url
     */
    @TableField("lib_url")
    private String libUrl;

    /**
     * 是否启用（非空，0-禁用，1-启用，默认1）
     * 对应表字段：is_enabled
     */
    @TableField(value = "is_enabled", fill = FieldFill.INSERT)
    private Integer isEnabled;

    /**
     * 比对库描述（可选，如“2010-2024年校内本科毕业论文集合”）
     * 对应表字段：description
     */
    @TableField("description")
    private String description;

}
