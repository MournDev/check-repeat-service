package com.abin.checkrepeatservice.common.utils;

import javax.sql.DataSource;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * 数据库导出工具：纯 JDBC 生成 SQL 转储文件（gzip 压缩）。
 *
 * <p><b>为什么不用 mysqldump：</b>生产镜像是 {@code eclipse-temurin:17-jre}，
 * 容器内没有 mysql 客户端。装一个进去要改 Dockerfile 并联网构建，
 * 因此这里用 JDBC 自己生成。</p>
 *
 * <p><b>内存约束：</b>生产 JVM 只有 -Xmx768m，所以导出必须流式——
 * 用 MySQL 的流式游标（{@code setFetchSize(Integer.MIN_VALUE)}）逐行读，
 * 边读边写，绝不把整表读进 List/StringBuilder。</p>
 */
public final class DatabaseDumpUtils {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 每条 INSERT 攒多少行后落盘，兼顾文件体积与写入次数 */
    private static final int BATCH_ROWS = 200;

    private DatabaseDumpUtils() {
    }

    /**
     * 把整个库导出为 gzip 压缩的 SQL 文件。
     *
     * @param dataSource 数据源
     * @param target     目标文件（父目录会被自动创建）
     * @return 落盘文件大小（字节）
     */
    public static long dumpToGzipFile(DataSource dataSource, Path target) throws Exception {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // 单独取一条连接，不占用 Hikari 池里的连接跑整场导出
        try (Connection conn = dataSource.getConnection()) {
            // 关闭自动提交：RR 隔离级别下 SHOW CREATE TABLE 与各表 SELECT 落在同一快照上，
            // 保证 DDL 与数据一致。代价是整场导出期间持有一个长事务，undo 会累积——
            // 本项目库规模小，拿这个换一致性是划算的。
            conn.setAutoCommit(false);
            try {
                List<String> tables = listBaseTables(conn);

                try (OutputStream fileOut = Files.newOutputStream(target);
                     GZIPOutputStream gz = new GZIPOutputStream(new BufferedOutputStream(fileOut));
                     Writer writer = new BufferedWriter(new OutputStreamWriter(gz, StandardCharsets.UTF_8))) {

                    writer.write("-- check-repeat-service 内置导出器生成（纯 JDBC，不依赖 mysqldump）\n");
                    writer.write("-- 生成时间: " + LocalDateTime.now().format(TIMESTAMP) + "\n\n");
                    // 恢复时关掉外键检查，避免表间依赖顺序导致导入失败
                    writer.write("SET NAMES utf8mb4;\n");
                    writer.write("SET FOREIGN_KEY_CHECKS=0;\n\n");

                    for (String table : tables) {
                        writeCreateTable(conn, writer, table);
                        writeInserts(conn, writer, table);
                    }

                    writer.write("SET FOREIGN_KEY_CHECKS=1;\n");
                }

                // 只读事务，提交以释放一致性快照
                conn.commit();
                return Files.size(target);
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    /**
     * 列出当前库的所有基础表。
     *
     * <p>用 {@code DATABASE()} 而不是硬编码库名：生产库名是
     * {@code check-repeat-system}（带连字符），与 yml 默认的
     * {@code check-repeat-service} 不同，且大小写敏感。
     * 也刻意不用 {@code connection.getCatalog()}——驱动可能返回 null。</p>
     */
    private static List<String> listBaseTables(Connection conn) throws SQLException {
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                + "ORDER BY table_name";
        List<String> tables = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    /**
     * 输出建表语句。
     *
     * <p>直接落 {@code SHOW CREATE TABLE} 的原文，不手写 CREATE TABLE——
     * 手写必然漏掉 COLLATE、索引、char(1) 之类的细节。</p>
     */
    private static void writeCreateTable(Connection conn, Writer writer, String table)
            throws SQLException, IOException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE " + quote(table))) {
            if (rs.next()) {
                writer.write("--\n-- Table structure for table " + quote(table) + "\n--\n\n");
                writer.write("DROP TABLE IF EXISTS " + quote(table) + ";\n");
                writer.write(rs.getString(2));
                writer.write(";\n\n");
            }
        }
    }

    /**
     * 流式输出某张表的所有数据。逐行读取、分批写入，避免整表进内存。
     */
    private static void writeInserts(Connection conn, Writer writer, String table)
            throws SQLException, IOException {
        try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            // Integer.MIN_VALUE 是 MySQL Connector/J 的流式游标约定，逐行返回不缓存整个结果集
            stmt.setFetchSize(Integer.MIN_VALUE);

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + quote(table))) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                StringBuilder columnList = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) {
                        columnList.append(", ");
                    }
                    columnList.append(quote(meta.getColumnLabel(i)));
                }
                String insertPrefix = "INSERT INTO " + quote(table) + " (" + columnList + ") VALUES\n";

                StringBuilder batch = new StringBuilder();
                int rowsInBatch = 0;
                boolean wroteAny = false;

                while (rs.next()) {
                    if (rowsInBatch == 0) {
                        batch.setLength(0);
                        batch.append(insertPrefix);
                    } else {
                        batch.append(",\n");
                    }

                    batch.append('(');
                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) {
                            batch.append(", ");
                        }
                        batch.append(toSqlLiteral(rs, meta, i));
                    }
                    batch.append(')');

                    rowsInBatch++;
                    if (rowsInBatch >= BATCH_ROWS) {
                        batch.append(";\n");
                        writer.write(batch.toString());
                        rowsInBatch = 0;
                        wroteAny = true;
                    }
                }

                if (rowsInBatch > 0) {
                    batch.append(";\n");
                    writer.write(batch.toString());
                    wroteAny = true;
                }
                if (wroteAny) {
                    writer.write("\n");
                }
            }
        }
    }

    /**
     * 把一列的值转成 SQL 字面量。
     *
     * <p>按 java.sql.Types 分派。本项目 init.sql 里的列类型只有
     * bigint/datetime/varchar/tinyint/int/text/decimal/json/double/char/timestamp/mediumtext，
     * 没有 blob/binary/bit，所以字符串分支是兜底也就是主路径。</p>
     */
    private static String toSqlLiteral(ResultSet rs, ResultSetMetaData meta, int index) throws SQLException {
        int type = meta.getColumnType(index);
        switch (type) {
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
                // 统一走 getString：既规避 tinyint 是否映射成 boolean 的驱动差异，
                // 也避免 bigint 超出 JS 安全整数范围时被驱动做额外转换
                return literalOrNull(rs.getString(index));
            case Types.DECIMAL:
            case Types.NUMERIC: {
                BigDecimal value = rs.getBigDecimal(index);
                // toPlainString 而非 toString，避免出现 1E+2 这类科学计数法
                return value == null ? "NULL" : value.toPlainString();
            }
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.REAL: {
                double value = rs.getDouble(index);
                return rs.wasNull() ? "NULL" : Double.toString(value);
            }
            case Types.NULL:
                return "NULL";
            default: {
                // varchar/text/json/char/datetime/timestamp 等一律按字符串处理；
                // Connector/J 的 getString 对 DATETIME 返回的就是 'yyyy-MM-dd HH:mm:ss'
                String value = rs.getString(index);
                return value == null ? "NULL" : "'" + escapeString(value) + "'";
            }
        }
    }

    private static String literalOrNull(String value) {
        return value == null ? "NULL" : value;
    }

    /**
     * 按 MySQL 的转义规则处理字符串字面量。
     *
     * <p>反斜杠必须和引号一起转义，否则形如 {@code C:\\path} 与
     * {@code it's} 的值会破坏语句结构。项目里 messages.attachments、
     * review_workflow.steps、operation_param 等 text/json 列都含引号与换行。</p>
     *
     * <p>前提是服务端 sql_mode 未启用 NO_BACKSLASH_ESCAPES（MySQL 8 默认未启用）。
     * 若线上改过该模式，此处转义会失效，需要改用十六进制字面量。</p>
     */
    private static String escapeString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\'':
                    sb.append("\\'");
                    break;
                case '\0':
                    sb.append("\\0");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\u001a':
                    sb.append("\\Z");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 用反引号包裹标识符，并转义标识符内的反引号 */
    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
