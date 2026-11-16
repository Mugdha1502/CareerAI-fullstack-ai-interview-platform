package com.careerai.backend.practice;

import com.careerai.backend.execution.CodeLanguage;
import com.careerai.backend.execution.FakeCodeExecutionProvider;
import com.careerai.backend.repository.PracticeResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Full stack (security, controllers, services, JPA on the in-memory test database) for coding and
 * SQL practice. Code execution uses FakeCodeExecutionProvider — never Judge0 — and SQL runs in
 * real disposable H2 sandboxes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PracticeExecutionIntegrationTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger USERS = new AtomicInteger();

    @TestConfiguration
    static class FakeProviderConfig {
        @Bean
        @Primary
        FakeCodeExecutionProvider fakeCodeExecutionProvider(CodingQuestionCatalog catalog) {
            return new FakeCodeExecutionProvider(catalog);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FakeCodeExecutionProvider fake;

    @Autowired
    private PracticeResultRepository resultRepository;

    @BeforeEach
    void reset() {
        fake.configured = true;
        fake.timeout = false;
        fake.requests.clear();
    }

    /* ---------- helpers ---------- */

    private String register() throws Exception {
        String email = "junit-" + USERS.incrementAndGet() + "@example.test";
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("name", "JUnit User", "email", email, "password", "secret123", "confirmPassword", "secret123"))))
            .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return JSON.readTree(result.getResponse().getContentAsString()).path("token").asString();
    }

    private record Response(int status, JsonNode body) {
    }

    private Response call(String method, String path, String token, Object body) throws Exception {
        var request = method.equals("GET") ? get(path) : post(path).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        MvcResult result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString();
        return new Response(result.getResponse().getStatus(), content.isBlank() ? JSON.nullNode() : JSON.readTree(content));
    }

    private static Map<String, Object> code(String language, String source, String stdin) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("language", language);
        body.put("sourceCode", source);
        body.put("stdin", stdin);
        return body;
    }

    private static Map<String, Object> submit(String questionId, String language, String source) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("questionId", questionId);
        body.put("language", language);
        body.put("sourceCode", source);
        return body;
    }

    private int historySize(String token) throws Exception {
        return call("GET", "/api/practice/history", token, null).body().path("totalItems").asInt();
    }

    /* ---------- security / ownership ---------- */

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        assertThat(call("POST", "/api/practice/coding/execute", null, code("JAVA", "class Main {}", "")).status()).isEqualTo(401);
        assertThat(call("POST", "/api/practice/coding/submit", null, submit("coding-dp-climbing-stairs", "JAVA", "x")).status()).isEqualTo(401);
        assertThat(call("POST", "/api/practice/sql/execute", null, Map.of("questionId", "sql-hr-select", "query", "SELECT 1")).status()).isEqualTo(401);
        assertThat(call("POST", "/api/practice/sql/submit", null, Map.of("questionId", "sql-hr-select", "query", "SELECT 1")).status()).isEqualTo(401);
        assertThat(call("GET", "/api/practice/coding/question?topic=Arrays", null, null).status()).isEqualTo(401);
    }

    @Test
    void questionsNeverExposeHiddenTestsOrReferenceQueries() throws Exception {
        String token = register();
        Response coding = call("GET", "/api/practice/coding/question?topic=Arrays", token, null);
        assertThat(coding.status()).isEqualTo(200);
        assertThat(coding.body().path("id").asString()).isEqualTo("coding-arrays-second-largest");
        assertThat(coding.body().path("starterCode").path("JAVA").asString()).contains("public class Main");
        assertThat(coding.body().path("hiddenTestCount").asInt()).isEqualTo(6);
        assertThat(coding.body().toString()).doesNotContain("hiddenTests").doesNotContain("1000000000 -1000000000 999999999");

        Response sql = call("GET", "/api/practice/sql/question?id=sql-hr-join", token, null);
        assertThat(sql.status()).isEqualTo(200);
        assertThat(sql.body().path("tables").get(1).path("sampleRows").size()).isEqualTo(3);
        assertThat(sql.body().toString()).doesNotContain("referenceQuery").doesNotContain("Kabir Nair").doesNotContain("hiddenSeed");
    }

    @Test
    void ownershipAndSpoofedUserIdsAreIgnored() throws Exception {
        String alice = register();
        String bob = register();
        Response session = call("POST", "/api/practice/sessions", alice, Map.of("module", "coding", "topic", "Dynamic Programming", "difficulty", "Beginner"));
        long aliceSession = session.body().path("id").asLong();

        Map<String, Object> stolen = submit("coding-dp-climbing-stairs", "PYTHON", "SOLVED coding-dp-climbing-stairs");
        stolen.put("sessionId", aliceSession);
        assertThat(call("POST", "/api/practice/coding/submit", bob, stolen).status()).isEqualTo(404);

        Map<String, Object> spoof = submit("coding-dp-climbing-stairs", "PYTHON", "SOLVED coding-dp-climbing-stairs");
        spoof.put("userId", 1);
        spoof.put("email", "someone-else@example.test");
        assertThat(call("POST", "/api/practice/coding/submit", bob, spoof).status()).isEqualTo(200);
        assertThat(historySize(bob)).isEqualTo(1);
        assertThat(historySize(alice)).isZero();

        Map<String, Object> mine = submit("coding-dp-climbing-stairs", "PYTHON", "SOLVED coding-dp-climbing-stairs");
        mine.put("sessionId", aliceSession);
        Response saved = call("POST", "/api/practice/coding/submit", alice, mine);
        assertThat(saved.body().path("result").path("sessionId").asLong()).isEqualTo(aliceSession);
        assertThat(historySize(alice)).isEqualTo(1);
        assertThat(historySize(bob)).isEqualTo(1);
    }

    /* ---------- coding ---------- */

    @Test
    void runJavaAndPythonWithoutSavingResults() throws Exception {
        String token = register();
        long resultsBefore = resultRepository.count();
        Response java = call("POST", "/api/practice/coding/execute", token, code("JAVA", "public class Main {}", "1"));
        assertThat(java.status()).isEqualTo(200);
        assertThat(java.body().path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(java.body().path("stdout").asString()).isEqualTo("Hello from JAVA\n");
        Response python = call("POST", "/api/practice/coding/execute", token, code("PYTHON", "print('hi')", ""));
        assertThat(python.body().path("stdout").asString()).isEqualTo("Hello from PYTHON\n");
        assertThat(fake.requests).extracting(r -> r.language()).containsExactly(CodeLanguage.JAVA, CodeLanguage.PYTHON);
        // Server-side limits, never from the client.
        assertThat(fake.requests.getFirst().limits().memoryLimitKb()).isEqualTo(256000);
        assertThat(fake.requests.getFirst().limits().cpuTimeLimitSeconds()).isEqualTo(2.5);
        assertThat(resultRepository.count()).isEqualTo(resultsBefore);
        assertThat(historySize(token)).isZero();
    }

    @Test
    void runShowsExpectedOutputForSampleInput() throws Exception {
        String token = register();
        Map<String, Object> body = code("JAVA", "SOLVED coding-dp-climbing-stairs", "3");
        body.put("questionId", "coding-dp-climbing-stairs");
        Response run = call("POST", "/api/practice/coding/execute", token, body);
        assertThat(run.body().path("expectedOutput").asString()).isEqualTo("3");
        assertThat(run.body().path("matchesExpected").asBoolean()).isTrue();
    }

    @Test
    void compilationRuntimeAndTimeLimitResults() throws Exception {
        String token = register();
        Response compile = call("POST", "/api/practice/coding/execute", token, code("JAVA", "COMPILE_ERROR", ""));
        assertThat(compile.body().path("status").asString()).isEqualTo("COMPILATION_ERROR");
        assertThat(compile.body().path("compileOutput").asString()).contains("';' expected");
        Response runtime = call("POST", "/api/practice/coding/execute", token, code("JAVA", "RUNTIME_ERROR", ""));
        assertThat(runtime.body().path("status").asString()).isEqualTo("RUNTIME_ERROR");
        assertThat(runtime.body().path("stderr").asString()).contains("ArithmeticException");
        Response slow = call("POST", "/api/practice/coding/execute", token, code("CPP", "SLOW", ""));
        assertThat(slow.body().path("status").asString()).isEqualTo("TIME_LIMIT_EXCEEDED");
    }

    @Test
    void providerUnavailableAndPollingTimeout() throws Exception {
        String token = register();
        fake.configured = false;
        Response unavailable = call("POST", "/api/practice/coding/execute", token, code("JAVA", "x", ""));
        assertThat(unavailable.status()).isEqualTo(503);
        assertThat(unavailable.body().path("code").asString()).isEqualTo("EXECUTION_PROVIDER_UNAVAILABLE");
        assertThat(unavailable.body().path("message").asString()).isEqualTo("Code execution is temporarily unavailable.");
        assertThat(call("GET", "/api/practice/coding/status", token, null).body().path("executionAvailable").asBoolean()).isFalse();
        fake.configured = true;
        fake.timeout = true;
        Response timeout = call("POST", "/api/practice/coding/submit", token, submit("coding-dp-climbing-stairs", "JAVA", "x"));
        assertThat(timeout.status()).isEqualTo(504);
        assertThat(timeout.body().path("code").asString()).isEqualTo("EXECUTION_TIMEOUT");
        assertThat(historySize(token)).isZero(); // provider failures are never scored
    }

    @Test
    void validationOversizedSourceStdinAndUnsupportedLanguage() throws Exception {
        String token = register();
        Response big = call("POST", "/api/practice/coding/execute", token, code("JAVA", "x".repeat(51201), ""));
        assertThat(big.status()).isEqualTo(400);
        assertThat(big.body().path("message").asString()).contains("50 KB");
        Response bigStdin = call("POST", "/api/practice/coding/execute", token, code("JAVA", "x", "1".repeat(20481)));
        assertThat(bigStdin.status()).isEqualTo(400);
        Response language = call("POST", "/api/practice/coding/execute", token, code("RUBY", "puts 1", ""));
        assertThat(language.status()).isEqualTo(400);
        assertThat(language.body().path("code").asString()).isEqualTo("INVALID_LANGUAGE");
        Response unknown = call("POST", "/api/practice/coding/submit", token, submit("coding-does-not-exist", "JAVA", "x"));
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.body().path("code").asString()).isEqualTo("QUESTION_NOT_FOUND");
        assertThat(fake.requests).isEmpty();
    }

    @Test
    void submitAllPassedAndPartialAreScoredAndSaved() throws Exception {
        String token = register();
        Response all = call("POST", "/api/practice/coding/submit", token, submit("coding-dp-climbing-stairs", "JAVA", "SOLVED coding-dp-climbing-stairs"));
        assertThat(all.status()).isEqualTo(200);
        assertThat(all.body().path("status").asString()).isEqualTo("PASSED");
        assertThat(all.body().path("passedTests").asInt()).isEqualTo(6);
        assertThat(all.body().path("totalTests").asInt()).isEqualTo(6);
        assertThat(all.body().path("score").asInt()).isEqualTo(100);
        assertThat(all.body().toString()).doesNotContain("\"stdin\"").doesNotContain("expectedOutput");

        Response partial = call("POST", "/api/practice/coding/submit", token, submit("coding-arrays-second-largest", "PYTHON", "PASS_FIRST_3 coding-arrays-second-largest"));
        assertThat(partial.body().path("status").asString()).isEqualTo("PARTIAL");
        assertThat(partial.body().path("passedTests").asInt()).isEqualTo(3);
        assertThat(partial.body().path("score").asInt()).isEqualTo(50);
        assertThat(partial.body().path("errorMessage").asString()).isEqualTo("Wrong answer on a hidden test.");

        JsonNode history = call("GET", "/api/practice/history?module=coding", token, null).body().path("items");
        assertThat(history.size()).isEqualTo(2);
        JsonNode latest = history.get(0);
        assertThat(latest.path("module").asString()).isEqualTo("coding");
        assertThat(latest.path("questionId").asString()).isEqualTo("coding-arrays-second-largest");
        assertThat(latest.path("language").asString()).isEqualTo("PYTHON");
        assertThat(latest.path("executionStatus").asString()).isEqualTo("PARTIAL");
        assertThat(latest.path("accuracy").asInt()).isEqualTo(50);
        assertThat(latest.path("topic").asString()).isEqualTo("Arrays");
    }

    @Test
    void compileErrorSubmissionScoresZero() throws Exception {
        String token = register();
        Response compile = call("POST", "/api/practice/coding/submit", token, submit("coding-dp-climbing-stairs", "JAVA", "COMPILE_ERROR"));
        assertThat(compile.body().path("status").asString()).isEqualTo("COMPILATION_ERROR");
        assertThat(compile.body().path("score").asInt()).isZero();
        assertThat(compile.body().path("compileOutput").asString()).contains("';' expected");
    }

    @Test
    void codeRunIsRateLimited() throws Exception {
        String token = register();
        for (int i = 0; i < 10; i++) {
            assertThat(call("POST", "/api/practice/coding/execute", token, code("JAVA", "x", "")).status()).isEqualTo(200);
        }
        Response limited = call("POST", "/api/practice/coding/execute", token, code("JAVA", "x", ""));
        assertThat(limited.status()).isEqualTo(429);
        assertThat(limited.body().path("code").asString()).isEqualTo("RATE_LIMITED");
        // Another user is not affected.
        assertThat(call("POST", "/api/practice/coding/execute", register(), code("JAVA", "x", "")).status()).isEqualTo(200);
    }

    /* ---------- SQL ---------- */

    @Test
    void sqlRunReturnsBoundedTableAndIsNotSaved() throws Exception {
        String token = register();
        Response run = call("POST", "/api/practice/sql/execute", token, Map.of("questionId", "sql-hr-join",
            "query", "SELECT e.name AS employee, d.name AS department FROM employees e JOIN departments d ON d.id = e.department_id ORDER BY e.id"));
        assertThat(run.status()).isEqualTo(200);
        assertThat(run.body().path("columns").toString()).isEqualTo("[\"employee\",\"department\"]");
        assertThat(run.body().path("rowCount").asInt()).isEqualTo(3);
        assertThat(run.body().path("rows").get(0).toString()).isEqualTo("[\"Asha Rao\",\"Engineering\"]");
        assertThat(historySize(token)).isZero();
    }

    @Test
    void sqlCannotSeeTheApplicationDatabase() throws Exception {
        // In this test the application's own datasource (H2 test DB) HAS a users table with rows.
        String token = register();
        Response users = call("POST", "/api/practice/sql/execute", token, Map.of("questionId", "sql-hr-select", "query", "SELECT * FROM users"));
        assertThat(users.status()).isEqualTo(422);
        assertThat(users.body().path("message").asString()).contains("Unknown table");
        Response results = call("POST", "/api/practice/sql/execute", token, Map.of("questionId", "sql-hr-select", "query", "SELECT * FROM practice_results"));
        assertThat(results.status()).isEqualTo(422);
        Response tables = call("POST", "/api/practice/sql/execute", token, Map.of("questionId", "sql-hr-select",
            "query", "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"));
        assertThat(tables.body().path("rows").toString().toLowerCase()).doesNotContain("users").doesNotContain("practice");
    }

    @Test
    void sqlErrorsAndRestrictions() throws Exception {
        String token = register();
        Response syntax = call("POST", "/api/practice/sql/execute", token, Map.of("questionId", "sql-hr-select", "query", "SELECT name FROM employees WHERE"));
        assertThat(syntax.status()).isEqualTo(422);
        assertThat(syntax.body().path("code").asString()).isEqualTo("SQL_EXECUTION_ERROR");
        assertThat(syntax.body().path("message").asString()).startsWith("Syntax error near").doesNotContain("org.h2").doesNotContain("sbx_");
        Response column = call("POST", "/api/practice/sql/execute", token, Map.of("questionId", "sql-hr-select", "query", "SELECT nope FROM employees"));
        assertThat(column.body().path("message").asString()).isEqualTo("Unknown column 'nope'.");
        Response drop = call("POST", "/api/practice/sql/execute", token, Map.of("questionId", "sql-hr-select", "query", "DROP TABLE employees"));
        assertThat(drop.status()).isEqualTo(400);
        assertThat(drop.body().path("code").asString()).isEqualTo("SQL_NOT_ALLOWED");
        Response multi = call("POST", "/api/practice/sql/execute", token, Map.of("questionId", "sql-hr-select", "query", "SELECT 1; SELECT 2"));
        assertThat(multi.body().path("message").asString()).isEqualTo("Multiple statements are not allowed. Submit one query at a time.");
        Response big = call("POST", "/api/practice/sql/execute", token, Map.of("questionId", "sql-hr-select", "query", "SELECT '" + "x".repeat(20481) + "'"));
        assertThat(big.status()).isEqualTo(400);
        Response unknown = call("POST", "/api/practice/sql/execute", token, Map.of("questionId", "sql-nope", "query", "SELECT 1"));
        assertThat(unknown.status()).isEqualTo(404);
    }

    @Test
    void sqlSubmissionCorrectPartialIncorrectAndError() throws Exception {
        String token = register();
        Response correct = call("POST", "/api/practice/sql/submit", token, Map.of("questionId", "sql-hr-having",
            "query", "SELECT d.name, COUNT(*) FROM employees e JOIN departments d ON d.id = e.department_id GROUP BY d.name HAVING COUNT(*) > 1"));
        assertThat(correct.status()).isEqualTo(200);
        assertThat(correct.body().path("status").asString()).isEqualTo("CORRECT");
        assertThat(correct.body().path("passedChecks").asInt()).isEqualTo(2);
        assertThat(correct.body().path("score").asInt()).isEqualTo(100);

        // Hard-coding the visible answer passes the visible data but not the hidden rows.
        Response hardcoded = call("POST", "/api/practice/sql/submit", token, Map.of("questionId", "sql-hr-having",
            "query", "SELECT 'Engineering' AS department, 2 AS employee_count"));
        assertThat(hardcoded.body().path("status").asString()).isEqualTo("PARTIAL");
        assertThat(hardcoded.body().path("score").asInt()).isEqualTo(50);

        Response incorrect = call("POST", "/api/practice/sql/submit", token, Map.of("questionId", "sql-hr-having", "query", "SELECT name FROM departments"));
        assertThat(incorrect.body().path("status").asString()).isEqualTo("INCORRECT");
        assertThat(incorrect.body().path("score").asInt()).isZero();

        Response error = call("POST", "/api/practice/sql/submit", token, Map.of("questionId", "sql-hr-having", "query", "SELECT missing_column FROM employees"));
        assertThat(error.body().path("status").asString()).isEqualTo("ERROR");
        assertThat(error.body().path("errorMessage").asString()).contains("Unknown column");

        Response ordered = call("POST", "/api/practice/sql/submit", token, Map.of("questionId", "sql-retail-window-functions",
            "query", "SELECT order_date, total, SUM(total) OVER (ORDER BY order_date) AS running FROM orders ORDER BY order_date DESC"));
        assertThat(ordered.body().path("status").asString()).isEqualTo("INCORRECT"); // ORDERED mode

        JsonNode history = call("GET", "/api/practice/history?module=sql", token, null).body().path("items");
        assertThat(history.size()).isEqualTo(5);
        assertThat(history.get(history.size() - 1).path("questionId").asString()).isEqualTo("sql-hr-having");
        assertThat(history.get(history.size() - 1).path("executionStatus").asString()).isEqualTo("CORRECT");
        assertThat(history.get(history.size() - 1).path("accuracy").asInt()).isEqualTo(100);

        // Not-allowed statements are rejected without being saved.
        assertThat(call("POST", "/api/practice/sql/submit", token, Map.of("questionId", "sql-hr-having", "query", "DELETE FROM employees")).status()).isEqualTo(400);
        assertThat(call("GET", "/api/practice/history?module=sql", token, null).body().path("items").size()).isEqualTo(5);
    }

    @Test
    void submissionsFeedPerformance() throws Exception {
        String token = register();
        call("POST", "/api/practice/coding/submit", token, submit("coding-dp-climbing-stairs", "JAVA", "SOLVED coding-dp-climbing-stairs"));
        call("POST", "/api/practice/sql/submit", token, Map.of("questionId", "sql-hr-select", "query", "SELECT name, hire_date FROM employees"));
        Response summary = call("GET", "/api/performance/summary", token, null);
        assertThat(summary.status()).isEqualTo(200);
        assertThat(summary.body().toString()).contains("2");
        Response recent = call("GET", "/api/practice/recent", token, null);
        assertThat(recent.body().size()).isEqualTo(2);
    }
}
