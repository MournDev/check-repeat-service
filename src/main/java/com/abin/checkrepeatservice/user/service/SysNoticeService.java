package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.pojo.entity.SysNotice;
import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 站内信服务接口（保持原有逻辑：分页查询、未读统计、标记已读）
 */
public interface SysNoticeService extends IService<SysNotice> {

    /**
     * 核心方法：发送通知（站内信+系统内邮箱，异步执行）
     * @param userId 接收用户ID（关联sys_user.id）
     * @param noticeType 通知类型：1-提交成功，2-查重完成，3-审核结果，4-截止提醒
     * @param title 通知标题（站内信标题+邮件主题）
     * @param content 通知内容（支持换行，邮箱会自动转为HTML格式）
     * @param sendEmail 是否同时发送系统内邮箱（true=发送，false=仅站内信）
     */
    void sendNotice(Long userId, Integer noticeType, String title, String content, boolean sendEmail);

    /**
     * 分页查询用户通知列表（原有逻辑：支持已读/未读筛选，按时间倒序）
     * @param userId 当前登录用户ID
     * @param isRead 是否已读（0=未读，1=已读，null=查询所有）
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页条数（默认10）
     * @return 分页结果VO（含总条数、当前页数据）
     */
    PageResultVO<SysNotice> getUserNoticePage(Long userId, Integer isRead, Integer pageNum, Integer pageSize);

    /**
     * 统计用户未读通知数（原有逻辑：用于前端显示未读小红点）
     * @param userId 当前登录用户ID
     * @return 未读通知数量（>=0）
     */
    Integer getUnreadNoticeCount(Long userId);

    /**
     * 标记单个通知为已读（原有逻辑：带权限校验，仅能标记自己的通知）
     * @param noticeId 通知ID（sys_notice.id）
     * @param userId 当前登录用户ID（防止越权操作）
     * @return true=标记成功，false=标记失败（通知不存在/已读/越权）
     */
    boolean markNoticeAsRead(Long noticeId, Long userId);

    /**
     * 标记所有未读通知为已读（原有逻辑：批量操作，提升用户体验）
     * @param userId 当前登录用户ID
     * @return 成功标记的数量
     */
    Integer markAllUnreadAsRead(Long userId);
}