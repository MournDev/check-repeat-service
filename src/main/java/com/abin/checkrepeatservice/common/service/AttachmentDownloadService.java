package com.abin.checkrepeatservice.common.service;

import com.abin.checkrepeatservice.mapper.AttachmentDownloadRecordMapper;
import com.abin.checkrepeatservice.pojo.entity.AttachmentDownloadRecord;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 附件下载留痕与计数（学生端/教师端共享文件列表共用）。
 *
 * <p>下载成功才记录；记录失败只记日志，绝不影响下载本身。</p>
 */
@Slf4j
@Service
public class AttachmentDownloadService {

    @Resource
    private AttachmentDownloadRecordMapper attachmentDownloadRecordMapper;

    /**
     * 记录一次成功下载
     */
    public void record(Long attachmentId, Long userId) {
        if (attachmentId == null || userId == null) {
            return;
        }
        try {
            AttachmentDownloadRecord record = new AttachmentDownloadRecord();
            record.setAttachmentId(attachmentId);
            record.setUserId(userId);
            attachmentDownloadRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("记录附件下载失败 - attachmentId: {}, userId: {}", attachmentId, userId, e);
        }
    }

    /**
     * 批量统计附件下载次数
     *
     * @param attachmentIds 附件ID集合（可含 null）
     * @return attachmentId → 下载次数；无记录时为空 Map
     */
    public Map<Long, Integer> countByAttachmentIds(Collection<Long> attachmentIds) {
        Map<Long, Integer> counts = new HashMap<>();
        List<Long> ids = attachmentIds == null ? List.of()
                : attachmentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return counts;
        }
        List<AttachmentDownloadRecord> records = attachmentDownloadRecordMapper.selectList(
                new LambdaQueryWrapper<AttachmentDownloadRecord>()
                        .in(AttachmentDownloadRecord::getAttachmentId, ids));
        for (AttachmentDownloadRecord record : records) {
            counts.merge(record.getAttachmentId(), 1, Integer::sum);
        }
        return counts;
    }
}
