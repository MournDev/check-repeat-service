package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.SysNotice;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.user.mapper.SysNoticeMapper;
import com.abin.checkrepeatservice.user.service.SysNoticeService;
import com.abin.checkrepeatservice.user.vo.NoticeStatsVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内信服务实现
 *
 * 纪律：每一条查询与写入都必须 eq(SysNotice::getUserId, userId) —— 否则 clearAllNotices
 * 会清空全表、deleteNotice 能删掉别人的通知。
 *
 * create_time 由库的 DEFAULT CURRENT_TIMESTAMP 填充，insert 前【不要】setCreateTime
 * （原因见 SysNotice.createTime 上的注释）。
 */
@Slf4j
@Service
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice> implements SysNoticeService {

    @Resource
    private SysNoticeMapper sysNoticeMapper;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private EmailService emailService;

    // ------------------------------ 发送通知（站内信+邮箱） ------------------------------

    /**
     * 注意限定符是 asyncExecutor（AsyncConfig 里只定义了 checkTaskExecutor 与 asyncExecutor）。
     * 原先写的 "taskExecutor" 这个 bean 不存在，一旦有人调用就会因解析不到限定符而抛异常。
     */
    @Async("asyncExecutor")
    @Override
    public void sendNotice(Long userId, Integer noticeType, String title, String content, boolean sendEmail) {
        try {
            // 1. 保存站内信（核心逻辑，确保登录后可查）
            SysNotice notice = new SysNotice();
            notice.setUserId(userId);
            notice.setNoticeType(noticeType);
            notice.setNoticeTitle(title);
            notice.setNoticeContent(content);
            notice.setIsRead(0); // 初始未读
            notice.setPriority(0);
            notice.setIsDeleted(0);
            sysNoticeMapper.insert(notice);
            log.info("站内信保存成功：userId={}, noticeId={}", userId, notice.getId());

            // 2. 发送系统内邮箱（替代短信，无第三方依赖）
            if (sendEmail) {
                SysUser user = sysUserMapper.selectById(userId);
                if (user != null && StringUtils.hasText(user.getEmail())) {
                    boolean emailResult = emailService.sendNoticeEmail(
                            user.getEmail(),
                            title,
                            content
                    );
                    if (!emailResult) {
                        log.warn("邮箱通知发送失败（站内信已正常保存）：userId={}, email={}", userId, user.getEmail());
                    }
                } else {
                    log.warn("用户邮箱不存在，无法发送邮箱通知：userId={}", userId);
                }
            }
        } catch (Exception e) {
            log.error("发送通知失败：userId={}, noticeType={}", userId, noticeType, e);
            // 通知失败不阻断主业务（如提交/查重），仅记录日志
        }
    }

    // ------------------------------ 查询 ------------------------------

    @Override
    public Page<SysNotice> getNoticePageForUser(Long userId, Integer noticeType, Integer isRead,
                                                Integer priority, Integer pageNum, Integer pageSize) {
        int num = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1 || pageSize > 100) ? 10 : pageSize;

        Page<SysNotice> page = new Page<>(num, size);
        LambdaQueryWrapper<SysNotice> wrapper = buildUserScopedQuery(userId, noticeType, isRead, priority);
        // 只按 create_time 排序时，同一秒插入的多条通知顺序不稳定，翻页会重复/漏行，
        // 所以用 id 兜底做二级排序（雪花 id 单调递增）
        wrapper.orderByDesc(SysNotice::getCreateTime).orderByDesc(SysNotice::getId);

        return this.page(page, wrapper);
    }

    @Override
    public NoticeStatsVO getNoticeStats(Long userId) {
        long total = this.count(buildUserScopedQuery(userId, null, null, null));
        long unread = this.count(buildUserScopedQuery(userId, null, 0, null));
        long urgent = this.count(buildUserScopedQuery(userId, null, null, 2));
        // 已读 = 总数 - 未读，避免再多一次 count；total 与 unread 在同一事务快照内取，
        // 但两次 count 之间可能有并发写入，极端情况下 read 可能为负，这里兜一下
        long read = Math.max(total - unread, 0);
        return new NoticeStatsVO((int) total, (int) unread, (int) urgent, (int) read);
    }

    @Override
    public List<SysNotice> getLatestNotices(Long userId, Integer limit) {
        int size = (limit == null || limit < 1) ? 5 : Math.min(limit, 50);
        // 第三个参数 false = 不查总数（这里用不上，能省一次 count）
        Page<SysNotice> page = new Page<>(1, size, false);
        LambdaQueryWrapper<SysNotice> wrapper = buildUserScopedQuery(userId, null, null, null)
                .orderByDesc(SysNotice::getCreateTime)
                .orderByDesc(SysNotice::getId);
        return this.page(page, wrapper).getRecords();
    }

    @Override
    public Map<Integer, Integer> getUnreadCountByPriority(Long userId) {
        // 固定返回 0/1/2 三个键（无数据时为 0 而不是缺键），前端取值不用做 null 判断
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int priority = 0; priority <= 2; priority++) {
            result.put(priority, (int) this.count(buildUserScopedQuery(userId, null, 0, priority)));
        }
        return result;
    }

    @Override
    public Integer getUnreadNoticeCount(Long userId) {
        return sysNoticeMapper.countUnreadByUserId(userId);
    }

    // ------------------------------ 标记已读 ------------------------------

    @Override
    public Integer batchMarkAsRead(List<Long> noticeIds, Long userId) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return 0;
        }
        LambdaUpdateWrapper<SysNotice> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(SysNotice::getIsRead, 1)
                .set(SysNotice::getReadTime, LocalDateTime.now())
                .eq(SysNotice::getUserId, userId)      // 归属校验：只能标记自己的
                .in(SysNotice::getId, noticeIds)
                .eq(SysNotice::getIsRead, 0);          // 已读的不重复计入行数
        return sysNoticeMapper.update(null, wrapper);
    }

    @Override
    public boolean markNoticeAsRead(Long noticeId, Long userId) {
        // 1. 先查询通知是否存在（避免无效操作）
        SysNotice notice = sysNoticeMapper.selectById(noticeId);
        if (notice == null || Integer.valueOf(1).equals(notice.getIsDeleted())) {
            log.warn("标记通知已读失败：通知不存在或已删除，noticeId={}", noticeId);
            return false;
        }
        // 2. 权限校验：仅通知所属用户可标记
        if (!userId.equals(notice.getUserId())) {
            log.warn("标记通知已读失败：越权操作，noticeId={}, userId={}, ownerId={}", noticeId, userId, notice.getUserId());
            return false;
        }
        // 3. 已读无需重复标记
        if (Integer.valueOf(1).equals(notice.getIsRead())) {
            log.info("通知已为已读状态，无需重复标记：noticeId={}", noticeId);
            return true;
        }
        // 4. 执行标记操作（更新is_read和read_time）
        int rows = sysNoticeMapper.markAsRead(noticeId, userId, LocalDateTime.now());
        return rows > 0;
    }

    @Override
    public Integer markAllUnreadAsRead(Long userId) {
        return sysNoticeMapper.markAllUnreadAsRead(userId, LocalDateTime.now());
    }

    // ------------------------------ 删除（逻辑删除） ------------------------------

    @Override
    public Integer deleteNotice(Long noticeId, Long userId) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotice::getId, noticeId)
                .eq(SysNotice::getUserId, userId); // 归属校验：删不到别人的
        return sysNoticeMapper.delete(wrapper);
    }

    @Override
    public Integer batchDeleteNotices(List<Long> noticeIds, Long userId) {
        if (noticeIds == null || noticeIds.isEmpty()) {
            return 0;
        }
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotice::getUserId, userId)
                .in(SysNotice::getId, noticeIds);
        return sysNoticeMapper.delete(wrapper);
    }

    @Override
    public Integer clearAllNotices(Long userId) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotice::getUserId, userId);
        return sysNoticeMapper.delete(wrapper);
    }

    // ------------------------------ 内部工具 ------------------------------

    /**
     * 构造「限定在当前用户范围内」的基础查询条件。
     * 三个筛选参数为 null 时表示不筛选，因此统计与列表可以复用同一个方法。
     */
    private LambdaQueryWrapper<SysNotice> buildUserScopedQuery(Long userId, Integer noticeType,
                                                               Integer isRead, Integer priority) {
        LambdaQueryWrapper<SysNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotice::getUserId, userId); // 归属范围，任何时候都不能省
        if (noticeType != null) {
            wrapper.eq(SysNotice::getNoticeType, noticeType);
        }
        if (isRead != null) {
            wrapper.eq(SysNotice::getIsRead, isRead);
        }
        if (priority != null) {
            wrapper.eq(SysNotice::getPriority, priority);
        }
        return wrapper;
    }
}
