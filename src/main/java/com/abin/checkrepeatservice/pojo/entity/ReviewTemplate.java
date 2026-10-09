package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审核意见模板实体类
 * 对应数据库表：review_template
 *
 * <p>scenarios 列是 MySQL 的 json 类型，这里映射成 String 而不是 List&lt;String&gt;，
 * 与 {@link ReviewWorkflow#getSteps()} 保持一致：序列化/反序列化统一放在 Service 层
 * 用 ObjectMapper 做。全项目没有 MyBatis-Plus JSON TypeHandler 的先例
 * （无 JacksonTypeHandler / autoResultMap），隐式依赖那套配置只会让
 * 「读回来是 null」这类问题更难查。</p>
 *
 * <p>表里还有 delete_by / delete_time 两列，这里不映射 —— BaseEntity 没有这两个字段，
 * 其它实体也一律不映射，软删只写 is_deleted。列留着为 NULL。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_template")
public class ReviewTemplate extends BaseEntity {

    /**
     * 模板标题
     */
    @TableField("title")
    private String title;

    /**
     * 模板分类（approve-通过类 / modify-修改类 / reject-拒绝类 / suggest-建议类 / format-格式类）
     *
     * <p>取值域由前端定义并本地筛选，后端不做白名单校验 —— 新增一个分类
     * 不应该要求先改后端。</p>
     */
    @TableField("category")
    private String category;

    /**
     * 模板内容
     */
    @TableField("content")
    private String content;

    /**
     * 使用场景（JSON 数组文本，如 ["初审","复审"]）
     */
    @TableField("scenarios")
    private String scenarios;

    /**
     * 是否公开：公开的模板同角色的其它教师也能看到并使用
     */
    @TableField("is_public")
    private Boolean isPublic;

    /**
     * 被使用次数
     */
    @TableField("usage_count")
    private Integer usageCount;

    /**
     * 最后使用时间，从未使用过则为 NULL
     */
    @TableField("last_used")
    private LocalDateTime lastUsed;

}
