package com.abin.checkrepeatservice.admin.service.Impl;

import com.abin.checkrepeatservice.admin.mapper.SysBackupLogMapper;
import com.abin.checkrepeatservice.admin.mapper.SystemParamMapper;
import com.abin.checkrepeatservice.admin.service.SysBackupService;
import com.abin.checkrepeatservice.common.Exception.BusinessException;
import com.abin.checkrepeatservice.common.enums.ResultCode;
import com.abin.checkrepeatservice.common.utils.DatabaseDumpUtils;
import com.abin.checkrepeatservice.pojo.entity.SysBackupLog;
import com.abin.checkrepeatservice.pojo.entity.SystemParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 数据库备份服务实现
 */
@Slf4j
@Service
public class SysBackupServiceImpl implements SysBackupService {

    public static final String TYPE_AUTO = "AUTO";
    public static final String TYPE_MANUAL = "MANUAL";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** error_message 列是 varchar(1000)，超出会被 MySQL 截断报错，这里主动截断 */
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    @Resource
    private SysBackupLogMapper sysBackupLogMapper;

    @Resource
    private SystemParamMapper systemParamMapper;

    @Resource
    private DataSource dataSource;

    /** 备份文件目录；默认 /data/backup/ 落在 docker-compose 已挂的 app_data:/data 卷里 */
    @Value("${backup.dir:/data/backup/}")
    private String backupDir;

    /** 库里取不到设置时的兜底默认值 */
    @Value("${backup.enabled:true}")
    private boolean defaultEnabled;

    @Value("${backup.retention-days:30}")
    private int defaultRetentionDays;

    /** 单飞锁：并发触发会写出两个文件并互相覆盖 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public SysBackupLog triggerBackup(String backupType) {
        if (!running.compareAndSet(false, true)) {
            throw new BusinessException(ResultCode.BUSINESS_NO_REPEAT,
                    "已有备份任务正在执行，请稍后再试", null);
        }

        LocalDateTime startTime = LocalDateTime.now();
        SysBackupLog record = new SysBackupLog();
        record.setBackupType(backupType == null ? TYPE_MANUAL : backupType);
        // create_time 不会被自动填充（本实体不继承 BaseEntity），必须手工赋值，
        // 否则备份记录页的「备份时间」列是空的
        record.setCreateTime(startTime);
        record.setUpdateTime(startTime);
        record.setStartTime(startTime);

        try {
            String fileName = "backup_" + startTime.format(FILE_TIMESTAMP) + ".sql.gz";
            Path target = Paths.get(backupDir).resolve(fileName);

            long fileSize = DatabaseDumpUtils.dumpToGzipFile(dataSource, target);

            LocalDateTime endTime = LocalDateTime.now();
            record.setFileName(fileName);
            record.setFileSize(fileSize);
            record.setEndTime(endTime);
            record.setDurationMs(Duration.between(startTime, endTime).toMillis());
            record.setStatus(STATUS_SUCCESS);
            record.setUpdateTime(endTime);
            sysBackupLogMapper.insert(record);

            log.info("数据库备份成功: file={}, size={}B, duration={}ms",
                    fileName, fileSize, record.getDurationMs());

            // 清理过期备份。失败不影响本次备份结果，只记日志。
            try {
                cleanupExpired();
            } catch (Exception e) {
                log.warn("清理过期备份失败: {}", e.getMessage(), e);
            }

            return record;
        } catch (Exception e) {
            LocalDateTime endTime = LocalDateTime.now();
            record.setEndTime(endTime);
            record.setDurationMs(Duration.between(startTime, endTime).toMillis());
            record.setStatus(STATUS_FAILED);
            record.setErrorMessage(truncate(e.getMessage()));
            record.setUpdateTime(endTime);
            try {
                sysBackupLogMapper.insert(record);
            } catch (Exception insertError) {
                log.error("写入备份失败记录时出错: {}", insertError.getMessage(), insertError);
            }
            log.error("数据库备份失败: {}", e.getMessage(), e);
            return record;
        } finally {
            running.set(false);
        }
    }

    @Override
    public Page<SysBackupLog> getHistory(int page, int size) {
        Page<SysBackupLog> pageObj = new Page<>(page, size);
        return sysBackupLogMapper.selectPage(pageObj,
                new LambdaQueryWrapper<SysBackupLog>().orderByDesc(SysBackupLog::getCreateTime));
    }

    @Override
    public SysBackupLog getLastBackup() {
        Page<SysBackupLog> pageObj = new Page<>(1, 1);
        Page<SysBackupLog> result = sysBackupLogMapper.selectPage(pageObj,
                new LambdaQueryWrapper<SysBackupLog>().orderByDesc(SysBackupLog::getCreateTime));
        List<SysBackupLog> records = result.getRecords();
        return records.isEmpty() ? null : records.get(0);
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("lastBackup", getLastBackup());
        status.put("backupDir", backupDir);
        status.put("running", running.get());
        return status;
    }

    @Override
    public Map<String, Object> getSettings() {
        SystemParam param = loadSystemParam();
        Integer enabled = param == null ? null : param.getBackupEnabled();
        Integer retentionDays = param == null ? null : param.getBackupRetentionDays();

        Map<String, Object> settings = new HashMap<>();
        settings.put("enabled", enabled == null ? defaultEnabled : enabled == 1);
        settings.put("retentionDays", retentionDays == null ? defaultRetentionDays : retentionDays);
        return settings;
    }

    @Override
    public void updateSettings(Boolean enabled, Integer retentionDays) {
        SystemParam param = loadSystemParam();
        if (param == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND,
                    "系统参数未初始化，无法保存备份设置", null);
        }
        if (enabled != null) {
            param.setBackupEnabled(enabled ? 1 : 0);
        }
        if (retentionDays != null) {
            if (retentionDays <= 0) {
                throw new BusinessException(ResultCode.PARAM_VALUE_INVALID,
                        "备份保留天数必须大于 0", null);
            }
            param.setBackupRetentionDays(retentionDays);
        }
        systemParamMapper.updateById(param);
        log.info("备份设置已更新: enabled={}, retentionDays={}", enabled, retentionDays);
    }

    @Override
    public int cleanupExpired() {
        int retentionDays = resolveRetentionDays();
        LocalDateTime cutoff = LocalDate.now().minusDays(retentionDays).atStartOfDay();

        List<SysBackupLog> expired = sysBackupLogMapper.selectList(
                new LambdaQueryWrapper<SysBackupLog>().lt(SysBackupLog::getCreateTime, cutoff));
        if (expired.isEmpty()) {
            return 0;
        }

        Path dir = Paths.get(backupDir);
        int removed = 0;
        for (SysBackupLog record : expired) {
            // 先删文件再删记录：文件删除失败就保留记录，便于下次重试
            if (record.getFileName() != null && !record.getFileName().isEmpty()) {
                try {
                    Files.deleteIfExists(dir.resolve(record.getFileName()));
                } catch (IOException e) {
                    log.warn("删除过期备份文件失败，保留记录待重试: file={}, error={}",
                            record.getFileName(), e.getMessage());
                    continue;
                }
            }
            sysBackupLogMapper.deleteById(record.getId());
            removed++;
        }

        if (removed > 0) {
            log.info("清理过期备份完成: 保留 {} 天, 删除 {} 条", retentionDays, removed);
        }
        return removed;
    }

    @Override
    public boolean isAutoBackupEnabled() {
        return Boolean.TRUE.equals(getSettings().get("enabled"));
    }

    private int resolveRetentionDays() {
        SystemParam param = loadSystemParam();
        Integer retentionDays = param == null ? null : param.getBackupRetentionDays();
        return retentionDays == null || retentionDays <= 0 ? defaultRetentionDays : retentionDays;
    }

    /** system_param 是单例表，取第一条即可 */
    private SystemParam loadSystemParam() {
        Page<SystemParam> pageObj = new Page<>(1, 1);
        List<SystemParam> records = systemParamMapper.selectPage(pageObj, new LambdaQueryWrapper<>()).getRecords();
        return records.isEmpty() ? null : records.get(0);
    }

    private String truncate(String message) {
        if (message == null) {
            return "未知错误";
        }
        return message.length() <= ERROR_MESSAGE_MAX_LENGTH
                ? message
                : message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }
}
