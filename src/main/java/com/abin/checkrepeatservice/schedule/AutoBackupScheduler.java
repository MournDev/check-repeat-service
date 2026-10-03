package com.abin.checkrepeatservice.schedule;

import com.abin.checkrepeatservice.admin.service.Impl.SysBackupServiceImpl;
import com.abin.checkrepeatservice.admin.service.SysBackupService;
import com.abin.checkrepeatservice.pojo.entity.SysBackupLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 自动备份定时任务
 *
 * <p>「系统配置」页的备份开关（system_param.backup_enabled）只写库不生效的话，
 * 用户关掉自动备份后仍会每天生成文件。这里负责读该开关并执行。</p>
 */
@Component
@Slf4j
public class AutoBackupScheduler {

    @Resource
    private SysBackupService sysBackupService;

    /**
     * 每天凌晨 2:00 执行自动备份（与「系统配置」页的文案一致）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void autoBackup() {
        try {
            if (!sysBackupService.isAutoBackupEnabled()) {
                log.info("自动备份已关闭，跳过本次执行");
                return;
            }
        } catch (Exception e) {
            // 读设置失败时保守跳过，不要因为一个配置读取问题就产出无人预期的备份
            log.error("读取自动备份开关失败，跳过本次执行: {}", e.getMessage(), e);
            return;
        }

        log.info("开始执行自动备份...");
        try {
            SysBackupLog record = sysBackupService.triggerBackup(SysBackupServiceImpl.TYPE_AUTO);
            if (SysBackupServiceImpl.STATUS_SUCCESS.equals(record.getStatus())) {
                log.info("自动备份成功: file={}, duration={}ms", record.getFileName(), record.getDurationMs());
            } else {
                log.error("自动备份失败: {}", record.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("自动备份执行异常: {}", e.getMessage(), e);
        }
    }
}
