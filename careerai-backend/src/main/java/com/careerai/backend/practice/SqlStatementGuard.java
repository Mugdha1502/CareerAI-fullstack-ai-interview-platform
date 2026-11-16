package com.careerai.backend.practice;

import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * First line of defence for practice SQL: exactly ONE read-only statement (SELECT / WITH).
 *
 * A small tokenizer skips string literals ('..' with '' escapes), quoted identifiers ("..", `..`)
 * and comments (-- and /* *\/), so a ';' or keyword inside a string or comment is not mistaken
 * for a second statement. This is defence in depth only — the real boundary is the disposable H2
 * database and its SELECT-only user (see SqlSandbox).
 */
public final class SqlStatementGuard {

    /** Statement keywords and H2/MySQL functions that are never allowed in practice queries. */
    private static final Set<String> FORBIDDEN = Set.of(
        "DROP", "CREATE", "ALTER", "INSERT", "UPDATE", "DELETE", "MERGE", "UPSERT", "TRUNCATE",
        "GRANT", "REVOKE", "SHUTDOWN", "RUNSCRIPT", "SCRIPT", "BACKUP", "CALL", "EXECUTE", "EXEC",
        "SET", "COMMIT", "ROLLBACK", "CHECKPOINT", "INTO",
        "LINK_SCHEMA", "FILE_READ", "FILE_WRITE", "CSVREAD", "CSVWRITE", "LOAD_FILE", "DATABASE_PATH",
        // unbounded row/size generators (the sandbox shares the JVM's CPU and memory)
        "RECURSIVE", "SYSTEM_RANGE", "GENERATE_SERIES", "REPEAT", "SPACE", "LPAD", "RPAD"
    );

    private SqlStatementGuard() {
    }

    /** Validates the query and returns it without a trailing semicolon. */
    public static String requireSingleSelect(String query) {
        String sql = query == null ? "" : query.strip();
        if (sql.isEmpty()) {
            throw notAllowed("Write a query first.");
        }
        Tokens tokens = tokenize(sql);
        if (tokens.unterminated) {
            throw new PracticeExecutionException(HttpStatus.UNPROCESSABLE_CONTENT, "SQL_EXECUTION_ERROR", "Syntax error: unterminated string, identifier or comment.");
        }
        if (tokens.statementCount > 1) {
            throw notAllowed("Multiple statements are not allowed. Submit one query at a time.");
        }
        if (tokens.words.isEmpty()) {
            throw notAllowed("Write a query first.");
        }
        String first = tokens.words.getFirst();
        if (!first.equals("SELECT") && !first.equals("WITH")) {
            throw notAllowed("Statement type not allowed: only SELECT queries (optionally starting with WITH) are allowed.");
        }
        for (String word : tokens.words) {
            if (FORBIDDEN.contains(word)) {
                throw notAllowed(word + " is not allowed in practice queries.");
            }
        }
        return sql.substring(0, tokens.endOfStatement).strip();
    }

    private static PracticeExecutionException notAllowed(String message) {
        return new PracticeExecutionException(HttpStatus.BAD_REQUEST, "SQL_NOT_ALLOWED", message);
    }

    static final class Tokens {
        final List<String> words = new ArrayList<>();
        int statementCount = 0;
        int endOfStatement;
        boolean unterminated;
    }

    static Tokens tokenize(String sql) {
        Tokens tokens = new Tokens();
        tokens.endOfStatement = sql.length();
        boolean contentInCurrent = false;
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                int end = closing(sql, i, c);
                if (end < 0) { tokens.unterminated = true; return tokens; }
                contentInCurrent = true;
                i = end + 1;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                // '#' is deliberately NOT a comment here: H2 does not treat it as one, and the guard
                // must never consider text a comment that the database would execute.
                int end = sql.indexOf('\n', i);
                i = end < 0 ? n : end + 1;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) { tokens.unterminated = true; return tokens; }
                i = end + 2;
            } else if (c == ';') {
                if (contentInCurrent) {
                    tokens.statementCount++;
                    if (tokens.statementCount == 1) tokens.endOfStatement = i;
                }
                contentInCurrent = false;
                i++;
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_' || sql.charAt(i) == '$')) i++;
                tokens.words.add(sql.substring(start, i).toUpperCase(Locale.ROOT));
                contentInCurrent = true;
            } else {
                if (!Character.isWhitespace(c)) contentInCurrent = true;
                i++;
            }
        }
        if (contentInCurrent) tokens.statementCount++;
        return tokens;
    }

    /** Index of the closing quote (doubled quotes are escapes), or -1. */
    private static int closing(String sql, int open, char quote) {
        int i = open + 1;
        while (i < sql.length()) {
            if (sql.charAt(i) == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i;
            }
            i++;
        }
        return -1;
    }
}
