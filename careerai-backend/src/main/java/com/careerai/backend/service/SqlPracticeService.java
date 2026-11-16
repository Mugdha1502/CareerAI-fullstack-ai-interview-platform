package com.careerai.backend.service;

import com.careerai.backend.billing.FeatureKey;
import com.careerai.backend.billing.EntitlementService;
import com.careerai.backend.dto.PracticeHistoryItem;
import com.careerai.backend.dto.SqlQueryRequest;
import com.careerai.backend.dto.SqlQueryResponse;
import com.careerai.backend.dto.SqlQuestionResponse;
import com.careerai.backend.dto.SqlSubmitRequest;
import com.careerai.backend.dto.SqlSubmitResponse;
import com.careerai.backend.entity.User;
import com.careerai.backend.practice.PracticeExecutionException;
import com.careerai.backend.practice.PracticeRateLimiter;
import com.careerai.backend.practice.SqlQuestion;
import com.careerai.backend.practice.SqlQuestionCatalog;
import com.careerai.backend.practice.SqlResultComparator;
import com.careerai.backend.practice.SqlSandbox;
import com.careerai.backend.practice.SqlStatementGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL practice: Run Query (result table, never saved) and Submit Query (result compared with the
 * reference result on the visible data AND on hidden extra rows, scored and saved).
 * Every query runs in a fresh disposable H2 database (SqlSandbox); the CareerAI MySQL database
 * and the application DataSource are never involved.
 */
@Service
public class SqlPracticeService {

    private final SqlQuestionCatalog catalog;
    private final SqlSandbox sandbox;
    private final EntitlementService entitlementService;
    private final PracticeRateLimiter rateLimiter;
    private final PracticeService practiceService;
    private final CurrentUserService currentUserService;
    private final int runPerMinute;
    private final int submitPerMinute;

    /** Reference results are deterministic: computed once per question and data variant. */
    private final Map<String, SqlSandbox.ResultTable> referenceCache = new ConcurrentHashMap<>();
    private final Map<String, List<SqlQuestionResponse.Table>> sampleCache = new ConcurrentHashMap<>();

    public SqlPracticeService(
        SqlQuestionCatalog catalog,
        SqlSandbox sandbox,
        EntitlementService entitlementService,
        PracticeRateLimiter rateLimiter,
        PracticeService practiceService,
        CurrentUserService currentUserService,
        @Value("${app.practice.rate.sql-run-per-minute:20}") int runPerMinute,
        @Value("${app.practice.rate.sql-submit-per-minute:10}") int submitPerMinute
    ) {
        this.catalog = catalog;
        this.sandbox = sandbox;
        this.entitlementService = entitlementService;
        this.rateLimiter = rateLimiter;
        this.practiceService = practiceService;
        this.currentUserService = currentUserService;
        this.runPerMinute = runPerMinute;
        this.submitPerMinute = submitPerMinute;
    }

    public SqlQuestionResponse question(String id, String dataset, String topic) {
        SqlQuestion question = id != null && !id.isBlank() ? catalog.require(id) : catalog.requireByDatasetAndTopic(dataset, topic);
        SqlQuestion.Dataset data = catalog.dataset(question);
        return new SqlQuestionResponse(question.id(), data.name(), data.description(), question.topic(), question.difficulty(),
            question.question(), question.hint(), sampleTables(data));
    }

    public SqlQueryResponse run(String email, SqlQueryRequest request) {
        User user = currentUserService.require(email);
        SqlQuestion question = catalog.require(request.questionId());
        String query = SqlStatementGuard.requireSingleSelect(request.query());
        rateLimiter.check(user.getId(), "sql-run", runPerMinute);
        // SQL_RUN counts every query the sandbox executed, including ones that returned a SQL error;
        // rejected statements (not allowed / invalid request) never reach this point.
        return entitlementService.consume(user, FeatureKey.SQL_RUN,
            () -> toResponse(sandbox.run(catalog.dataset(question), false, query, true)), SqlPracticeService::executedInSandbox);
    }

    public SqlSubmitResponse submit(String email, SqlSubmitRequest request) {
        User user = currentUserService.require(email);
        SqlQuestion question = catalog.require(request.questionId());
        String query = SqlStatementGuard.requireSingleSelect(request.query());
        rateLimiter.check(user.getId(), "sql-submit", submitPerMinute);
        // SQL_SUBMIT counts a scored submission (a query error is a scored 0 attempt).
        return entitlementService.consume(user, FeatureKey.SQL_SUBMIT, () -> evaluateSubmission(email, request, question, query));
    }

    /** Failures that still used the sandbox: the query ran and returned a SQL error or timed out. */
    private static boolean executedInSandbox(RuntimeException failure) {
        return failure instanceof PracticeExecutionException ex && ("SQL_EXECUTION_ERROR".equals(ex.code()) || "SQL_TIMEOUT".equals(ex.code()));
    }

    private SqlSubmitResponse evaluateSubmission(String email, SqlSubmitRequest request, SqlQuestion question, String query) {
        SqlQuestion.Dataset data = catalog.dataset(question);

        int total = 2;
        int passed = 0;
        SqlQueryResponse output = null;
        String status;
        String message;
        String errorMessage = null;
        try {
            SqlSandbox.ResultTable visible = sandbox.run(data, false, query, true);
            output = toResponse(visible);
            boolean visibleOk = SqlResultComparator.matches(question.evaluationMode(), visible, reference(question, data, false));
            SqlSandbox.ResultTable hidden = sandbox.run(data, true, query, true);
            boolean hiddenOk = SqlResultComparator.matches(question.evaluationMode(), hidden, reference(question, data, true));
            passed = (visibleOk ? 1 : 0) + (hiddenOk ? 1 : 0);
            status = passed == total ? "CORRECT" : visibleOk ? "PARTIAL" : "INCORRECT";
            message = switch (status) {
                case "CORRECT" -> "Correct — your query returns the expected result, including on hidden data.";
                case "PARTIAL" -> "Your result matches the sample data but not the hidden data. Avoid hard-coding values.";
                default -> "Your result does not match the expected result.";
            };
        } catch (PracticeExecutionException ex) {
            if (!"SQL_EXECUTION_ERROR".equals(ex.code()) && !"SQL_TIMEOUT".equals(ex.code())) throw ex;
            // The query ran but failed (syntax, unknown column, timeout): a scored 0 attempt.
            status = "ERROR";
            message = "Your query could not be executed.";
            errorMessage = ex.getMessage();
        }

        PracticeHistoryItem saved = practiceService.saveScoredSubmission(email, new PracticeService.ScoredSubmission(
            "sql", question.topic(), question.difficulty(), question.id(), null, status, passed, total, request.sessionId()));
        return new SqlSubmitResponse(status, passed, total, (int) Math.round(passed * 100.0 / total), message, errorMessage, output, saved);
    }

    private SqlSandbox.ResultTable reference(SqlQuestion question, SqlQuestion.Dataset data, boolean hidden) {
        return referenceCache.computeIfAbsent(question.id() + (hidden ? ":hidden" : ":visible"),
            key -> sandbox.run(data, hidden, question.referenceQuery(), false));
    }

    /** Sample rows are exactly the visible seed data (SELECT * per table in a sandbox). */
    private List<SqlQuestionResponse.Table> sampleTables(SqlQuestion.Dataset data) {
        return sampleCache.computeIfAbsent(data.name(), key -> {
            List<SqlQuestionResponse.Table> tables = new ArrayList<>();
            for (SqlQuestion.Table table : data.tables()) {
                SqlSandbox.ResultTable rows = sandbox.run(data, false, "SELECT * FROM " + table.name(), false);
                tables.add(new SqlQuestionResponse.Table(table.name(), table.columns(), rows.columns(), rows.rows()));
            }
            return tables;
        });
    }

    private static SqlQueryResponse toResponse(SqlSandbox.ResultTable table) {
        return new SqlQueryResponse(table.columns(), table.rows(), table.rows().size(), table.truncated(), table.executionTimeMs());
    }
}
