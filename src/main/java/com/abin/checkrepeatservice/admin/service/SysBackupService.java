package com.abin.checkrepeatservice.admin.service;

import com.abin.checkrepeatservice.pojo.entity.SysBackupLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 数据库备份服务
 */
public interface SysBackupService {

    /**
     * 立即执行一次备份，并把结果写入 sys_backup_log。
     *
     * <p>同步执行。备份失败（如磁盘不可写、SQL 执行异常）<b>不抛异常</b>，
     * 而是返回一条 status=FAILED 的记录，由调用方按 status 判断并提示用户。
     * 只有「已有备份在执行中」这种情况才抛业务异常。</p>
     *
     * @param backupType 备份类型（AUTO/MANUAL）
     * @return 本次备份记录
     */
    SysBackupLog triggerBackup(String backupType);

    /**
     * 分页查询备份历史
     */
    Page<SysBackupLog> getHistory(int page, int size);

    /**
     * 查询最近一次备份记录，无记录时返回 null
     */
    SysBackupLog getLastBackup();

    /**
     * 备份状态：最近一次记录、备份目录、是否有任务正在执行
     */
    Map<String, Object> getStatus();

    /**
     * 读取备份设置：{enabled: boolean, retentionDays: int}
     */
    Map<String, Object> getSettings();

    /**
     * 更新备份设置（入参为 null 表示不修改该项）
     */
    void updateSettings(Boolean enabled, Integer retentionDays);

    /**
     * 是否启用自动备份（以库里 system_param.backup_enabled 为准）
     */
    boolean isAutoBackupEnabled();

    /**
     * 删除超过保留天数的备份文件及其记录
     *
     * @return 清理的记录条数
     */
    int cleanupExpired();
}
