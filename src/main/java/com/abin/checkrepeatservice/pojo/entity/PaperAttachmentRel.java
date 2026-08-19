package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 论文-附件关联表实体类
 * 关联论文表（paper_submit）与通用文件表（sys_attachment），支持一篇论文关联多个附件
 */
@Data
@TableName("paper_attachment_rel") // 数据库表名映射
@ApiModel(value = "PaperAttachmentRel", description = "论文-附件关联实体")
public class PaperAttachmentRel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 关联ID（雪花id）
     */
    @TableId(type = IdType.ASSIGN_ID)
    @ApiModelProperty(value = "关联ID（主键）", example = "1001")
    private Long id;

    /**
     * 论文ID（关联 paper_submit.id）
     */
    @ApiModelProperty(value = "论文ID", required = true, example = "123456")
    private Long paperId;

    /**
     * 文件ID（关联 sys_attachment.id）
     */
    @ApiModelProperty(value = "文件ID（通用文件表主键）", required = true, example = "789")
    private Long fileId;

    /**
     * 附件用途（枚举化取值）
     * 示例：PAPER_DRAFT=论文初稿，PAPER_FINAL=论文终稿，CHECK_REPORT=查重报告，REVIEW_ATTACH=审核附件
     */
    @ApiModelProperty(value = "附件用途", example = "PAPER_DRAFT", notes = "可选值：PAPER_DRAFT/PAPER_FINAL/CHECK_REPORT/REVIEW_ATTACH")
    private String attachmentUse;

    /**
     * 逻辑删除标记（0=未删除，1=已删除）
     * MyBatis-Plus自动拦截，查询时默认过滤已删除数据
     */
    @TableLogic
    @ApiModelProperty(value = "逻辑删除标记", hidden = true) // 隐藏Swagger文档显示
    private Integer isDeleted = 0;

    /**
     * 创建人ID（学生ID/管理员ID，继承BaseEntity）
     * 父类BaseEntity已包含，无需重复定义
     */
    private Long createBy;

    /**
     * 创建时间（继承BaseEntity）
     * 父类BaseEntity已包含，无需重复定义
     */
    private LocalDateTime createTime;

}
