package com.careerai.backend.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Judge0 CE (self-hosted, hosted, or via RapidAPI) as the sandboxed execution provider.
 *
 * Flow (no wait=true): POST /submissions(?batch) → token(s) → poll GET until every submission is
 * terminal, at most app.execution.max-wait-ms. Source, stdin and outputs travel base64-encoded.
 *
 * Every submission carries the server's limits (CPU/wall time, memory, process/thread count,
 * file size) and enable_network=false. Judge0 honours enable_network=false only when the instance
 * allows clients to set it (allow_enable_network); a self-hosted instance should also be
 * configured with networking disabled. Judge0 runs code inside its isolate sandbox, never here.
 *
 * Authentication: RapidAPI (X-RapidAPI-Key + X-RapidAPI-Host) when JUDGE0_API_HOST is set,
 * otherwise JUDGE0_API_KEY in the JUDGE0_AUTH_HEADER header (Judge0 default X-Auth-Token).
 * The key is never logged or returned.
 */
@Component
@ConditionalOnProperty(name = "app.execution.provider", havingValue = "judge0")
public class Judge0CodeExecutionProvider implements CodeExecutionProvider {

    private static final Logger log = LoggerFactory.getLogger(Judge0CodeExecutionProvider.class);
    private static final String FIELDS = "token,stdout,stderr,compile_output,message,status,time,memory";

    /** Name prefixes used to discover language ids from GET /languages (newest id wins). */
    private static final Map<CodeLanguage, Predicate<String>> LANGUAGE_NAMES = Map.of(
        CodeLanguage.JAVA, name -> name.startsWith("Java ("),
        CodeLanguage.PYTHON, name -> name.startsWith("Python (3"),
        CodeLanguage.CPP, name -> name.startsWith("C++ (GCC"),
        CodeLanguage.JAVASCRIPT, name -> name.startsWith("JavaScript (Node")
    );

    private final String baseUrl;
    private final String apiKey;
    private final String apiHost;
    private final String authHeader;
    private final Map<CodeLanguage, Integer> configuredIds;
    private final long pollIntervalMs;
    private final long maxWaitMs;
    private final int maxOutputChars;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile Map<CodeLanguage, Integer> discoveredIds;

    public Judge0CodeExecutionProvider(
        @Value("${app.execution.judge0.base-url:}") String baseUrl,
        @Value("${app.execution.judge0.api-key:}") String apiKey,
        @Value("${app.execution.judge0.api-host:}") String apiHost,
        @Value("${app.execution.judge0.auth-header:X-Auth-Token}") String authHeader,
        @Value("${app.execution.judge0.language-ids:}") String languageIds,
        @Value("${app.execution.poll-interval-ms:400}") long pollIntervalMs,
        @Value("${app.execution.max-wait-ms:10000}") long maxWaitMs,
        @Value("${app.execution.max-output-chars:51200}") int maxOutputChars,
        @Value("${app.execution.judge0.timeout-seconds:10}") int timeoutSeconds,
        ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiHost = apiHost == null ? "" : apiHost.trim();
        this.authHeader = authHeader == null || authHeader.isBlank() ? "X-Auth-Token" : authHeader.trim();
        this.configuredIds = parseLanguageIds(languageIds);
        this.pollIntervalMs = Math.max(100, pollIntervalMs);
        this.maxWaitMs = Math.max(this.pollIntervalMs, maxWaitMs);
        this.maxOutputChars = Math.max(1024, maxOutputChars);
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 10))).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();

        if (!isConfigured()) {
            log.warn("JUDGE0_BASE_URL is not set: code execution endpoints answer 503; everything else works normally.");
        } else {
            log.info("Judge0 execution provider configured ({} , auth: {}).", this.baseUrl,
                !this.apiHost.isEmpty() ? "RapidAPI headers" : !this.apiKey.isEmpty() ? this.authHeader + " header" : "none");
        }
    }

    @Override
    public String name() {
        return "judge0";
    }

    @Override
    public boolean isConfigured() {
        return !baseUrl.isEmpty();
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        requireConfigured();
        Map<String, Object> body = submission(request);
        JsonNode created = send(() -> restClient.post()
            .uri(baseUrl + "/submissions?base64_encoded=true&wait=false").headers(this::auth)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(body))
            .retrieve()
            .body(String.class), "create submission");
        String token = created.path("token").asString("");
        if (token.isBlank()) {
            log.warn("JUDGE0_BAD_RESPONSE: no token in create response.");
            throw ExecutionProviderException.unavailable();
        }
        return pollUntilDone(List.of(token)).getFirst();
    }

    @Override
    public List<ExecutionResult> executeBatch(List<ExecutionRequest> requests) {
        requireConfigured();
        if (requests.isEmpty()) return List.of();
        List<Map<String, Object>> submissions = new ArrayList<>();
        for (ExecutionRequest request : requests) submissions.add(submission(request));

        JsonNode created = send(() -> restClient.post()
            .uri(baseUrl + "/submissions/batch?base64_encoded=true").headers(this::auth)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(Map.of("submissions", submissions)))
            .retrieve()
            .body(String.class), "create batch");

        List<String> tokens = new ArrayList<>();
        for (JsonNode item : created) {
            String token = item.path("token").asString("");
            if (token.isBlank()) {
                log.warn("JUDGE0_BAD_RESPONSE: batch item rejected: {}", truncate(item.toString(), 300));
                throw ExecutionProviderException.unavailable();
            }
            tokens.add(token);
        }
        if (tokens.size() != requests.size()) {
            log.warn("JUDGE0_BAD_RESPONSE: {} tokens for {} submissions.", tokens.size(), requests.size());
            throw ExecutionProviderException.unavailable();
        }
        return pollUntilDone(tokens);
    }

    /** Polls the tokens until all are terminal; bounded by maxWaitMs. Results keep the token order. */
    private List<ExecutionResult> pollUntilDone(List<String> tokens) {
        long deadline = System.nanoTime() + maxWaitMs * 1_000_000L;
        while (true) {
            List<JsonNode> items = fetch(tokens);
            List<ExecutionResult> results = new ArrayList<>();
            boolean done = true;
            for (JsonNode item : items) {
                ExecutionResult result = toResult(item);
                done &= result.status().isTerminal();
                results.add(result);
            }
            if (done) return results;
            if (System.nanoTime() >= deadline) {
                log.warn("JUDGE0_POLL_TIMEOUT: {} submission(s) not finished after {} ms.", tokens.size(), maxWaitMs);
                throw ExecutionProviderException.timeout();
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ExecutionProviderException.timeout();
            }
        }
    }

    private List<JsonNode> fetch(List<String> tokens) {
        if (tokens.size() == 1) {
            JsonNode item = send(() -> restClient.get()
                .uri(baseUrl + "/submissions/" + tokens.getFirst() + "?base64_encoded=true&fields=" + FIELDS).headers(this::auth)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class), "get submission");
            return List.of(item);
        }
        JsonNode batch = send(() -> restClient.get()
            .uri(baseUrl + "/submissions/batch?tokens=" + String.join(",", tokens) + "&base64_encoded=true&fields=" + FIELDS).headers(this::auth)
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(String.class), "get batch");
        Map<String, JsonNode> byToken = new LinkedHashMap<>();
        for (JsonNode item : batch.path("submissions")) byToken.put(item.path("token").asString(""), item);
        List<JsonNode> ordered = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            JsonNode item = byToken.get(tokens.get(i));
            // Without tokens in the response, fall back to position.
            ordered.add(item != null ? item : batch.path("submissions").path(i));
        }
        return ordered;
    }

    private Map<String, Object> submission(ExecutionRequest request) {
        ExecutionLimits limits = request.limits();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("language_id", languageId(request.language()));
        body.put("source_code", encode(request.sourceCode()));
        body.put("stdin", encode(request.stdin() == null ? "" : request.stdin()));
        body.put("cpu_time_limit", limits.cpuTimeLimitSeconds());
        body.put("cpu_extra_time", 0.5);
        body.put("wall_time_limit", limits.wallTimeLimitSeconds());
        body.put("memory_limit", limits.memoryLimitKb());
        body.put("max_processes_and_or_threads", limits.maxProcessesAndThreads());
        body.put("max_file_size", limits.maxFileSizeKb());
        body.put("enable_network", false);
        return body;
    }

    ExecutionResult toResult(JsonNode item) {
        int statusId = item.path("status").path("id").asInt(0);
        String stdout = decode(item.path("stdout"));
        String stderr = decode(item.path("stderr"));
        String compileOutput = decode(item.path("compile_output"));
        String message = decode(item.path("message"));
        ExecutionStatus status = switch (statusId) {
            case 1 -> ExecutionStatus.QUEUED;
            case 2 -> ExecutionStatus.PROCESSING;
            case 3 -> ExecutionStatus.ACCEPTED;
            case 4 -> ExecutionStatus.WRONG_ANSWER;
            case 5 -> ExecutionStatus.TIME_LIMIT_EXCEEDED;
            case 6 -> ExecutionStatus.COMPILATION_ERROR;
            case 7, 8, 9, 10, 11, 12, 14 -> looksLikeMemoryLimit(stderr, message) ? ExecutionStatus.MEMORY_LIMIT_EXCEEDED : ExecutionStatus.RUNTIME_ERROR;
            case 13 -> ExecutionStatus.INTERNAL_ERROR;
            default -> ExecutionStatus.INTERNAL_ERROR;
        };
        return new ExecutionResult(status, truncate(stdout), truncate(stderr), truncate(compileOutput),
            parseTimeMs(item.path("time")), item.path("memory").isNumber() ? item.path("memory").asLong() : null,
            truncate(message, 500));
    }

    /** Judge0 has no separate memory status; recognise the usual out-of-memory signatures. */
    private static boolean looksLikeMemoryLimit(String stderr, String message) {
        String text = (stderr + " " + message).toLowerCase(Locale.ROOT);
        return text.contains("outofmemoryerror") || text.contains("memoryerror") || text.contains("bad_alloc")
            || text.contains("heap out of memory") || text.contains("memory limit");
    }

    private int languageId(CodeLanguage language) {
        Integer configured = configuredIds.get(language);
        if (configured != null) return configured;
        Map<CodeLanguage, Integer> ids = discoveredIds;
        if (ids == null) ids = discoverLanguages();
        Integer id = ids.get(language);
        if (id == null) {
            log.warn("JUDGE0_LANGUAGE_MISSING: no {} language on this Judge0 instance; set JUDGE0_LANGUAGE_IDS.", language);
            throw ExecutionProviderException.unavailable();
        }
        return id;
    }

    /** GET /languages once; keeps the newest (highest id) match per language. */
    private synchronized Map<CodeLanguage, Integer> discoverLanguages() {
        if (discoveredIds != null) return discoveredIds;
        JsonNode languages = send(() -> restClient.get().uri(baseUrl + "/languages").headers(this::auth)
            .accept(MediaType.APPLICATION_JSON).retrieve().body(String.class), "list languages");
        Map<CodeLanguage, Integer> ids = new EnumMap<>(CodeLanguage.class);
        for (JsonNode language : languages) {
            String name = language.path("name").asString("");
            int id = language.path("id").asInt(-1);
            LANGUAGE_NAMES.forEach((key, matches) -> {
                if (id > 0 && matches.test(name) && id > ids.getOrDefault(key, -1)) ids.put(key, id);
            });
        }
        log.info("Judge0 languages discovered: {}", ids);
        discoveredIds = ids;
        return ids;
    }

    private interface Call {
        String run();
    }

    private JsonNode send(Call call, String operation) {
        try {
            String response = call.run();
            return objectMapper.readTree(response == null ? "null" : response);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String category = switch (status) {
                case 401, 403 -> "JUDGE0_AUTH_FAILURE";
                case 429 -> "JUDGE0_RATE_LIMITED";
                case 422 -> "JUDGE0_REJECTED";
                case 503 -> "JUDGE0_QUEUE_FULL";
                default -> "JUDGE0_HTTP_ERROR";
            };
            log.warn("{}: {} failed with HTTP {}: {}", category, operation, status, safe(ex.getResponseBodyAsString()));
            throw ExecutionProviderException.unavailable();
        } catch (RestClientException ex) {
            log.warn("JUDGE0_UNREACHABLE: {} failed: {}", operation, ex.getClass().getSimpleName());
            throw ExecutionProviderException.unavailable();
        } catch (ExecutionProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("JUDGE0_BAD_RESPONSE: {} returned unreadable data: {}", operation, ex.getClass().getSimpleName());
            throw ExecutionProviderException.unavailable();
        }
    }

    private void auth(HttpHeaders headers) {
        if (!apiHost.isEmpty()) {
            headers.set("X-RapidAPI-Key", apiKey);
            headers.set("X-RapidAPI-Host", apiHost);
        } else if (!apiKey.isEmpty()) {
            headers.set(authHeader, apiKey);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) throw ExecutionProviderException.unavailable();
    }

    private static Map<CodeLanguage, Integer> parseLanguageIds(String value) {
        Map<CodeLanguage, Integer> ids = new EnumMap<>(CodeLanguage.class);
        if (value == null || value.isBlank()) return ids;
        for (String pair : value.split(",")) {
            String[] parts = pair.split("=");
            if (parts.length != 2) continue;
            CodeLanguage.parse(parts[0]).ifPresent(language -> {
                try {
                    ids.put(language, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException ignored) {
                    // skipped: invalid id
                }
            });
        }
        return ids;
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";
        String text = node.asString("");
        if (text.isEmpty()) return "";
        try {
            return new String(Base64.getMimeDecoder().decode(text), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return text;
        }
    }

    private static Long parseTimeMs(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            return Math.round(Double.parseDouble(node.asString("0")) * 1000);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String truncate(String text) {
        return truncate(text, maxOutputChars);
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "\n… (output truncated)" : text;
    }

    /** Provider text for logs: one line, short, never containing the key. */
    private String safe(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ");
        if (!apiKey.isEmpty()) value = value.replace(apiKey, "[redacted]");
        return truncate(value, 300);
    }
}
