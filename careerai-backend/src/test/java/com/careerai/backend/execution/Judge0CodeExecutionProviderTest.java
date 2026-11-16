package com.careerai.backend.execution;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Judge0 provider against a local fake Judge0 HTTP server (no real Judge0, no quota):
 * async token + polling, batches, limits, base64, auth headers, language discovery, errors.
 */
class Judge0CodeExecutionProviderTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final ExecutionLimits LIMITS = new ExecutionLimits(2.5, 5, 256000, 60, 1024);

    private HttpServer server;
    private String baseUrl;
    private final List<JsonNode> createdBodies = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> headers = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> polls = new ConcurrentHashMap<>();
    /** token → final Judge0 response (status id + base64 fields). */
    private final Map<String, String> finals = new ConcurrentHashMap<>();
    private final AtomicInteger tokenCounter = new AtomicInteger();
    private volatile int pollsBeforeDone = 2;
    private volatile int forcedStatus = 0;
    private volatile String outcome = "accepted";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> h = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.getFirst()));
        headers.add(h);
        if (forcedStatus != 0) { send(exchange, forcedStatus, "{\"error\":\"nope\"}"); return; }
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        String method = exchange.getRequestMethod();
        if (path.equals("/languages")) {
            send(exchange, 200, "[{\"id\":62,\"name\":\"Java (OpenJDK 13.0.1)\"},{\"id\":91,\"name\":\"Java (JDK 17.0.6)\"},{\"id\":63,\"name\":\"JavaScript (Node.js 12.14.0)\"},"
                + "{\"id\":71,\"name\":\"Python (3.8.1)\"},{\"id\":70,\"name\":\"Python (2.7.17)\"},{\"id\":54,\"name\":\"C++ (GCC 9.2.0)\"}]");
            return;
        }
        if (method.equals("POST") && path.equals("/submissions")) {
            JsonNode body = JSON.readTree(exchange.getRequestBody().readAllBytes());
            createdBodies.add(body);
            String token = register(body);
            send(exchange, 201, "{\"token\":\"" + token + "\"}");
            return;
        }
        if (method.equals("POST") && path.equals("/submissions/batch")) {
            JsonNode body = JSON.readTree(exchange.getRequestBody().readAllBytes());
            List<String> out = new ArrayList<>();
            for (JsonNode item : body.path("submissions")) { createdBodies.add(item); out.add("{\"token\":\"" + register(item) + "\"}"); }
            send(exchange, 201, "[" + String.join(",", out) + "]");
            return;
        }
        if (method.equals("GET") && path.equals("/submissions/batch")) {
            String tokens = query.replaceAll(".*tokens=([^&]*).*", "$1");
            List<String> items = new ArrayList<>();
            for (String token : tokens.split(",")) items.add(state(token));
            send(exchange, 200, "{\"submissions\":[" + String.join(",", items) + "]}");
            return;
        }
        if (method.equals("GET") && path.startsWith("/submissions/")) {
            send(exchange, 200, state(path.substring("/submissions/".length())));
            return;
        }
        send(exchange, 404, "{}");
    }

    private String register(JsonNode body) {
        String token = "tok-" + tokenCounter.incrementAndGet();
        String stdin = new String(Base64.getDecoder().decode(body.path("stdin").asString("")), StandardCharsets.UTF_8);
        String response = switch (outcome) {
            case "compile" -> final_(6, "", "", "Main.java:3: error: ';' expected", "");
            case "tle" -> final_(5, "", "", "", "Time limit exceeded");
            case "runtime" -> final_(11, "", "Exception in thread \"main\" java.lang.ArithmeticException: / by zero", "", "Exited with error status 1");
            case "oom" -> final_(11, "", "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space", "", "");
            default -> final_(3, "echo:" + stdin + "\n", "", "", "");
        };
        finals.put(token, "{\"token\":\"" + token + "\"," + response);
        polls.put(token, new AtomicInteger());
        return token;
    }

    private static String final_(int status, String stdout, String stderr, String compile, String message) {
        return "\"status\":{\"id\":" + status + ",\"description\":\"x\"},\"stdout\":" + b64(stdout) + ",\"stderr\":" + b64(stderr)
            + ",\"compile_output\":" + b64(compile) + ",\"message\":" + b64(message) + ",\"time\":\"0.042\",\"memory\":10240}";
    }

    /** Like Judge0: MIME base64 whose line breaks are JSON-escaped (\n) inside the string. */
    private static String b64(String text) {
        return text.isEmpty() ? "null" : "\"" + Base64.getMimeEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)).replace("\r\n", "\\n") + "\"";
    }

    private String state(String token) {
        int count = polls.get(token).incrementAndGet();
        if (count <= pollsBeforeDone) {
            return "{\"token\":\"" + token + "\",\"status\":{\"id\":" + (count == 1 ? 1 : 2) + "},\"stdout\":null,\"stderr\":null,\"compile_output\":null,\"message\":null,\"time\":null,\"memory\":null}";
        }
        return finals.get(token);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private Judge0CodeExecutionProvider provider(String apiKey, String apiHost, String languageIds, long maxWaitMs) {
        return new Judge0CodeExecutionProvider(baseUrl, apiKey, apiHost, "X-Auth-Token", languageIds, 100, maxWaitMs, 51200, 5, JSON);
    }

    @Test
    void asyncSubmitThenPollUntilAccepted() {
        ExecutionResult result = provider("judge0-token", "", "", 5000)
            .execute(new ExecutionRequest(CodeLanguage.JAVA, "class Main {}", "5 9", LIMITS));
        assertThat(result.status()).isEqualTo(ExecutionStatus.ACCEPTED);
        assertThat(result.stdout()).isEqualTo("echo:5 9\n");
        assertThat(result.executionTimeMs()).isEqualTo(42L);
        assertThat(result.memoryKb()).isEqualTo(10240L);
        assertThat(polls.get("tok-1").get()).isEqualTo(3); // queued, processing, done
    }

    @Test
    void sendsServerLimitsNetworkOffBase64AndDiscoveredLanguage() {
        provider("", "", "", 5000).execute(new ExecutionRequest(CodeLanguage.PYTHON, "print('hi')", "x", LIMITS));
        JsonNode body = createdBodies.getFirst();
        assertThat(body.path("language_id").asInt()).isEqualTo(71); // Python 3, not Python 2
        assertThat(new String(Base64.getDecoder().decode(body.path("source_code").asString()), StandardCharsets.UTF_8)).isEqualTo("print('hi')");
        assertThat(body.path("enable_network").asBoolean(true)).isFalse();
        assertThat(body.path("cpu_time_limit").asDouble()).isEqualTo(2.5);
        assertThat(body.path("wall_time_limit").asDouble()).isEqualTo(5.0);
        assertThat(body.path("memory_limit").asInt()).isEqualTo(256000);
        assertThat(body.path("max_processes_and_or_threads").asInt()).isEqualTo(60);
        provider("", "", "", 5000).execute(new ExecutionRequest(CodeLanguage.JAVA, "class Main {}", "", LIMITS));
        assertThat(createdBodies.get(1).path("language_id").asInt()).isEqualTo(91); // newest Java
    }

    @Test
    void configuredLanguageIdsOverrideDiscovery() {
        provider("", "", "JAVA=62,CPP=54", 5000).execute(new ExecutionRequest(CodeLanguage.JAVA, "class Main {}", "", LIMITS));
        assertThat(createdBodies.getFirst().path("language_id").asInt()).isEqualTo(62);
        assertThat(headers.stream().noneMatch(h -> h.containsKey("x-auth-token"))).isTrue();
    }

    @Test
    void authHeadersSelfHostedVsRapidApi() {
        provider("judge0-token", "", "JAVA=62", 5000).execute(new ExecutionRequest(CodeLanguage.JAVA, "x", "", LIMITS));
        assertThat(headers).allMatch(h -> "judge0-token".equals(h.get("x-auth-token")) && !h.containsKey("x-rapidapi-key"));
        headers.clear();
        provider("rapid-key", "judge0-ce.p.rapidapi.com", "JAVA=62", 5000).execute(new ExecutionRequest(CodeLanguage.JAVA, "x", "", LIMITS));
        assertThat(headers).allMatch(h -> "rapid-key".equals(h.get("x-rapidapi-key")) && "judge0-ce.p.rapidapi.com".equals(h.get("x-rapidapi-host")));
    }

    @Test
    void batchKeepsOrder() {
        List<ExecutionResult> results = provider("", "", "PYTHON=71", 5000).executeBatch(List.of(
            new ExecutionRequest(CodeLanguage.PYTHON, "x", "a", LIMITS),
            new ExecutionRequest(CodeLanguage.PYTHON, "x", "b", LIMITS),
            new ExecutionRequest(CodeLanguage.PYTHON, "x", "c", LIMITS)));
        assertThat(results).extracting(ExecutionResult::stdout).containsExactly("echo:a\n", "echo:b\n", "echo:c\n");
    }

    @Test
    void statusMapping() {
        Judge0CodeExecutionProvider provider = provider("", "", "JAVA=62", 5000);
        outcome = "compile";
        ExecutionResult compile = provider.execute(new ExecutionRequest(CodeLanguage.JAVA, "x", "", LIMITS));
        assertThat(compile.status()).isEqualTo(ExecutionStatus.COMPILATION_ERROR);
        assertThat(compile.compileOutput()).contains("';' expected");
        outcome = "tle";
        assertThat(provider.execute(new ExecutionRequest(CodeLanguage.JAVA, "x", "", LIMITS)).status()).isEqualTo(ExecutionStatus.TIME_LIMIT_EXCEEDED);
        outcome = "runtime";
        ExecutionResult runtime = provider.execute(new ExecutionRequest(CodeLanguage.JAVA, "x", "", LIMITS));
        assertThat(runtime.status()).isEqualTo(ExecutionStatus.RUNTIME_ERROR);
        assertThat(runtime.stderr()).contains("ArithmeticException");
        outcome = "oom";
        assertThat(provider.execute(new ExecutionRequest(CodeLanguage.JAVA, "x", "", LIMITS)).status()).isEqualTo(ExecutionStatus.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    void pollingIsBoundedAndTimesOut() {
        pollsBeforeDone = Integer.MAX_VALUE; // never finishes
        long started = System.nanoTime();
        assertThatThrownBy(() -> provider("", "", "JAVA=62", 600).execute(new ExecutionRequest(CodeLanguage.JAVA, "x", "", LIMITS)))
            .isInstanceOf(ExecutionProviderException.class)
            .satisfies(ex -> assertThat(((ExecutionProviderException) ex).reason()).isEqualTo(ExecutionProviderException.Reason.TIMEOUT));
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(3000);
    }

    @Test
    void providerErrorsBecomeUnavailableWithoutLeakingDetails() {
        forcedStatus = 401;
        assertThatThrownBy(() -> provider("bad-key", "", "JAVA=62", 1000).execute(new ExecutionRequest(CodeLanguage.JAVA, "x", "", LIMITS)))
            .isInstanceOf(ExecutionProviderException.class)
            .hasMessage("Code execution is temporarily unavailable.");
        forcedStatus = 429;
        assertThatThrownBy(() -> provider("k", "", "JAVA=62", 1000).execute(new ExecutionRequest(CodeLanguage.JAVA, "x", "", LIMITS)))
            .isInstanceOf(ExecutionProviderException.class);
    }

    @Test
    void notConfiguredAndUnreachable() {
        Judge0CodeExecutionProvider unconfigured = new Judge0CodeExecutionProvider("", "", "", "X-Auth-Token", "", 100, 1000, 51200, 2, JSON);
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.execute(new ExecutionRequest(CodeLanguage.JAVA, "x", "", LIMITS)))
            .isInstanceOf(ExecutionProviderException.class);
        server.stop(0);
        assertThatThrownBy(() -> provider("", "", "JAVA=62", 1000).execute(new ExecutionRequest(CodeLanguage.JAVA, "x", "", LIMITS)))
            .isInstanceOf(ExecutionProviderException.class)
            .satisfies(ex -> assertThat(((ExecutionProviderException) ex).reason()).isEqualTo(ExecutionProviderException.Reason.UNAVAILABLE));
    }
}
