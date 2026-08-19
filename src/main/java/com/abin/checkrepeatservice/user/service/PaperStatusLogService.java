package com.abin.checkrepeatservice.user.service;

import com.abin.checkrepeatservice.pojo.entity.PaperStatusLog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 论文状态日志服务接口
 */
public interface PaperStatusLogService extends IService<PaperStatusLog> {

    /**
     * 记录状态变更日志
     * @param paperId 论文ID
     * @param oldStatus 变更前状态
     * @param newStatus 变更后状态
     * @param statusReason 变更原因
     * @param operateUserId 操作人ID
     * @param operateIp 操作IP
     */
    void recordStatusLog(Long paperId, Integer oldStatus, Integer newStatus,
                         String statusReason, Long operateUserId, String operateIp);
}
