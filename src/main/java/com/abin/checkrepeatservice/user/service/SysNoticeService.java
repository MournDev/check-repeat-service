package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.pojo.entity.SysNotice;
import com.abin.checkrepeatservice.user.vo.NoticeStatsVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 站内信服务接口
 *
 * 所有方法都以 userId 作为第一个语义参数：每条查询与写入都必须落在
 * 「当前用户自己的通知」范围内，否则 clearAllNotices 会清空全表、
 * deleteNotice 能删掉别人的通知。
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
     * 分页查询用户通知列表（支持类型/已读/优先级筛选，按创建时间倒序）
     *
     * 返回 MyBatis-Plus 的 Page（序列化为 {records,total,size,current,pages}），
     * 不要换成 PageResultVO —— 它没有 total 字段，前端分页器会静默显示 0。
     *
     * @param userId 当前用户ID
     * @param noticeType 通知类型筛选（null=全部）
     * @param isRead 是否已读（0=未读，1=已读，null=全部）
     * @param priority 优先级筛选（null=全部）
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页条数
     */
    Page<SysNotice> getNoticePageForUser(Long userId, Integer noticeType, Integer isRead,
                                        Integer priority, Integer pageNum, Integer pageSize);

    /**
     * 统计用户通知概况（总数/未读/紧急/已读）
     * @param userId 当前用户ID
     */
    NoticeStatsVO getNoticeStats(Long userId);

    /**
     * 查询用户最新通知（按创建时间倒序，最多 limit 条）
     * @param userId 当前用户ID
     * @param limit 条数上限（内部会收敛到 1~50）
     */
    List<SysNotice> getLatestNotices(Long userId, Integer limit);

    /**
     * 按优先级统计用户未读通知数
     * @param userId 当前用户ID
     * @return 固定包含 0/1/2 三个键的映射（没有对应通知时为 0，不是缺键）
     */
    Map<Integer, Integer> getUnreadCountByPriority(Long userId);

    /**
     * 统计用户未读通知数（前端显示未读小红点）
     * @param userId 当前登录用户ID
     * @return 未读通知数量（>=0）
     */
    Integer getUnreadNoticeCount(Long userId);

    /**
     * 批量标记通知为已读（仅限当前用户自己的、当前未读的通知）
     * @param noticeIds 通知ID集合
     * @param userId 当前用户ID（归属校验）
     * @return 实际影响行数
     */
    Integer batchMarkAsRead(List<Long> noticeIds, Long userId);

    /**
     * 标记单个通知为已读（带归属校验，仅能标记自己的通知）
     * @param noticeId 通知ID（sys_notice.id）
     * @param userId 当前登录用户ID（防止越权操作）
     * @return true=标记成功，false=标记失败（通知不存在/已读/越权）
     */
    boolean markNoticeAsRead(Long noticeId, Long userId);

    /**
     * 标记所有未读通知为已读
     * @param userId 当前登录用户ID
     * @return 成功标记的数量
     */
    Integer markAllUnreadAsRead(Long userId);

    /**
     * 删除单条通知（逻辑删除，仅限当前用户自己的）
     * @param noticeId 通知ID
     * @param userId 当前用户ID（归属校验）
     * @return 实际影响行数（0 表示通知不存在或不属于当前用户）
     */
    Integer deleteNotice(Long noticeId, Long userId);

    /**
     * 批量删除通知（逻辑删除，仅限当前用户自己的）
     * @param noticeIds 通知ID集合
     * @param userId 当前用户ID（归属校验）
     * @return 实际影响行数
     */
    Integer batchDeleteNotices(List<Long> noticeIds, Long userId);

    /**
     * 清空当前用户的全部通知（逻辑删除）
     * @param userId 当前用户ID
     * @return 实际影响行数
     */
    Integer clearAllNotices(Long userId);

    /**
     * 给当前用户插入一条测试通知（同步插入，便于前端拿到 200 后立刻刷新到数据）
     * @param userId 当前用户ID
     * @param priority 优先级：0-普通，2-紧急（null 视为 0）
     * @return 插入后的实体（含生成的 id 与 createTime）
     */
    SysNotice createTestNotice(Long userId, Integer priority);
}
