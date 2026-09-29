package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统站内信实体类（对应sys_notice表）
 */
@Data
@TableName("sys_notice")
public class SysNotice {

    /**
     * 主键ID
     *
     * 必须用 ASSIGN_ID（雪花，由 MP 在 Java 侧生成），不能用 AUTO：
     * sys_notice.id 在库里的定义是 `bigint NOT NULL`，既【没有】AUTO_INCREMENT、
     * 也没有默认值（本仓库 schema 的房规就是雪花主键，见 sys_user / sys_operation_log；
     * 全局 mybatis-plus.global-config.db-config.id-type 也是 ASSIGN_ID）。
     * 用 AUTO 时 MP 不生成主键、并把该列排除在 INSERT 之外，而列无自增无默认值，
     * 严格模式下插入会直接报 1048 → 通知一条都存不进去。
     *
     * 序列化成字符串：雪花是 19 位数字，超出 JS 的 2^53 安全整数范围，
     * 用数字下发会被前端四舍五入，拼出来的 /api/notices/read/{id} 指向不存在的记录，
     * 且可能误伤别人的行。与 BaseEntity.id / SystemMessage 的既有写法保持一致。
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 接收用户ID（关联sys_user.id）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 通知类型：1-提交成功，2-查重完成，3-审核结果，4-截止提醒，
     * 5-系统通知，6-教师分配，7-修改请求，8-其他（与前端 NOTICE_TYPES 对应）
     */
    private Integer noticeType;

    /**
     * 通知标题
     */
    private String noticeTitle;

    /**
     * 通知内容
     */
    private String noticeContent;

    /**
     * 是否已读：0-未读，1-已读（前端用 === 1 / === 0 做数字比较，不要改成布尔）
     */
    private Integer isRead;

    /**
     * 阅读时间
     */
    private LocalDateTime readTime;

    /**
     * 优先级：0-普通，1-重要，2-紧急（前端「紧急通知」统计取 priority = 2）
     */
    private Integer priority;

    /**
     * 创建时间（由库的 DEFAULT CURRENT_TIMESTAMP 填充，调用方【不要】再 setCreateTime）
     */
    private LocalDateTime createTime;

    /**
     * 软删除：0-未删除，1-已删除（MP 逻辑删除，查询自动追加 is_deleted = 0）
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 用户姓名（冗余字段，便于显示）
     */
    @TableField(exist = false)
    private String userName;

    /**
     * 用户学号/工号（冗余字段，便于显示）
     */
    @TableField(exist = false)
    private String userNo;

    /**
     * 通知类型描述（冗余字段，便于显示）
     */
    @TableField(exist = false)
    private String noticeTypeDesc;

    /**
     * 相关业务ID（如论文ID、任务ID等）
     * 库里有对应列 related_id，详情弹窗要原样展示，同样按字符串下发防止精度丢失
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long relatedId;

    /**
     * 相关业务类型（如paper、task等）
     * 库里有对应列 related_type
     */
    private String relatedType;
}
