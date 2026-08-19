package com.abin.checkrepeatservice.user.service.Impl;

import com.abin.checkrepeatservice.pojo.entity.PaperStatusLog;
import com.abin.checkrepeatservice.user.mapper.PaperStatusLogMapper;
import com.abin.checkrepeatservice.user.service.PaperStatusLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;


@Service
public class PaperStatusLogServiceImpl extends ServiceImpl<PaperStatusLogMapper, PaperStatusLog> implements PaperStatusLogService {

    @Resource
    private PaperStatusLogMapper paperStatusLogMapper;

    @Override
    public void recordStatusLog(Long paperId, Integer oldStatus, Integer newStatus,
                                String statusReason, Long operateUserId, String operateIp) {
        // 构建日志实体
        PaperStatusLog log = new PaperStatusLog();
        log.setPaperId(paperId);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setStatusReason(statusReason);
        log.setOperateIp(operateIp);
        // 操作人ID（继承BaseEntity的createUserId字段）
        log.setCreateBy(operateUserId);

        // 保存日志
        paperStatusLogMapper.insert(log);
    }
}
