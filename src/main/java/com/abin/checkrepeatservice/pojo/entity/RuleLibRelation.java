package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规则库关系实体类
 * 记录查重规则与对比库的关联关系
 */
@Data
@TableName("rule_lib_relation")
public class RuleLibRelation {

    /**
     * 主键ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 规则ID（关联check_rule.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("rule_id")
    private Long ruleId;

    /**
     * 对比库ID（关联compare_lib.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("lib_id")
    private Long libId;

    /**
     * 软删除标记（0-未删除，1-已删除）
     */
    @TableField("is_deleted")
    private Integer isDeleted;

    /**
     * 创建人ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("created_by")
    private Long createdBy;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 更新人ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableField("updated_by")
    private Long updatedBy;

    /**
     * 更新时间
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;

    // 冗余字段 - 便于查询显示
    /**
     * 规则名称
     */
    @TableField(exist = false)
    private String ruleName;

    /**
     * 对比库名称
     */
    @TableField(exist = false)
    private String libName;
}