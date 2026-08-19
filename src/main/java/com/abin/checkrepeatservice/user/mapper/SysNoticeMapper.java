package com.abin.checkrepeatservice.user.mapper;

import com.abin.checkrepeatservice.pojo.entity.SysNotice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内信Mapper接口（完整版：含分页查询、总条数统计、批量标记方法）
 */
@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /**
     * 分页查询用户通知（原有逻辑：支持已读筛选）
     * @param userId 用户ID
     * @param isRead 是否已读（0=未读，1=已读，null=所有）
     * @param start 分页起始位置（从0开始）
     * @param pageSize 每页条数
     * @return 通知列表（按创建时间倒序）
     */
    List<SysNotice> selectByUserId(
            @Param("userId") Long userId,
            @Param("isRead") Integer isRead,
            @Param("start") Integer start,
            @Param("pageSize") Integer pageSize
    );

    /**
     * 统计用户通知总条数（用于分页计算）
     * @param userId 用户ID
     * @param isRead 是否已读（0=未读，1=已读，null=所有）
     * @return 总条数
     */
    Integer countTotalByUserId(
            @Param("userId") Long userId,
            @Param("isRead") Integer isRead
    );

    /**
     * 统计用户未读通知数（原有逻辑：前端小红点用）
     * @param userId 用户ID
     * @return 未读数量
     */
    Integer countUnreadByUserId(@Param("userId") Long userId);

    /**
     * 标记单个通知为已读（含阅读时间）
     * @param noticeId 通知ID
     * @param userId 用户ID（权限校验）
     * @param readTime 阅读时间
     * @return 影响行数
     */
    int markAsRead(
            @Param("noticeId") Long noticeId,
            @Param("userId") Long userId,
            @Param("readTime") LocalDateTime readTime
    );

    /**
     * 批量标记用户所有未读通知为已读（原有逻辑：批量操作）
     * @param userId 用户ID
     * @param readTime 阅读时间（统一设置为当前时间）
     * @return 成功标记的数量
     */
    int markAllUnreadAsRead(
            @Param("userId") Long userId,
            @Param("readTime") LocalDateTime readTime
    );
}