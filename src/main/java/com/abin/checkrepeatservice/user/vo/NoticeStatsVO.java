package com.abin.checkrepeatservice.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知统计VO（管理端「通知管理」页顶部四张卡片）
 *
 * 用 VO 而不是 Map：字段名写错是编译错误，而不是页面上静静地显示 0。
 * 字段名必须与前端 `reactive({ total: 0, unread: 0, urgent: 0, read: 0 })` 的键一一对应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoticeStatsVO {

    /** 通知总数（未删除） */
    private Integer total;

    /** 未读数量（is_read = 0） */
    private Integer unread;

    /** 紧急通知数量（priority = 2） */
    private Integer urgent;

    /** 已读数量（is_read = 1） */
    private Integer read;
}
