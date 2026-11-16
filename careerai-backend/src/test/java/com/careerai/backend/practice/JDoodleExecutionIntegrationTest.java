package com.careerai.backend.practice;

import com.careerai.backend.execution.FakeJDoodleServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
 * Full stack with the REAL JDoodleCodeExecutionProvider (CODE_EXECUTION_PROVIDER=jdoodle) talking to
 * a local fake JDoodle API: Run Code, combined hidden-test submissions (one JDoodle call each), and
 * provider failures that must never create a PracticeResult. No real credits are used.
 */
@SpringBootTest(properties = {
    "app.execution.provider=jdoodle",
    "app.execution.jdoodle.client-id=" + FakeJDoodleServer.CLIENT_ID,
    "app.execution.jdoodle.client-secret=" + FakeJDoodleServer.CLIENT_SECRET,
    "app.execution.jdoodle.read-timeout-seconds=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JDoodleExecutionIntegrationTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger USERS = new AtomicInteger();
    private static final FakeJDoodleServer SERVER = start();

    private static FakeJDoodleServer start() {
        try {
            return new FakeJDoodleServer(new CodingQuestionCatalog(JsonMapper.builder().build()));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void jdoodleUrl(DynamicPropertyRegistry registry) {
        registry.add("app.execution.jdoodle.base-url", SERVER::baseUrl);
    }

    @AfterAll
    static void stop() {
        SERVER.close();
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void reset() {
        SERVER.reset();
    }

    private record Response(int status, JsonNode body) {
    }

    private String register() throws Exception {
        String email = "jdoodle-" + USERS.incrementAndGet() + "@example.test";
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("name", "JDoodle Tester", "email", email, "password", "secret123", "confirmPassword", "secret123")))).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).path("token").asString();
    }

    private Response call(String method, String path, String token, Object body) throws Exception {
        var request = method.equals("GET") ? get(path) : post(path).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        MvcResult result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString();
        return new Response(result.getResponse().getStatus(), content.isBlank() ? JSON.nullNode() : JSON.readTree(content));
    }

    private static Map<String, Object> submit(String language, String source) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("questionId", "coding-dp-climbing-stairs");
        body.put("language", language);
        body.put("sourceCode", source);
        return body;
    }

    private int history(String token) throws Exception {
        return call("GET", "/api/practice/history", token, null).body().path("totalItems").asInt();
    }

    @Test
    void runCodeWithStdinUsesOneCallAndSavesNothing() throws Exception {
        String token = register();
        Response run = call("POST", "/api/practice/coding/execute", token, Map.of("language", "PYTHON", "sourceCode", "print(input())", "stdin", "42"));
        assertThat(run.status()).isEqualTo(200);
        assertThat(run.body().path("status").asString()).isEqualTo("ACCEPTED");
        assertThat(run.body().path("stdout").asString()).isEqualTo("echo:42\n");
        assertThat(run.body().toString()).doesNotContain("statusCode").doesNotContain("cpuTime").doesNotContain(FakeJDoodleServer.CLIENT_SECRET);
        assertThat(SERVER.executeBodies).hasSize(1);
        assertThat(SERVER.executeBodies.getFirst().path("stdin").asString()).isEqualTo("42");
        assertThat(history(token)).isZero();

        Response compile = call("POST", "/api/practice/coding/execute", token, Map.of("language", "JAVA", "sourceCode", "COMPILE_ERROR", "stdin", ""));
        assertThat(compile.body().path("status").asString()).isEqualTo("COMPILATION_ERROR");
        assertThat(compile.body().path("compileOutput").asString()).contains("';' expected");
        Response runtime = call("POST", "/api/practice/coding/execute", token, Map.of("language", "JAVASCRIPT", "sourceCode", "RUNTIME_ERROR", "stdin", ""));
        assertThat(runtime.body().path("status").asString()).isEqualTo("RUNTIME_ERROR");
        assertThat(runtime.body().path("stderr").asString()).contains("ReferenceError");
    }

    @Test
    void submitRunsAllHiddenTestsInOneJDoodleExecution() throws Exception {
        String token = register();
        Response all = call("POST", "/api/practice/coding/submit", token, submit("JAVA", "class Main { /* SOLVED coding-dp-climbing-stairs */ }"));
        assertThat(all.status()).isEqualTo(200);
        assertThat(all.body().path("status").asString()).isEqualTo("PASSED");
        assertThat(all.body().path("passedTests").asInt()).isEqualTo(6);
        assertThat(all.body().path("totalTests").asInt()).isEqualTo(6);
        assertThat(all.body().path("score").asInt()).isEqualTo(100);
        assertThat(SERVER.executeBodies).hasSize(1); // 6 hidden tests, ONE credit
        assertThat(SERVER.executeBodies.getFirst().path("script").asString()).contains("CAREERAI_INPUTS").contains("public class Main");
        assertThat(all.body().toString()).doesNotContain("__CAREERAI_").doesNotContain("expectedOutput");

        SERVER.reset();
        Response partial = call("POST", "/api/practice/coding/submit", token, submit("PYTHON", "# PASS_FIRST_3 coding-dp-climbing-stairs"));
        assertThat(partial.body().path("status").asString()).isEqualTo("PARTIAL");
        assertThat(partial.body().path("passedTests").asInt()).isEqualTo(3);
        assertThat(partial.body().path("score").asInt()).isEqualTo(50);
        assertThat(SERVER.executeBodies).hasSize(1);

        JsonNode items = call("GET", "/api/practice/history?module=coding", token, null).body().path("items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).path("language").asString()).isEqualTo("PYTHON");
        assertThat(items.get(0).path("executionStatus").asString()).isEqualTo("PARTIAL");
        assertThat(items.get(1).path("accuracy").asInt()).isEqualTo(100);
    }

    @Test
    void compileErrorAndTimeoutSubmissionsAreScored() throws Exception {
        String token = register();
        Response compile = call("POST", "/api/practice/coding/submit", token, submit("CPP", "COMPILE_ERROR"));
        assertThat(compile.body().path("status").asString()).isEqualTo("COMPILATION_ERROR");
        assertThat(compile.body().path("score").asInt()).isZero();
        assertThat(compile.body().path("compileOutput").asString()).contains("error");
        Response timeout = call("POST", "/api/practice/coding/submit", token, submit("JAVASCRIPT", "// INFINITE coding-dp-climbing-stairs"));
        assertThat(timeout.body().path("tests").get(1).path("status").asString()).isEqualTo("TIME_LIMIT_EXCEEDED");
        assertThat(history(token)).isEqualTo(2);
    }

    @Test
    void providerFailuresNeverCreateAPracticeResult() throws Exception {
        String token = register();
        SERVER.forcedHttpStatus = 429;
        Response limit = call("POST", "/api/practice/coding/submit", token, submit("JAVA", "SOLVED coding-dp-climbing-stairs"));
        assertThat(limit.status()).isEqualTo(429);
        assertThat(limit.body().path("code").asString()).isEqualTo("EXECUTION_PROVIDER_LIMIT_REACHED");
        assertThat(limit.body().path("message").asString()).isEqualTo("Code execution provider daily limit reached. Please try again later.");
        assertThat(SERVER.executeBodies).hasSize(1); // no retries, no further hidden-test calls

        SERVER.reset();
        SERVER.forcedHttpStatus = 500;
        Response down = call("POST", "/api/practice/coding/submit", token, submit("JAVA", "SOLVED coding-dp-climbing-stairs"));
        assertThat(down.status()).isEqualTo(503);
        assertThat(down.body().path("code").asString()).isEqualTo("EXECUTION_PROVIDER_UNAVAILABLE");

        SERVER.reset();
        SERVER.delayMs = 2000;
        Response slow = call("POST", "/api/practice/coding/submit", token, submit("JAVA", "SOLVED coding-dp-climbing-stairs"));
        assertThat(slow.status()).isEqualTo(504);
        assertThat(slow.body().path("code").asString()).isEqualTo("EXECUTION_TIMEOUT");

        assertThat(history(token)).isZero();

        // A provider failure is not the user's plan limit, and it uses none of their allowance.
        SERVER.reset();
        JsonNode submitUsage = call("GET", "/api/billing/me", token, null).body().path("features").path("CODING_SUBMIT");
        assertThat(submitUsage.path("used").asInt()).isZero();
        assertThat(limit.body().path("code").asString()).isNotEqualTo("PLAN_LIMIT_REACHED");
    }

    @Test
    void oversizedSourceIsRejectedBeforeAnyCall() throws Exception {
        String token = register();
        Response big = call("POST", "/api/practice/coding/execute", token, Map.of("language", "JAVA", "sourceCode", "x".repeat(51201), "stdin", ""));
        assertThat(big.status()).isEqualTo(400);
        assertThat(SERVER.executeBodies).isEmpty();
    }

    @Test
    void jwtIsStillRequired() throws Exception {
        assertThat(call("POST", "/api/practice/coding/execute", null, Map.of("language", "JAVA", "sourceCode", "x", "stdin", "")).status()).isEqualTo(401);
        assertThat(SERVER.executeBodies).isEmpty();
    }
}
