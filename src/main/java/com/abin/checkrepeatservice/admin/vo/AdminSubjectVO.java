package com.abin.checkrepeatservice.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.List;

/**
 * 管理端学科领域列表项（树形）。
 *
 * 带 referenceCount / hasChildren 是为了让页面在点「删除」**之前**就能把
 * 「有几处引用、下面还有几个二级学科」摆给管理员看，而不是点了才被拒。
 * 删除的最终裁决仍在服务端（列表数据可能是几分钟前拉的），前端这份只用于提示。
 */
@Data
public class AdminSubjectVO {

    /**
     * 学科ID。种子数据是 13 位、管理端新建的是 19 位雪花ID，
     * 两种都序列化成字符串——19 位过 2^53，给数字形态前端会丢末位。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long parentId;

    /** 层级：1-一级门类，2-二级学科 */
    private Integer level;

    /** 学科编码（对外稳定标识，创建后不可改） */
    private String code;

    /** 学科名称（可改） */
    private String name;

    /** 同层排序 */
    private Integer sortNo;

    /** 是否启用：0-停用，1-启用 */
    private Integer isEnabled;

    /** 被引用次数（论文 + 阈值配置 + 教师研究方向 + 专业映射） */
    private Integer referenceCount;

    /** 是否有下级学科（有子节点不允许删除） */
    private Boolean hasChildren;

    /** 二级学科的列表（仅一级门类有值；二级为 null，前端表格靠它展开） */
    private List<AdminSubjectVO> children;

}
