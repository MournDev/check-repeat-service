package com.abin.checkrepeatservice.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业务事件日聚合指标
 *
 * 按 (event, result, stat_date) 聚合，每次事件 upsert 累加 event_count。
 *
 * 注意：本类【刻意不继承 BaseEntity】。
 * BaseEntity 带 @TableLogic 软删除注解，而 MySQL 没有部分唯一索引，
 * 一旦某行被软删除，唯一键 uk_event_result_date 仍被它占着，
 * 后续同 (event,result,stat_date) 的插入会变成更新到已删除行，数据静默不可见。
 * 因此 isDeleted 只作为普通列保留（与仓库既有表结构一致），本表不做软删除。
 */
@Data
@TableName("metric_event_daily")
public class MetricEventDaily {

    /**
     * 主键ID（雪花ID，由业务代码生成并传入，故为 INPUT 而非 ASSIGN_ID）
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 事件类型：user_login / user_register / paper_submit / paper_review
     */
    private String event;

    /**
     * 结果标签：success / failure / pass / reject
     */
    private String result;

    /**
     * 统计日期
     */
    private LocalDate statDate;

    /**
     * 当日累计次数
     */
    private Long eventCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 软删除标记（0-未删除，1-已删除）。本表不做软删除，恒为 0。
     */
    private Integer isDeleted;
}
