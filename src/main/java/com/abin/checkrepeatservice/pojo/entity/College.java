package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学院实体类
 * 对应数据库表：college
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("college")
public class College extends BaseEntity {

    /**
     * 学院编码（唯一）
     */
    @TableField("college_code")
    private String collegeCode;

    /**
     * 学院名称
     */
    @TableField("college_name")
    private String collegeName;

    /**
     * 学院描述（可选）
     */
    @TableField("college_desc")
    private String collegeDesc;

}
