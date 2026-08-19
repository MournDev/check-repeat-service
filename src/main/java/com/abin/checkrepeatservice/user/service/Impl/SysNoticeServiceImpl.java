package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.mapper.SysUserMapper;
import com.abin.checkrepeatservice.pojo.entity.SysNotice;
import com.abin.checkrepeatservice.pojo.entity.SysUser;
import com.abin.checkrepeatservice.user.mapper.SysNoticeMapper;
import com.abin.checkrepeatservice.user.service.SysNoticeService;
import com.abin.checkrepeatservice.user.vo.PageResultVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内信服务实现（完整版：含原有分页、统计、批量标记逻辑）
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

    // ------------------------------ 新增逻辑：发送通知（站内信+邮箱） ------------------------------
    @Async("taskExecutor") // 异步执行，避免阻塞主流程
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

    @Override
    public PageResultVO<SysNotice> getUserNoticePage(Long userId, Integer isRead, Integer pageNum, Integer pageSize) {
        // 1. 参数校验与默认值设置
        if (pageNum == null || pageNum < 1) pageNum = 1;
        if (pageSize == null || pageSize < 1 || pageSize > 50) pageSize = 10; // 限制最大每页50条，避免性能问题
        int start = (pageNum - 1) * pageSize;

        // 2. 查询当前页数据
        List<SysNotice> noticeList = sysNoticeMapper.selectByUserId(userId, isRead, start, pageSize);

        // 3. 查询总条数（用于分页计算）
        Integer totalCount = sysNoticeMapper.countTotalByUserId(userId, isRead);

        // 4. 组装分页结果VO（前端统一接收格式）
        return new PageResultVO<>(
                pageNum,
                pageSize,
                totalCount,
                (totalCount + pageSize - 1) / pageSize, // 总页数（向上取整）
                noticeList
        );
    }

    @Override
    public Integer getUnreadNoticeCount(Long userId) {
        // 直接调用Mapper统计未读数量（仅查未删除+未读的通知）
        return sysNoticeMapper.countUnreadByUserId(userId);
    }

    @Override
    public boolean markNoticeAsRead(Long noticeId, Long userId) {
        // 1. 先查询通知是否存在（避免无效操作）
        SysNotice notice = sysNoticeMapper.selectById(noticeId);
        if (notice == null || notice.getIsDeleted() == 1) {
            log.warn("标记通知已读失败：通知不存在或已删除，noticeId={}", noticeId);
            return false;
        }
        // 2. 权限校验：仅通知所属用户可标记
        if (!notice.getUserId().equals(userId)) {
            log.warn("标记通知已读失败：越权操作，noticeId={}, userId={}, ownerId={}", noticeId, userId, notice.getUserId());
            return false;
        }
        // 3. 已读无需重复标记
        if (notice.getIsRead() == 1) {
            log.info("通知已为已读状态，无需重复标记：noticeId={}", noticeId);
            return true;
        }
        // 4. 执行标记操作（更新is_read和read_time）
        int rows = sysNoticeMapper.markAsRead(noticeId, userId, LocalDateTime.now());
        return rows > 0;
    }

    @Override
    public Integer markAllUnreadAsRead(Long userId) {
        // 调用Mapper批量更新（仅更新当前用户的未读+未删除通知）
        return sysNoticeMapper.markAllUnreadAsRead(userId, LocalDateTime.now());
    }
}