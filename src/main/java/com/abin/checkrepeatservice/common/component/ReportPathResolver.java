package com.abin.checkrepeatservice.common.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 查重报告路径解析器。
 */
@Slf4j
@Component
public class ReportPathResolver {

    private static final DateTimeFormatter DATE_DIR_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 报告存储根目录；与 application.yml 的 report.storage.base-path 对齐 */
    @Value("${report.storage.base-path:/data/report}")
    private String basePath;

    /**
     * 生成相对存储路径：{@code yyyy/MM/dd/<reportNo>.pdf}。
     * 统一使用正斜杠，避免 Windows/Linux 之间的分隔符差异。
     */
    public String buildRelativePath(String reportNo) {
        return LocalDate.now().format(DATE_DIR_FORMATTER) + "/" + reportNo + ".pdf";
    }

    /**
     * 相对路径 -> 绝对路径。
     *
     * <ul>
     *   <li>存量绝对路径原样返回（兼容历史数据）；</li>
     *   <li>相对路径解析到配置根目录，并做规范化 + 越界校验，防止 {@code ../} 路径穿越。</li>
     * </ul>
     *
     * @param storedPath 数据库中的 report_path
     * @return 绝对路径；入参为空时返回 null
     */
    public String toAbsolute(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }

        String normalizedStored = storedPath.trim().replace('\\', '/');
        if (isAbsoluteLike(normalizedStored)) {
            return normalizedStored;
        }

        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path resolved = base.resolve(normalizedStored).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("非法的报告存储路径：" + storedPath);
        }
        return resolved.toString();
    }

    /**
     * 相对路径 -> File；入参为空时返回 null。
     */
    public File toFile(String storedPath) {
        String absolutePath = toAbsolute(storedPath);
        return absolutePath == null ? null : new File(absolutePath);
    }

    /**
     * 确保目标文件的父目录存在（PDF 生成前调用）。
     */
    public void ensureParentDirectory(String absolutePath) {
        if (absolutePath == null) {
            return;
        }
        File parent = new File(absolutePath).getParentFile();
        if (parent != null && !parent.exists()) {
            boolean created = parent.mkdirs();
            if (!created && !parent.exists()) {
                log.warn("报告目录创建失败：{}", parent.getAbsolutePath());
            }
        }
    }

    private boolean isAbsoluteLike(String path) {
        // Windows 盘符（D:/...）在 Linux 上不会被 Paths#isAbsolute 识别，需显式匹配
        return path.startsWith("/") || path.matches("^[A-Za-z]:/.*");
    }
}
