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
 * 知识库文章实体类（对应 knowledge_article 表）
 *
 * 不继承 BaseEntity：该表只有 author_id / create_time / update_time / is_deleted，
 * 【没有】create_by / update_by 列。继承 BaseEntity 会让 MP 在 INSERT 时带上这两列，
 * 直接报未知列错误（同类先例见 SysNotice、MessageTemplate）。
 *
 * create_time / update_time 由库的 DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP
 * 填充，调用方【不要】手动 setCreateTime / setUpdateTime。
 */
@Data
@TableName("knowledge_article")
public class KnowledgeArticle {

    /**
     * 主键ID（雪花，全局 id-type=ASSIGN_ID）
     *
     * 序列化成字符串下发：雪花是 19 位数字，超出 JS 的 2^53 安全整数范围，
     * 用数字下发会被前端四舍五入，拼出来的 /api/knowledge/articles/{id} 指向不存在的记录。
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 文章标题
     */
    private String title;

    /**
     * 正文（Markdown）
     */
    private String content;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 所属分类ID（关联 knowledge_category.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long categoryId;

    /**
     * 标签（逗号分隔）
     */
    private String tags;

    /**
     * 浏览次数
     */
    private Long viewCount;

    /**
     * 状态：DRAFT-草稿，PUBLISHED-已发布，ARCHIVED-已归档（与前端 KnowledgeManagement 的筛选项一致）
     */
    private String status;

    /**
     * 排序值（越小越靠前）
     */
    private Integer sort;

    /**
     * 作者ID（关联 sys_user.id，由当前登录管理员填充）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long authorId;

    /**
     * 作者姓名（冗余字段，列表直接展示，避免再查一次 sys_user）
     */
    private String authorName;

    /**
     * 创建时间（由库默认值填充）
     */
    private LocalDateTime createTime;

    /**
     * 更新时间（由库 ON UPDATE 填充）
     */
    private LocalDateTime updateTime;

    /**
     * 软删除：0-未删除，1-已删除（MP 逻辑删除，查询自动追加 is_deleted = 0）
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 分类名称（冗余展示字段，库里没有该列，由 Service 批量回填）
     */
    @TableField(exist = false)
    private String categoryName;
}
