package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库分类实体类（对应 knowledge_category 表）
 *
 * 不继承 BaseEntity：该表没有 create_by / update_by 列，理由同 KnowledgeArticle。
 */
@Data
@TableName("knowledge_category")
public class KnowledgeCategory {

    /**
     * 主键ID（雪花，序列化成字符串下发，防止 JS 精度丢失）
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 分类编码（唯一，前端按编码筛选文章列表）
     */
    private String code;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 图标标识
     */
    private String icon;

    /**
     * 排序值（越小越靠前）
     */
    private Integer sort;

    /**
     * 状态：ACTIVE-启用，其余视为停用
     */
    private String status;

    /**
     * 创建时间（由库默认值填充）
     */
    private LocalDateTime createTime;

    /**
     * 更新时间（由库 ON UPDATE 填充）
     */
    private LocalDateTime updateTime;

    /**
     * 软删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 该分类下已发布文章数（冗余展示字段，库里没有该列，由 Service 批量统计回填；
     * 前端 HelpCenter 的分类徽标用的是 articleCount）
     */
    @TableField(exist = false)
    private Integer articleCount;
}
