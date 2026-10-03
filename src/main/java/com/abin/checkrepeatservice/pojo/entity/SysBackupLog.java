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
 * 备份日志实体：对应数据库表 sys_backup_log
 *
 * <p>注意：本类<b>不继承</b> {@link BaseEntity}。sys_backup_log 表里
 * 没有 create_by / update_by 两列，继承 BaseEntity 会让 MyBatis-Plus
 * 往 INSERT 里带上这两列，直接报 Unknown column。</p>
 *
 * <p>由此带来的两个后果：
 * <ul>
 *   <li>{@code createTime}/{@code updateTime} 不会被 {@code MyMetaObjectHandler}
 *       自动填充（它只处理 instanceof BaseEntity 的对象），必须由 service 手工赋值；</li>
 *   <li>表里 create_time 的默认值是 NULL 而非 CURRENT_TIMESTAMP，
 *       不手工赋值的话审计日志列表按时间排序/展示会是空的。</li>
 * </ul>
 * </p>
 */
@Data
@TableName("sys_backup_log")
public class SysBackupLog {

    /** 主键ID（雪花ID，无自增） */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 备份文件名 */
    @TableField("file_name")
    private String fileName;

    /** 文件大小（字节） */
    @TableField("file_size")
    private Long fileSize;

    /** 备份状态（SUCCESS/FAILED） */
    @TableField("status")
    private String status;

    /** 备份开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 备份结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    /** 错误信息 */
    @TableField("error_message")
    private String errorMessage;

    /** 备份类型（AUTO/MANUAL） */
    @TableField("backup_type")
    private String backupType;

    /** 软删除标记（0-未删除，1-已删除）；字段名必须精确叫 isDeleted，全局 logic-delete-field 依赖它 */
    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;

    /** 删除人ID */
    @TableField("delete_by")
    private Long deleteBy;

    /** 删除时间 */
    @TableField("delete_time")
    private LocalDateTime deleteTime;

    /** 创建时间（不会被自动填充，需手工赋值） */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 更新时间（不会被自动填充，需手工赋值） */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
