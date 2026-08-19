package com.abin.checkrepeatservice.common.service.Impl;

import com.abin.checkrepeatservice.mapper.SysNoticeLogMapper;
import com.abin.checkrepeatservice.pojo.entity.SysNoticeLog;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NoticeLogService {

    private final SysNoticeLogMapper noticeLogMapper;

    /**
     * 保存通知日志
     */
    public void saveNoticeLog(String toEmail, String subject, String content,
                              String noticeType, boolean success, String errorMsg,
                              Long relatedId, String relatedType) {
        try {
            SysNoticeLog noticeLog = new SysNoticeLog();
            noticeLog.setToEmail(toEmail);
            noticeLog.setSubject(subject);
            noticeLog.setContent(content);
            noticeLog.setNoticeType(noticeType);
            noticeLog.setSuccess(success);
            noticeLog.setErrorMsg(errorMsg);
            noticeLog.setRelatedId(relatedId);
            noticeLog.setRelatedType(relatedType);
            noticeLog.setSendTime(LocalDateTime.now());
            noticeLog.setCreateTime(LocalDateTime.now());

            noticeLogMapper.insert(noticeLog);

            log.debug("通知日志保存成功：toEmail={}, noticeType={}", toEmail, noticeType);
        } catch (Exception e) {
            log.error("保存通知日志失败：toEmail={}, noticeType={}", toEmail, noticeType, e);
        }
    }

    /**
     * 查询通知发送记录
     */
    public List<SysNoticeLog> getNoticeLogsByRelatedId(Long relatedId, String relatedType) {
        QueryWrapper<SysNoticeLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("related_id", relatedId)
                .eq("related_type", relatedType)
                .orderByDesc("send_time");
        return noticeLogMapper.selectList(queryWrapper);
    }

    /**
     * 统计通知发送情况
     */
    public NoticeStats getNoticeStats(LocalDate startDate, LocalDate endDate) {
        List<SysNoticeLog> logs = noticeLogMapper.selectList(new QueryWrapper<SysNoticeLog>()
                .ge("send_time", startDate.atStartOfDay())
                .le("send_time", endDate.plusDays(1).atStartOfDay()));

        long total = logs.size();
        long successCount = logs.stream().filter(SysNoticeLog::getSuccess).count();
        double successRate = total > 0 ? (double) successCount / total * 100 : 0;

        return NoticeStats.builder()
                .totalCount(total)
                .successCount(successCount)
                .failureCount(total - successCount)
                .successRate(String.format("%.2f%%", successRate))
                .build();
    }
}

@Data
@Builder
class NoticeStats {
    private Long totalCount;
    private Long successCount;
    private Long failureCount;
    private String successRate;
}
