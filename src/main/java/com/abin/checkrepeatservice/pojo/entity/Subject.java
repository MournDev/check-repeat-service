package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学科领域实体类（对应 subject 表）
 *
 * 取代原先 sys_dict_data 里 dict_type='subject_field' 的那棵「字符串树」——
 * 原做法没有 parent_id，层级靠 dict_value 里的连字符约定出来，只能整树读出再在
 * 内存里用 startsWith 挂子树；现在是真正的父子关系。
 *
 * 几个刻意的设计：
 *  1. id 是写死的 13 位（见 种子数据.sql 段 3B），不是雪花 ID。13 位远小于 2^53，
 *     过 JS 不会丢末位，所以这一处不需要靠 @JsonFormat(STRING) 兜底 —— 但 BaseEntity
 *     的 id 已经带了这个注解，parentId 也就跟着标成 STRING，否则前端拿到
 *     「id 是字符串、parentId 是数字」这种混合形状，建树时的等值比较会全部失配。
 *  2. code 是对外稳定标识（paper_info.subject_code 存的就是它），创建后不可改；
 *     name 只是显示名，可随时改。
 *  3. 停用走 isEnabled=0，不走删除；真要删必须先在管理端查引用（论文/阈值/
 *     教师研究方向/专业映射）且没有子节点，才允许物理删除。
 *     原因：uk_subject_code 不含 is_deleted，软删后重加同 code 会直接撞唯一键。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("subject")
public class Subject extends BaseEntity {

    /**
     * 父学科ID：一级门类为 NULL，二级指向所属门类的 id
     */
    @TableField("parent_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long parentId;

    /**
     * 层级：1-一级门类，2-二级学科。由 parentId 推导，不接受前端传值
     */
    @TableField("level")
    private Integer level;

    /**
     * 学科编码（唯一，如 ENG / ENG-CS）。对外稳定标识，创建后不可改
     */
    @TableField("code")
    private String code;

    /**
     * 学科名称（如 工学 / 人工智能），可改
     */
    @TableField("name")
    private String name;

    /**
     * 同层排序
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 是否启用：0-停用，1-启用。停用后不再出现在选择器/阈值候选中，已引用的数据不受影响
     */
    @TableField(value = "is_enabled", fill = FieldFill.INSERT)
    private Integer isEnabled;

}
