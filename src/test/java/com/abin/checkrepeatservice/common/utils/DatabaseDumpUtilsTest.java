package com.abin.checkrepeatservice.common.utils;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 导出器的集成测试：导出一个真实库，再把它还原到临时库，逐表比对行数。
 *
 * <p>只断言"文件存在、能解压"是不够的——转义写错（引号、反斜杠、换行）
 * 时文件照样能生成，但导入会失败或数据损坏。所以这里真的跑一次还原。</p>
 *
 * <p><b>连接参数一律不落盘</b>，只从系统属性 / 环境变量读取，
 * 缺少口令时整个测试被跳过（{@code assumeTrue}），CI 不受影响。
 * 口令不设默认值，避免把本机凭据写进可提交的源码。</p>
 *
 * <pre>
 * MYSQL_PASSWORD=xxx mvn test -Dtest=DatabaseDumpUtilsTest \
 *   -Ddump.itest.enabled=true -Ddump.test.database=check-repeat-system
 * </pre>
 *
 * <p><b>必须显式加 {@code -Ddump.itest.enabled=true}</b>（或环境变量
 * {@code DUMP_ITEST_ENABLED=true}）才会执行：本测试会连真库全表计数并建删临时库，
 * 不能因为环境里恰好有 {@code MYSQL_PASSWORD} 就自动跑起来。</p>
 *
 * 可用键：{@code dump.test.host} / {@code dump.test.port} /
 * {@code dump.test.user} / {@code dump.test.password} / {@code dump.test.database}
 * 或同名环境变量 {@code MYSQL_HOST} / {@code MYSQL_PORT} /
 * {@code MYSQL_USERNAME} / {@code MYSQL_PASSWORD} / {@code MYSQL_DATABASE}。
 */
class DatabaseDumpUtilsTest {

    private static final String HOST = setting("dump.test.host", "MYSQL_HOST", "localhost");
    private static final String PORT = setting("dump.test.port", "MYSQL_PORT", "3306");
    private static final String USER = setting("dump.test.user", "MYSQL_USERNAME", "root");
    /** 无默认值：没显式提供口令就不连库、不跑这个测试 */
    private static final String PASSWORD = setting("dump.test.password", "MYSQL_PASSWORD", null);
    private static final String SOURCE_DB =
            setting("dump.test.database", "MYSQL_DATABASE", "check-repeat-system");

    private static final String RESTORE_DB = "check_repeat_dump_restore_test";

    // 转义专项测试用的自建库，与被测的真实库无关
    private static final String ESCAPE_SRC_DB = "check_repeat_dump_escape_src";
    private static final String ESCAPE_DST_DB = "check_repeat_dump_escape_dst";

    /**
     * 真实库往返：导出后还原到临时库，逐表比对行数。
     *
     * <p>注意这个测试**不能**证明转义正确——被测库里恰好没有含引号/反斜杠的数据，
     * 转义写坏它照样通过。转义由 {@link #escapingRoundTripsHostileStrings()} 专门覆盖。</p>
     */
    @Test
    void dumpThenRestorePreservesEveryTableRowCount() throws Exception {
        assumeDatabaseAvailable();

        Path workDir = Files.createTempDirectory("dump-test-");
        try {
            dumpAndRestore(SOURCE_DB, RESTORE_DB, workDir);

            Map<String, Integer> sourceCounts = tableRowCounts(SOURCE_DB);
            Map<String, Integer> restoreCounts = tableRowCounts(RESTORE_DB);
            assertTrue(!sourceCounts.isEmpty(), "源库应至少有一张表");

            assertEquals(sourceCounts.keySet(), restoreCounts.keySet(),
                    "还原后的表集合应与源库一致");
            sourceCounts.forEach((table, expected) ->
                    assertEquals(expected, restoreCounts.get(table),
                            "表 " + table + " 的行数在还原后不一致"));
        } finally {
            dropDatabaseQuietly(RESTORE_DB);
            deleteRecursively(workDir);
        }
    }

    /**
     * 转义专项：往自建库里塞一批"恶劣"字符串，导出还原后逐字节比对。
     *
     * <p>这是整个测试类里最关键的一条。行数相等不能说明什么——
     * 单引号没转义会让 INSERT 语法出错或把一个值拆成两个值，
     * 反斜杠没转义会把 {@code C:\path} 里的 {@code \p} 变成 {@code p}
     * （行数不变、内容已被悄悄改写）。所以这里比对的是内容本身。</p>
     *
     * <p>用自建库而不是真实库：既要造脏数据，就不能污染用户的库。</p>
     */
    @Test
    void escapingRoundTripsHostileStrings() throws Exception {
        assumeDatabaseAvailable();

        String[] payloads = {
                "it's a quote",                    // 单引号
                "back\\slash",                     // 反斜杠
                "trailing backslash \\",           // 结尾反斜杠（最容易吃掉后一个字符）
                "literal \\n not a newline",       // 字面量 \n，不能被当成换行
                "line1\nline2",                    // 真实换行
                "carriage\rreturn",                // 回车
                "tab\there",                       // 制表符
                "nul\0byte",                       // NUL
                "ctrl\u001aZ",                     // ctrl-Z，MySQL 里是 \Z
                "semi;colon and '; DROP TABLE x; --",  // 分号 + 注释，考验语句切分
                "/* block comment */",
                "double \"quoted\" text",
                "json {\"a\":\"it's \\\"q\\\"\",\"b\":[1,2]}",
                "percent % and underscore _",
                "中文与 emoji 😀🚀",
                "backtick ` and double `` backticks",   // 标识符引用符
                ""                                      // 空串（不是 NULL）
        };

        Path workDir = Files.createTempDirectory("dump-escape-test-");
        try {
            // 1. 造源库与脏数据
            recreateDatabase(ESCAPE_SRC_DB);
            insertHostileRows(ESCAPE_SRC_DB, payloads);

            // 2. 导出并还原到另一个库
            dumpAndRestore(ESCAPE_SRC_DB, ESCAPE_DST_DB, workDir);

            // 3. 逐一比对内容
            List<String> restored = readHostileRows(ESCAPE_DST_DB);
            assertEquals(payloads.length, restored.size(), "行数在还原后不一致");
            for (int i = 0; i < payloads.length; i++) {
                assertEquals(payloads[i], restored.get(i),
                        "第 " + i + " 行内容在导出还原后被改写了（转义有问题）");
            }
        } finally {
            dropDatabaseQuietly(ESCAPE_SRC_DB);
            dropDatabaseQuietly(ESCAPE_DST_DB);
            deleteRecursively(workDir);
        }
    }

    /**
     * 本测试会连真库、对每张表做 COUNT(*)，并创建/删除临时库，
     * 所以必须是**显式开启**才跑，不能因为环境里恰好有 MYSQL_PASSWORD 就自动执行
     * （本地 shell 里 export 过 MYSQL_PASSWORD 是很常见的情况，而它可能指向生产库）。
     *
     * <p>Jenkinsfile 用的是 {@code mvn -DskipTests}，CI 本就不会跑到这里；
     * 这道开关是防本地误跑。</p>
     */
    private void assumeDatabaseAvailable() {
        assumeTrue("true".equalsIgnoreCase(setting("dump.itest.enabled", "DUMP_ITEST_ENABLED", null)),
                "未显式开启导出器集成测试。要跑请加 -Ddump.itest.enabled=true 或 DUMP_ITEST_ENABLED=true");
        assumeTrue(PASSWORD != null && !PASSWORD.isBlank(),
                "未提供 MYSQL_PASSWORD / -Ddump.test.password，跳过导出器集成测试");
        assumeTrue(canConnect(), "本机 MySQL 不可用，跳过导出器集成测试");
    }

    /**
     * 导出 sourceDb → 解压 → 还原进 restoreDb。
     *
     * @return 解压后的 .sql 文件路径
     */
    private static Path dumpAndRestore(String sourceDb, String restoreDb, Path workDir) throws Exception {
        Path dumpFile = workDir.resolve("dump.sql.gz");
        Path restoredSql = workDir.resolve("dump.sql");

        long size = DatabaseDumpUtils.dumpToGzipFile(dataSource(sourceDb), dumpFile);
        assertTrue(size > 0, "导出文件不应为空");
        assertTrue(Files.exists(dumpFile), "导出文件应落盘");

        // 能解压说明 gzip 流正常关闭、内容完整
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(dumpFile))) {
            Files.copy(in, restoredSql, StandardCopyOption.REPLACE_EXISTING);
        }
        String sql = Files.readString(restoredSql, StandardCharsets.UTF_8);
        assertTrue(sql.contains("SET FOREIGN_KEY_CHECKS=0;"), "应包含外键检查开关");
        assertTrue(sql.contains("CREATE TABLE"), "应包含建表语句");

        // ScriptUtils 按 SQL 规则切分语句，不会在字符串内的分号处切错
        recreateDatabase(restoreDb);
        try (Connection conn = connection(restoreDb)) {
            ScriptUtils.executeSqlScript(conn,
                    new EncodedResource(new FileSystemResource(restoredSql), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("导出文件导入临时库失败（转义或语法有问题）：" + e.getMessage(), e);
        }
        return restoredSql;
    }

    private static void insertHostileRows(String database, String[] payloads) throws SQLException {
        try (Connection conn = connection(database);
             Statement ddl = conn.createStatement()) {
            ddl.execute("CREATE TABLE hostile ("
                    + "id INT PRIMARY KEY, "
                    + "s TEXT, "
                    + "v VARCHAR(255)) DEFAULT CHARSET=utf8mb4");
            try (var ps = conn.prepareStatement("INSERT INTO hostile (id, s, v) VALUES (?, ?, ?)")) {
                for (int i = 0; i < payloads.length; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, payloads[i]);
                    ps.setString(3, payloads[i]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    private static List<String> readHostileRows(String database) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection conn = connection(database);
             Statement stmt = conn.createStatement();
             // v 是 varchar(255)，能放下的都能放，用它同时验证 varchar 与 text 两条路径
             ResultSet rs = stmt.executeQuery("SELECT s, v FROM hostile ORDER BY id")) {
            while (rs.next()) {
                assertEquals(rs.getString(1), rs.getString(2),
                        "同一值在 TEXT 与 VARCHAR 列上还原结果不一致");
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private static String setting(String sysProp, String envVar, String fallback) {
        String value = System.getProperty(sysProp);
        if (value == null || value.isBlank()) {
            value = System.getenv(envVar);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private boolean canConnect() {
        try (Connection ignored = connection(null)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String jdbcUrl(String database) {
        String params = "?useUnicode=true&characterEncoding=utf8&useSSL=false"
                + "&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true";
        return "jdbc:mysql://" + HOST + ":" + PORT + "/"
                + (database == null ? "" : database) + params;
    }

    private static Connection connection(String database) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), USER, PASSWORD);
    }

    private static void recreateDatabase(String database) throws SQLException {
        try (Connection conn = connection(null); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS `" + database + "`");
            stmt.execute("CREATE DATABASE `" + database
                    + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
    }

    private static void dropDatabaseQuietly(String database) {
        try (Connection conn = connection(null); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS `" + database + "`");
        } catch (Exception ignored) {
            // 清理失败不影响测试结论
        }
    }

    /**
     * 精确统计每张表的行数。
     *
     * <p>刻意不用 {@code information_schema.table_rows}：那是 InnoDB 的采样估算值，
     * 对刚建好、刚插完数据的表可能严重偏离（甚至为 0），拿它比对会产生假失败。
     * 本库规模小，逐表 COUNT(*) 的代价可以忽略。</p>
     */
    private static Map<String, Integer> tableRowCounts(String database) throws SQLException {
        List<String> tables = new ArrayList<>();
        String listSql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = '" + database + "' AND table_type = 'BASE TABLE' "
                + "ORDER BY table_name";
        try (Connection conn = connection(database);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(listSql)) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection conn = connection(database); Statement stmt = conn.createStatement()) {
            for (String table : tables) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM `" + table.replace("`", "``") + "`")) {
                    counts.put(table, rs.next() ? rs.getInt(1) : 0);
                }
            }
        }
        return counts;
    }

    private static void deleteRecursively(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // 临时文件清理失败无所谓
                        }
                    });
        } catch (Exception ignored) {
            // 同上
        }
    }

    /** 极简 DataSource：测试里不想为了拿一条连接引入 Hikari 依赖 */
    private static DataSource dataSource(String database) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return connection(database);
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return DriverManager.getConnection(jdbcUrl(database), username, password);
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public Logger getParentLogger() {
                return Logger.getGlobal();
            }

            @Override
            public <T> T unwrap(Class<T> iface) {
                return null;
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }
}
