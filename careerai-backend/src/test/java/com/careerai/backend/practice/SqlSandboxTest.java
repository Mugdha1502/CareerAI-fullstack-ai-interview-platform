package com.careerai.backend.practice;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real, isolated H2 sandboxes — no Spring context, no application database involved. */
class SqlSandboxTest {

    private static SqlQuestionCatalog catalog;
    private static SqlSandbox sandbox;
    private static SqlQuestion.Dataset hr;
    private static SqlQuestion.Dataset retail;

    @BeforeAll
    static void setUp() throws Exception {
        catalog = new SqlQuestionCatalog(JsonMapper.builder().build());
        sandbox = new SqlSandbox(1, 200, 30, 500, 4);
        hr = catalog.dataset(catalog.require("sql-hr-select"));
        retail = catalog.dataset(catalog.require("sql-retail-select"));
    }

    private static SqlSandbox.ResultTable candidate(SqlQuestion.Dataset dataset, String query) {
        return sandbox.run(dataset, false, SqlStatementGuard.requireSingleSelect(query), true);
    }

    @Test
    void simpleSelectJoinGroupByCteAndWindow() {
        SqlSandbox.ResultTable select = candidate(hr, "SELECT name, hire_date FROM employees ORDER BY id");
        assertThat(select.columns()).containsExactly("name", "hire_date");
        assertThat(select.rows()).containsExactly(List.of("Asha Rao", "2021-03-15"), List.of("Vikram Shah", "2022-07-01"), List.of("Neha Iyer", "2023-01-09"));

        SqlSandbox.ResultTable join = candidate(hr, "SELECT e.name, d.name AS department FROM employees e JOIN departments d ON d.id = e.department_id ORDER BY e.id");
        assertThat(join.rows().getFirst()).containsExactly("Asha Rao", "Engineering");

        SqlSandbox.ResultTable group = candidate(retail, "SELECT c.city, SUM(o.total) AS total_value FROM orders o JOIN customers c ON c.id = o.customer_id GROUP BY c.city ORDER BY c.city");
        assertThat(group.rows()).hasSize(2);
        assertThat(SqlResultComparator.normalizeCell(group.rows().get(1).get(1))).isEqualTo("n:62500");

        SqlSandbox.ResultTable cte = candidate(hr, "WITH s AS (SELECT e.department_id, x.amount FROM employees e JOIN salaries x ON x.employee_id = e.id) "
            + "SELECT department_id, AVG(amount) AS avg_salary FROM s GROUP BY department_id ORDER BY department_id");
        assertThat(SqlResultComparator.normalizeCell(cte.rows().getFirst().get(1))).isEqualTo("n:91500");

        SqlSandbox.ResultTable window = candidate(retail, "SELECT order_date, total, SUM(total) OVER (ORDER BY order_date) AS running_total FROM orders ORDER BY order_date");
        assertThat(SqlResultComparator.normalizeCell(window.rows().get(2).get(2))).isEqualTo("n:69500");
    }

    @Test
    void cleanErrorsForSyntaxUnknownColumnAndUnknownTable() {
        assertThatThrownBy(() -> candidate(hr, "SELEC name FROM employees"))
            .isInstanceOf(PracticeExecutionException.class);
        assertThatThrownBy(() -> candidate(hr, "SELECT name FROM employees WHERE"))
            .isInstanceOf(PracticeExecutionException.class)
            .satisfies(ex -> {
                assertThat(((PracticeExecutionException) ex).code()).isEqualTo("SQL_EXECUTION_ERROR");
                assertThat(ex.getMessage()).startsWith("Syntax error near").doesNotContain("[").doesNotContain("SQL statement");
            });
        assertThatThrownBy(() -> candidate(hr, "SELECT salary_amount FROM salaries"))
            .hasMessageContaining("Unknown column 'salary_amount'");
        assertThatThrownBy(() -> candidate(hr, "SELECT * FROM payroll"))
            .hasMessageContaining("Unknown table");
    }

    @Test
    void neverSeesCareerAiTables() {
        // The CareerAI schema (users, practice_results, resumes, ...) does not exist in a sandbox.
        for (String table : List.of("users", "practice_results", "practice_sessions", "resumes", "interview_sessions", "profiles")) {
            assertThatThrownBy(() -> candidate(hr, "SELECT * FROM " + table)).hasMessageContaining("Unknown table");
        }
        SqlSandbox.ResultTable tables = candidate(hr, "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name");
        assertThat(tables.rows()).extracting(row -> String.valueOf(row.getFirst()).toLowerCase())
            .containsExactly("departments", "employees", "salaries");
    }

    @Test
    void guardRejectsForbiddenAndMultipleStatements() {
        for (String query : List.of(
            "DROP TABLE employees",
            "DELETE FROM employees",
            "UPDATE employees SET name = 'x'",
            "INSERT INTO employees VALUES (9, 'x', 10, DATE '2024-01-01')",
            "CREATE ALIAS EXEC AS 'String x() { return \"\"; }'",
            "RUNSCRIPT FROM 'evil.sql'",
            "SCRIPT TO 'dump.sql'",
            "BACKUP TO 'backup.zip'",
            "SHUTDOWN",
            "CALL FILE_READ('/etc/passwd')",
            "SELECT FILE_READ('/etc/passwd')",
            "SELECT * FROM CSVREAD('/etc/passwd')",
            "SELECT * FROM LINK_SCHEMA('x', '', 'jdbc:mysql://localhost/careerai', 'root', '', 'careerai')",
            "WITH RECURSIVE t(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM t) SELECT * FROM t",
            "SELECT SPACE(2000000000)"
        )) {
            assertThatThrownBy(() -> SqlStatementGuard.requireSingleSelect(query))
                .as(query)
                .isInstanceOf(PracticeExecutionException.class)
                .satisfies(ex -> assertThat(((PracticeExecutionException) ex).code()).isEqualTo("SQL_NOT_ALLOWED"));
        }
        assertThatThrownBy(() -> SqlStatementGuard.requireSingleSelect("SELECT 1; SELECT 2"))
            .hasMessageContaining("Multiple statements are not allowed");
        assertThatThrownBy(() -> SqlStatementGuard.requireSingleSelect("SELECT 1; DROP TABLE employees"))
            .hasMessageContaining("Multiple statements are not allowed");
    }

    @Test
    void parserAwareSingleStatementHandling() {
        // ';' inside strings, quoted identifiers and comments is not a statement separator.
        assertThat(SqlStatementGuard.requireSingleSelect("SELECT 'a;b' AS x;")).isEqualTo("SELECT 'a;b' AS x");
        assertThat(SqlStatementGuard.requireSingleSelect("SELECT name AS \"semi;colon\" FROM employees -- trailing; comment")).startsWith("SELECT");
        assertThat(SqlStatementGuard.requireSingleSelect("/* drop; */ SELECT 'it''s; fine' FROM employees")).contains("it''s; fine");
        assertThat(candidate(hr, "SELECT 'a;b' AS x").rows().getFirst()).containsExactly("a;b");
        // Keywords inside a string are just data.
        assertThat(candidate(hr, "SELECT 'DROP TABLE employees' AS note").rows().getFirst()).containsExactly("DROP TABLE employees");
    }

    @Test
    void selectOnlyUserBlocksEverythingElseEvenWithoutTheGuard() {
        // Bypass SqlStatementGuard entirely: H2's privileges are the real boundary.
        for (String query : List.of(
            "CREATE TABLE stolen (id INT)",
            "DROP TABLE employees",
            "DELETE FROM employees",
            "INSERT INTO employees VALUES (9, 'x', 10, DATE '2024-01-01')",
            "CREATE ALIAS EXEC_CMD FOR \"java.lang.Runtime.getRuntime\"",
            "SELECT FILE_READ('pom.xml')",
            "CREATE LINKED TABLE linked('org.h2.Driver', 'jdbc:h2:mem:other', 'sa', '', 'x')"
        )) {
            assertThatThrownBy(() -> sandbox.run(hr, false, query, true))
                .as(query)
                .isInstanceOf(PracticeExecutionException.class)
                .satisfies(ex -> assertThat(((PracticeExecutionException) ex).code()).isIn("SQL_NOT_ALLOWED", "SQL_EXECUTION_ERROR"));
        }
        // The data is unchanged afterwards (and every run is a fresh database anyway).
        assertThat(candidate(hr, "SELECT COUNT(*) FROM employees").rows().getFirst().getFirst()).isEqualTo(3L);
    }

    @Test
    void timeoutStopsLongQueries() {
        // Lazy row generation: pure CPU, cancelled by Statement.setQueryTimeout (1 s in this test).
        long started = System.nanoTime();
        assertThatThrownBy(() -> sandbox.run(hr, false, "SELECT COUNT(*) FROM SYSTEM_RANGE(1, 1000000) a, SYSTEM_RANGE(1, 1000000) b", true))
            .isInstanceOf(PracticeExecutionException.class)
            .satisfies(ex -> {
                assertThat(((PracticeExecutionException) ex).code()).isEqualTo("SQL_TIMEOUT");
                assertThat(ex.getMessage()).contains("timed out");
            });
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(10_000);
    }

    @Test
    void rowCapTruncatesLargeResults() {
        SqlSandbox.ResultTable result = candidate(hr, "SELECT a.id FROM employees a, employees b, employees c, employees d, employees e"); // 243 rows
        assertThat(result.rows()).hasSize(200);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void everyExecutionGetsAFreshDatabase() {
        assertThat(sandbox.run(hr, true, "SELECT COUNT(*) FROM employees", true).rows().getFirst().getFirst()).isEqualTo(6L);
        // The hidden rows of the previous run do not leak into the next one.
        assertThat(sandbox.run(hr, false, "SELECT COUNT(*) FROM employees", true).rows().getFirst().getFirst()).isEqualTo(3L);
    }

    @Test
    void everyReferenceQueryRunsOnVisibleAndHiddenData() {
        for (SqlQuestion question : catalog.all()) {
            SqlQuestion.Dataset data = catalog.dataset(question);
            SqlSandbox.ResultTable visible = sandbox.run(data, false, question.referenceQuery(), true);
            SqlSandbox.ResultTable hidden = sandbox.run(data, true, question.referenceQuery(), true);
            assertThat(visible.rows()).as(question.id()).isNotEmpty();
            assertThat(hidden.rows()).as(question.id()).isNotEmpty();
            // A reference query always matches itself (sanity check of the comparison modes).
            assertThat(SqlResultComparator.matches(question.evaluationMode(), visible, visible)).as(question.id()).isTrue();
        }
    }

    @Test
    void referenceResultsMatchTheProblemStatements() {
        SqlSandbox.ResultTable join = sandbox.run(hr, false, catalog.require("sql-hr-join").referenceQuery(), false);
        assertThat(join.rows()).containsExactlyInAnyOrder(List.of("Asha Rao", "Engineering"), List.of("Vikram Shah", "Sales"), List.of("Neha Iyer", "Engineering"));
        SqlSandbox.ResultTable second = sandbox.run(hr, false, catalog.require("sql-hr-subqueries").referenceQuery(), false);
        assertThat(SqlResultComparator.normalizeCell(second.rows().getFirst().getFirst())).isEqualTo("n:92000");
        SqlSandbox.ResultTable having = sandbox.run(retail, false, catalog.require("sql-retail-having").referenceQuery(), false);
        assertThat(having.rows()).hasSize(1);
        assertThat(having.rows().getFirst().getFirst()).isEqualTo("Rahul Mehta");
    }

    @Test
    void comparisonModes() {
        SqlSandbox.ResultTable a = new SqlSandbox.ResultTable(List.of("x", "y"), List.of(List.of("A", 1L), List.of("B", 2L)), false, 0);
        SqlSandbox.ResultTable reversed = new SqlSandbox.ResultTable(List.of("p", "q"), List.of(List.of("B", 2L), List.of("A", 1L)), false, 0);
        SqlSandbox.ResultTable decimal = new SqlSandbox.ResultTable(List.of("x", "y"), List.of(List.of("A", new java.math.BigDecimal("1.0")), List.of("B", 2.0)), false, 0);
        SqlSandbox.ResultTable different = new SqlSandbox.ResultTable(List.of("x", "y"), List.of(List.of("A", 1L), List.of("C", 2L)), false, 0);
        assertThat(SqlResultComparator.matches(SqlQuestion.EvaluationMode.UNORDERED, reversed, a)).isTrue();
        assertThat(SqlResultComparator.matches(SqlQuestion.EvaluationMode.ORDERED, reversed, a)).isFalse();
        assertThat(SqlResultComparator.matches(SqlQuestion.EvaluationMode.ORDERED, decimal, a)).isTrue();
        assertThat(SqlResultComparator.matches(SqlQuestion.EvaluationMode.UNORDERED, different, a)).isFalse();
        assertThat(SqlResultComparator.matches(SqlQuestion.EvaluationMode.ROW_COUNT, different, a)).isTrue();
        SqlSandbox.ResultTable one = new SqlSandbox.ResultTable(List.of("s"), List.of(List.of(92000L)), false, 0);
        SqlSandbox.ResultTable oneText = new SqlSandbox.ResultTable(List.of("salary"), List.of(List.of("92000")), false, 0);
        assertThat(SqlResultComparator.matches(SqlQuestion.EvaluationMode.SCALAR, oneText, one)).isTrue();
    }
}
