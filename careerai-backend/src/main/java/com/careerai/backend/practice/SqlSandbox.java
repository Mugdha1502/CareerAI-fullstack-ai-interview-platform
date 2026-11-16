package com.careerai.backend.practice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.h2.Driver;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Disposable SQL sandbox for practice queries — completely separate from the CareerAI datasource.
 *
 * Every execution:
 *   new private in-memory H2 database (random name; jdbc:h2:mem is only reachable inside this JVM)
 *   → schema + seed rows run by the database's own admin connection
 *   → a SELECT-only user is created and granted SELECT on the practice tables only
 *   → the candidate query runs as that user, read-only, with a query timeout and a row cap
 *   → result serialised (bounded) → both connections closed → the database is discarded.
 *
 * The SELECT-only user is the real security boundary: H2 refuses DDL, DML, RUNSCRIPT/SCRIPT/BACKUP,
 * CREATE ALIAS, LINK_SCHEMA and file functions for non-admin users, independent of the keyword
 * checks in SqlStatementGuard. No Spring DataSource, JPA or MySQL connection is ever used here.
 * The sandbox shares the JVM's CPU/memory, so concurrency, time, rows and cell sizes are capped.
 */
@Component
public class SqlSandbox {

    private static final Logger log = LoggerFactory.getLogger(SqlSandbox.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    // Used directly (not via DriverManager), so class loaders such as devtools' restart loader cannot hide it.
    private static final Driver H2 = new Driver();
    private static final String PRACTICE_USER = "PRACTICE_USER";
    private static final Pattern H2_CODE = Pattern.compile("\\s*\\[\\d+-\\d+]\\s*$");
    private static final Pattern NOT_FOUND_NAME = Pattern.compile("\"([^\"]+)\" not found");

    private final int queryTimeoutSeconds;
    private final int maxRows;
    private final int maxColumns;
    private final int maxCellChars;
    private final Semaphore concurrency;

    public SqlSandbox(
        @Value("${app.practice.sql.query-timeout-seconds:3}") int queryTimeoutSeconds,
        @Value("${app.practice.sql.max-rows:200}") int maxRows,
        @Value("${app.practice.sql.max-columns:30}") int maxColumns,
        @Value("${app.practice.sql.max-cell-chars:500}") int maxCellChars,
        @Value("${app.practice.sql.max-concurrent:4}") int maxConcurrent
    ) {
        this.queryTimeoutSeconds = Math.max(1, queryTimeoutSeconds);
        this.maxRows = Math.max(1, maxRows);
        this.maxColumns = Math.max(1, maxColumns);
        this.maxCellChars = Math.max(20, maxCellChars);
        this.concurrency = new Semaphore(Math.max(1, maxConcurrent), true);
    }

    /** Bounded query result. Cells are null, numbers or strings (dates as ISO text). */
    public record ResultTable(List<String> columns, List<List<Object>> rows, boolean truncated, long executionTimeMs) {
    }

    /**
     * Runs one query against a fresh copy of the dataset (plus the hidden extra rows when asked).
     * candidate=true runs it as the SELECT-only user; reference queries may run as admin.
     */
    public ResultTable run(SqlQuestion.Dataset dataset, boolean includeHiddenRows, String query, boolean candidate) {
        boolean acquired;
        try {
            acquired = concurrency.tryAcquire(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            throw new PracticeExecutionException(HttpStatus.SERVICE_UNAVAILABLE, "SQL_BUSY", "The SQL sandbox is busy. Please try again in a moment.");
        }
        String name = "jdbc:h2:mem:sbx_" + UUID.randomUUID().toString().replace("-", "");
        // Database settings are fixed by the admin connection that creates the database; the
        // practice user connects by name only (changing settings needs admin rights in H2).
        String url = name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=0";
        String adminPassword = secret();
        String userPassword = secret();
        try (Connection admin = connect(url, credentials("SA", adminPassword))) {
            try (Statement setup = admin.createStatement()) {
                for (String sql : dataset.schemaSql()) setup.execute(sql);
                for (String sql : dataset.seedSql()) setup.execute(sql);
                if (includeHiddenRows && dataset.hiddenSeedSql() != null) {
                    for (String sql : dataset.hiddenSeedSql()) setup.execute(sql);
                }
                setup.execute("CREATE USER " + PRACTICE_USER + " PASSWORD '" + userPassword + "'");
                for (SqlQuestion.Table table : dataset.tables()) {
                    setup.execute("GRANT SELECT ON " + table.name() + " TO " + PRACTICE_USER);
                }
            }
            if (!candidate) {
                return execute(admin, query);
            }
            try (Connection practice = connect(name, credentials(PRACTICE_USER, userPassword))) {
                practice.setReadOnly(true);
                return execute(practice, query);
            }
        } catch (SQLException ex) {
            throw translate(ex);
        } finally {
            // Closing the last connection drops the in-memory database (DB_CLOSE_DELAY=0).
            concurrency.release();
        }
    }

    private ResultTable execute(Connection connection, String query) throws SQLException {
        long started = System.nanoTime();
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            statement.setMaxRows(maxRows + 1);
            if (!statement.execute(query)) {
                throw new PracticeExecutionException(HttpStatus.BAD_REQUEST, "SQL_NOT_ALLOWED", "Statement type not allowed: the query must return rows.");
            }
            try (ResultSet resultSet = statement.getResultSet()) {
                ResultSetMetaData meta = resultSet.getMetaData();
                int columnCount = meta.getColumnCount();
                if (columnCount > maxColumns) {
                    throw new PracticeExecutionException(HttpStatus.UNPROCESSABLE_CONTENT, "SQL_EXECUTION_ERROR",
                        "The result has too many columns (maximum " + maxColumns + ").");
                }
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) columns.add(meta.getColumnLabel(i));
                List<List<Object>> rows = new ArrayList<>();
                boolean truncated = false;
                while (resultSet.next()) {
                    if (rows.size() == maxRows) {
                        truncated = true;
                        break;
                    }
                    List<Object> row = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) row.add(cell(resultSet.getObject(i)));
                    rows.add(row);
                }
                long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                return new ResultTable(columns, rows, truncated, elapsed);
            }
        }
    }

    private Object cell(Object value) {
        if (value == null) return null;
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigDecimal || value instanceof Double || value instanceof Float) return value;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        if (value instanceof Boolean) return value;
        String text = value instanceof TemporalAccessor || value instanceof java.util.Date ? value.toString() : String.valueOf(value);
        return text.length() > maxCellChars ? text.substring(0, maxCellChars) + "…" : text;
    }

    /** Clean, user-facing SQL errors: no stack traces, no SQL echo, no database names. */
    PracticeExecutionException translate(SQLException ex) {
        int code = ex.getErrorCode();
        String raw = cleanMessage(ex.getMessage());
        return switch (code) {
            case 57014 -> new PracticeExecutionException(HttpStatus.UNPROCESSABLE_CONTENT, "SQL_TIMEOUT",
                "Query timed out (limit " + queryTimeoutSeconds + " seconds).");
            case 42122 -> new PracticeExecutionException(HttpStatus.UNPROCESSABLE_CONTENT, "SQL_EXECUTION_ERROR",
                "Unknown column " + quotedName(ex.getMessage(), "") + ".");
            case 42102, 42104, 42103 -> new PracticeExecutionException(HttpStatus.UNPROCESSABLE_CONTENT, "SQL_EXECUTION_ERROR",
                "Unknown table " + quotedName(ex.getMessage(), "") + ". Use the tables shown in the schema.");
            case 42000, 42001 -> new PracticeExecutionException(HttpStatus.UNPROCESSABLE_CONTENT, "SQL_EXECUTION_ERROR",
                "Syntax error near " + syntaxContext(ex.getMessage()) + ".");
            case 90096, 90040 -> new PracticeExecutionException(HttpStatus.BAD_REQUEST, "SQL_NOT_ALLOWED",
                "Statement type not allowed in practice queries.");
            default -> {
                log.debug("Practice SQL error {}: {}", code, raw);
                yield new PracticeExecutionException(HttpStatus.UNPROCESSABLE_CONTENT, "SQL_EXECUTION_ERROR", "SQL error: " + raw);
            }
        };
    }

    private static String cleanMessage(String message) {
        String first = message == null ? "Query failed" : message.split("\\R", 2)[0];
        // Drop the echoed statement ("...; SQL statement:") and H2's [code-version] suffix.
        int sqlEcho = first.indexOf("; SQL statement:");
        if (sqlEcho >= 0) first = first.substring(0, sqlEcho);
        first = H2_CODE.matcher(first).replaceAll("");
        if (first.contains("jdbc:") || first.contains("sbx_")) return "The practice database could not be opened. Please try again.";
        return first.length() > 200 ? first.substring(0, 200) + "…" : first;
    }

    private static String quotedName(String message, String fallback) {
        Matcher matcher = NOT_FOUND_NAME.matcher(message == null ? "" : message);
        return matcher.find() ? "'" + matcher.group(1) + "'" : fallback;
    }

    /** H2 marks the error position with [*]: show a few words around it. */
    private static String syntaxContext(String message) {
        String text = message == null ? "" : message.split("\\R", 2)[0];
        int marker = text.indexOf("[*]");
        if (marker < 0) return "the end of the query";
        String after = text.substring(marker + 3).replaceAll("\";?\\s*SQL statement:.*$", "").replaceAll("\".*$", "").trim();
        String before = text.substring(Math.max(text.lastIndexOf('"', marker) + 1, marker - 25), marker).trim();
        String around = (before + " " + after).trim();
        if (around.length() > 40) around = around.substring(0, 40) + "…";
        return around.isEmpty() ? "the end of the query" : "'" + around + "'";
    }

    private static Connection connect(String url, Properties credentials) throws SQLException {
        Connection connection = H2.connect(url, credentials);
        if (connection == null) throw new SQLException("Sandbox database could not be opened");
        return connection;
    }

    private static Properties credentials(String user, String password) {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        return properties;
    }

    private static String secret() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
