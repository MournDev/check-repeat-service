package com.abin.checkrepeatservice.user.mapper;

import com.abin.checkrepeatservice.pojo.entity.SysNotice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 站内信Mapper接口
 *
 * 说明：分页/筛选/统计等查询统一走 MyBatis-Plus 的 LambdaQueryWrapper（实体驱动，
 * 列清单由实体决定，新增字段不会漏）。这里只保留三条手写 SQL —— 它们都带
 * 「按 user_id 圈定归属」的条件，写得比 Wrapper 直白，且无需改列清单。
 */
@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /**
     * 统计用户未读通知数（前端小红点用）
     * @param userId 用户ID
     * @return 未读数量
     */
    Integer countUnreadByUserId(@Param("userId") Long userId);

    /**
     * 标记单个通知为已读（含阅读时间）
     * @param noticeId 通知ID
     * @param userId 用户ID（归属校验，防止越权标记别人的通知）
     * @param readTime 阅读时间
     * @return 影响行数
     */
    int markAsRead(
            @Param("noticeId") Long noticeId,
            @Param("userId") Long userId,
            @Param("readTime") LocalDateTime readTime
    );

    /**
     * 批量标记用户所有未读通知为已读
     * @param userId 用户ID
     * @param readTime 阅读时间（统一设置为当前时间）
     * @return 成功标记的数量
     */
    int markAllUnreadAsRead(
            @Param("userId") Long userId,
            @Param("readTime") LocalDateTime readTime
    );
}
