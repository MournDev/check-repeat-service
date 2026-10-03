package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审核工作流配置实体类
 * 对应数据库表：review_workflow
 *
 * 语义上是单行配置表（见 ReviewWorkflowServiceImpl 里"取哪一行"的约定），
 * 结构上表里没有唯一约束，所以定位行号的方式必须与读路径保持一致。
 *
 * steps 列是 MySQL 的 json 类型，这里映射成 String 而不是结构化对象：
 * 序列化/反序列化统一放在 Service 层用 ObjectMapper 做，理由是全项目没有
 * MyBatis-Plus JSON TypeHandler 的先例（见 JacksonTypeHandler 无命中），
 * 隐式依赖 autoResultMap 配置只会让"读回来是 null"这类问题更难查。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("review_workflow")
public class ReviewWorkflow extends BaseEntity {

    /**
     * 工作流名称
     */
    @TableField("name")
    private String name;

    /**
     * 工作流描述
     */
    @TableField("description")
    private String description;

    /**
     * 审核步骤（JSON 数组文本，单个元素结构见 ReviewWorkflowVO.Step）
     */
    @TableField("steps")
    private String steps;

    /**
     * 启用状态
     */
    @TableField("enabled")
    private Boolean enabled;

}
