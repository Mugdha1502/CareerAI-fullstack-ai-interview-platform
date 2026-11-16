package com.careerai.backend.execution;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.careerai.backend.practice.CodingQuestionCatalog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JDoodle provider against a local fake JDoodle API — no real credits are used. */
class JDoodleCodeExecutionProviderTest {

    private static final ExecutionLimits LIMITS = new ExecutionLimits(2.5, 5, 256000, 60, 1024);
    private static FakeJDoodleServer server;
    private ListAppender<ILoggingEvent> logs;

    @BeforeAll
    static void startServer() throws Exception {
        server = new FakeJDoodleServer(new CodingQuestionCatalog(JsonMapper.builder().build()));
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void setUp() {
        server.reset();
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(JDoodleCodeExecutionProvider.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(JDoodleCodeExecutionProvider.class)).detachAppender(logs);
        // Credentials never appear in any log line.
        assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains(FakeJDoodleServer.CLIENT_SECRET)
            || event.getFormattedMessage().contains(FakeJDoodleServer.CLIENT_ID));
    }

    private static JDoodleCodeExecutionProvider provider(String id, String secret, int readTimeoutSeconds) {
        return new JDoodleCodeExecutionProvider(server.baseUrl(), id, secret, "", 2, readTimeoutSeconds, 51200, JsonMapper.builder().build());
    }

    private static JDoodleCodeExecutionProvider provider() {
        return provider(FakeJDoodleServer.CLIENT_ID, FakeJDoodleServer.CLIENT_SECRET, 5);
    }

    private static ExecutionRequest request(CodeLanguage language, String source, String stdin) {
        return new ExecutionRequest(language, source, stdin, LIMITS);
    }

    @Test
    void successfulRunsInAllFourLanguagesWithPinnedVersionsAndStdin() {
        Map<CodeLanguage, String[]> expected = Map.of(
            CodeLanguage.PYTHON, new String[] { "python3", "5" },
            CodeLanguage.JAVA, new String[] { "java", "5" },
            CodeLanguage.JAVASCRIPT, new String[] { "nodejs", "5" },
            CodeLanguage.CPP, new String[] { "cpp17", "3" });
        for (CodeLanguage language : CodeLanguage.values()) {
            server.reset();
            ExecutionResult result = provider().execute(request(language, "program", "5 9\n"));
            assertThat(result.status()).as(language.name()).isEqualTo(ExecutionStatus.ACCEPTED);
            assertThat(result.stdout()).isEqualTo("echo:5 9\n\n"); // the fake echoes stdin plus a newline
            assertThat(result.executionTimeMs()).isEqualTo(120L);
            assertThat(result.memoryKb()).isEqualTo(10240L);
            JsonNode body = server.executeBodies.getFirst();
            assertThat(body.path("language").asString()).isEqualTo(expected.get(language)[0]);
            assertThat(body.path("versionIndex").asString()).isEqualTo(expected.get(language)[1]);
            assertThat(body.path("stdin").asString()).isEqualTo("5 9\n");
            assertThat(body.path("script").asString()).isEqualTo("program");
        }
        // Credentials only travel in the JSON body to JDoodle, never in headers.
        assertThat(server.headers).noneMatch(h -> h.values().stream().anyMatch(v -> v.contains(FakeJDoodleServer.CLIENT_SECRET)));
    }

    @Test
    void javaScriptsDropPackageAndPublicTopLevelTypes() {
        provider().execute(request(CodeLanguage.JAVA, "package a.b;\nimport java.util.*;\n\npublic class Main {\n    public static void main(String[] a) {}\n}\n", ""));
        String script = server.executeBodies.getFirst().path("script").asString();
        assertThat(script).doesNotContain("package a.b;").contains("\nclass Main {").contains("public static void main");
    }

    @Test
    void compilationErrorsAreRecognisedPerLanguage() {
        for (CodeLanguage language : CodeLanguage.values()) {
            ExecutionResult result = provider().execute(request(language, "COMPILE_ERROR", ""));
            assertThat(result.status()).as(language.name()).isEqualTo(ExecutionStatus.COMPILATION_ERROR);
            assertThat(result.compileOutput()).as(language.name()).isNotBlank();
            assertThat(result.stdout()).isEmpty();
        }
    }

    @Test
    void runtimeErrorsSplitStdoutFromTheErrorText() {
        for (CodeLanguage language : CodeLanguage.values()) {
            ExecutionResult result = provider().execute(request(language, "RUNTIME_ERROR", ""));
            assertThat(result.status()).as(language.name()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
            assertThat(result.stdout()).as(language.name()).isEqualTo("before crash\n");
            assertThat(result.stderr()).as(language.name()).isNotBlank().doesNotContain("before crash");
        }
    }

    @Test
    void timeoutAndOptionalFlags() {
        ExecutionResult timeout = provider().execute(request(CodeLanguage.PYTHON, "INFINITE", ""));
        assertThat(timeout.status()).isEqualTo(ExecutionStatus.TIME_LIMIT_EXCEEDED);
        assertThat(timeout.stdout()).startsWith("partial").doesNotContain("JDoodle");
        server.withFlags = true;
        assertThat(provider().execute(request(CodeLanguage.JAVA, "COMPILE_ERROR", "")).status()).isEqualTo(ExecutionStatus.COMPILATION_ERROR);
        assertThat(provider().execute(request(CodeLanguage.JAVA, "fine", "x")).status()).isEqualTo(ExecutionStatus.ACCEPTED);
    }

    @Test
    void http200DoesNotMeanTheProgramSucceeded() {
        // Same HTTP 200 + statusCode 200 envelope, three different program outcomes.
        assertThat(provider().execute(request(CodeLanguage.PYTHON, "ok", "")).status()).isEqualTo(ExecutionStatus.ACCEPTED);
        assertThat(provider().execute(request(CodeLanguage.PYTHON, "COMPILE_ERROR", "")).status()).isEqualTo(ExecutionStatus.COMPILATION_ERROR);
        assertThat(provider().execute(request(CodeLanguage.PYTHON, "RUNTIME_ERROR", "")).status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
    }

    @Test
    void authFailureIsAConfigurationProblem() {
        assertThatThrownBy(() -> provider("wrong-id", "wrong-secret", 5).execute(request(CodeLanguage.JAVA, "x", "")))
            .isInstanceOf(ExecutionProviderException.class)
            .hasMessage("Code execution is temporarily unavailable.")
            .satisfies(ex -> assertThat(((ExecutionProviderException) ex).reason()).isEqualTo(ExecutionProviderException.Reason.UNAVAILABLE));
        assertThat(logs.list).anyMatch(event -> event.getFormattedMessage().contains("JDoodle authentication failed"));
    }

    @Test
    void creditLimitIsReportedAndNeverRetried() {
        server.forcedHttpStatus = 429;
        assertThatThrownBy(() -> provider().execute(request(CodeLanguage.JAVA, "x", "")))
            .isInstanceOf(ExecutionProviderException.class)
            .hasMessage("Code execution provider daily limit reached. Please try again later.")
            .satisfies(ex -> assertThat(((ExecutionProviderException) ex).reason()).isEqualTo(ExecutionProviderException.Reason.LIMIT_REACHED));
        assertThat(server.executeBodies).hasSize(1);
        server.reset();
        server.errorInBodyStatus = 429; // error reported inside a 200 body
        assertThatThrownBy(() -> provider().execute(request(CodeLanguage.JAVA, "x", "")))
            .satisfies(ex -> assertThat(((ExecutionProviderException) ex).reason()).isEqualTo(ExecutionProviderException.Reason.LIMIT_REACHED));
    }

    @Test
    void serverErrorsAndNetworkProblems() {
        server.forcedHttpStatus = 503;
        assertThatThrownBy(() -> provider().execute(request(CodeLanguage.JAVA, "x", "")))
            .satisfies(ex -> assertThat(((ExecutionProviderException) ex).reason()).isEqualTo(ExecutionProviderException.Reason.UNAVAILABLE));
        server.reset();
        server.delayMs = 2500; // longer than the 1 s read timeout
        long started = System.nanoTime();
        assertThatThrownBy(() -> provider(FakeJDoodleServer.CLIENT_ID, FakeJDoodleServer.CLIENT_SECRET, 1).execute(request(CodeLanguage.JAVA, "x", "")))
            .satisfies(ex -> assertThat(((ExecutionProviderException) ex).reason()).isEqualTo(ExecutionProviderException.Reason.TIMEOUT));
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2400);
        server.reset();
        JDoodleCodeExecutionProvider unreachable = new JDoodleCodeExecutionProvider("http://127.0.0.1:1", FakeJDoodleServer.CLIENT_ID,
            FakeJDoodleServer.CLIENT_SECRET, "", 1, 1, 51200, JsonMapper.builder().build());
        assertThatThrownBy(() -> unreachable.execute(request(CodeLanguage.JAVA, "x", "")))
            .satisfies(ex -> assertThat(((ExecutionProviderException) ex).reason()).isEqualTo(ExecutionProviderException.Reason.UNAVAILABLE));
    }

    @Test
    void missingCredentialsMeansNotConfiguredAndNoHttpCall() {
        for (JDoodleCodeExecutionProvider unconfigured : new JDoodleCodeExecutionProvider[] { provider("", "", 5), provider("id-only", "", 5), provider("", "secret-only", 5) }) {
            assertThat(unconfigured.isConfigured()).isFalse();
            assertThatThrownBy(() -> unconfigured.execute(request(CodeLanguage.JAVA, "x", ""))).isInstanceOf(ExecutionProviderException.class);
        }
        assertThat(server.executeBodies).isEmpty();
    }

    @Test
    void creditsSpentDiagnostics() {
        provider().execute(request(CodeLanguage.PYTHON, "x", ""));
        assertThat(provider().creditsSpent()).contains(1);
        assertThat(provider("", "", 5).creditsSpent()).isEmpty();
        assertThat(provider().prefersCombinedSubmission()).isTrue();
    }

    @Test
    void languageVersionOverrides() {
        JDoodleLanguages languages = new JDoodleLanguages("JAVA=java:4, PYTHON=python3:x, BAD");
        assertThat(languages.targetFor(CodeLanguage.JAVA).versionIndex()).isEqualTo("4");
        assertThat(languages.targetFor(CodeLanguage.PYTHON).versionIndex()).isEqualTo("5"); // invalid override ignored
        assertThat(languages.targetFor(CodeLanguage.CPP).language()).isEqualTo("cpp17");
    }
}
