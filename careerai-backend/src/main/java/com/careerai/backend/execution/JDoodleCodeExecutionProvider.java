package com.careerai.backend.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDoodle Compiler API (REST) as the sandboxed execution provider — the default provider.
 *
 *   POST {JDOODLE_BASE_URL}/v1/execute
 *   { clientId, clientSecret, script, stdin, language, versionIndex }
 *   → { output, statusCode, memory (KB), cpuTime (s) }   (error + statusCode on a failed request)
 *
 * HTTP 200 only means JDoodle processed the request: compile errors, runtime errors and timeouts
 * also arrive as 200 with the message inside "output". The program outcome is therefore classified
 * from the output (plus the optional isCompiled / isExecutionSuccess flags when present), never
 * from the HTTP status. 401 = server configuration problem, 429 = daily credits used up (never
 * retried). The client id/secret travel only in the request body to JDoodle and are never logged.
 *
 * Each call costs one JDoodle credit, so hidden tests are combined into ONE program per submission
 * (prefersCombinedSubmission → HiddenTestHarness) instead of one call per test.
 */
@Component
@ConditionalOnProperty(name = "app.execution.provider", havingValue = "jdoodle", matchIfMissing = true)
public class JDoodleCodeExecutionProvider implements CodeExecutionProvider {

    private static final Logger log = LoggerFactory.getLogger(JDoodleCodeExecutionProvider.class);

    private static final Pattern TIMEOUT = Pattern.compile("(?i)JDoodle\\s*-\\s*Timeout");
    private static final Pattern MEMORY = Pattern.compile("OutOfMemoryError|MemoryError|std::bad_alloc|heap out of memory|JavaScript heap");
    private static final Map<CodeLanguage, Pattern> COMPILE_ERRORS = Map.of(
        CodeLanguage.JAVA, Pattern.compile("(?m)^\\S*\\.java:\\d+: error:"),
        CodeLanguage.CPP, Pattern.compile("(?m)^\\S*\\.(?:cpp|cc|cxx|h|hpp)(?::\\d+){1,2}: (?:fatal )?error:"),
        CodeLanguage.PYTHON, Pattern.compile("(?m)^(?:SyntaxError|IndentationError|TabError): "),
        CodeLanguage.JAVASCRIPT, Pattern.compile("(?m)^SyntaxError: ")
    );
    private static final Map<CodeLanguage, Pattern> RUNTIME_ERRORS = Map.of(
        CodeLanguage.JAVA, Pattern.compile("(?m)^(?:Exception in thread \"[^\"]*\" |Error: Could not find or load main class|Error: Main method not found)"),
        CodeLanguage.CPP, Pattern.compile("(?m)(?:Segmentation fault|terminate called|core dumped|^Aborted|Floating point exception|double free|stack smashing)"),
        CodeLanguage.PYTHON, Pattern.compile("(?m)^Traceback \\(most recent call last\\):"),
        CodeLanguage.JAVASCRIPT, Pattern.compile("(?m)^(?:\\S+\\.js:\\d+\\s*$|(?:[A-Z]\\w*)?Error(?: \\[\\w+])?: .*\\n\\s+at )")
    );

    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final JDoodleLanguages languages;
    private final int maxOutputChars;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public JDoodleCodeExecutionProvider(
        @Value("${app.execution.jdoodle.base-url:https://api.jdoodle.com}") String baseUrl,
        @Value("${app.execution.jdoodle.client-id:}") String clientId,
        @Value("${app.execution.jdoodle.client-secret:}") String clientSecret,
        @Value("${app.execution.jdoodle.language-versions:}") String languageVersions,
        @Value("${app.execution.jdoodle.connect-timeout-seconds:5}") int connectTimeoutSeconds,
        @Value("${app.execution.jdoodle.read-timeout-seconds:15}") int readTimeoutSeconds,
        @Value("${app.execution.max-output-chars:51200}") int maxOutputChars,
        ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.jdoodle.com" : baseUrl.trim().replaceAll("/+$", "");
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.languages = new JDoodleLanguages(languageVersions);
        this.maxOutputChars = Math.max(1024, maxOutputChars);
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds))).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(1, readTimeoutSeconds)));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();

        if (!isConfigured()) {
            log.warn("JDOODLE_CLIENT_ID / JDOODLE_CLIENT_SECRET are not set: code execution endpoints answer 503; everything else works normally.");
        } else {
            log.info("JDoodle execution provider configured ({}/v1/execute; languages {}).", this.baseUrl, describeLanguages());
        }
    }

    @Override
    public String name() {
        return "jdoodle";
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isEmpty() && !clientSecret.isEmpty();
    }

    /** One JDoodle call = one credit: submissions run all hidden tests inside one program. */
    @Override
    public boolean prefersCombinedSubmission() {
        return true;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        if (!isConfigured()) throw ExecutionProviderException.unavailable();
        JDoodleLanguages.Target target = languages.targetFor(request.language());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", clientId);
        body.put("clientSecret", clientSecret);
        body.put("script", prepareScript(request.language(), request.sourceCode()));
        // Over REST all input must be supplied up front (a program waiting for input times out).
        body.put("stdin", request.stdin() == null ? "" : request.stdin());
        body.put("language", target.language());
        body.put("versionIndex", target.versionIndex());

        JsonNode response = post("/v1/execute", body, "execute");
        if (response.hasNonNull("error")) {
            throw requestFailure(response.path("statusCode").asInt(0), response.path("error").asString(""), "execute");
        }
        if (!response.has("output")) {
            log.warn("JDOODLE_BAD_RESPONSE: execute response had no output.");
            throw ExecutionProviderException.unavailable();
        }
        return classify(request.language(), response.path("output").asString(""),
            flag(response, "isCompiled"), flag(response, "isExecutionSuccess"),
            parseTimeMs(response.path("cpuTime")), parseLong(response.path("memory")));
    }

    /** Fallback only (submissions use the combined harness): one call per request, stops at the first failure. */
    @Override
    public List<ExecutionResult> executeBatch(List<ExecutionRequest> requests) {
        List<ExecutionResult> results = new ArrayList<>();
        for (ExecutionRequest request : requests) results.add(execute(request));
        return results;
    }

    /** Credits used today (POST /v1/credit-spent, free of charge). Diagnostics only; empty when unknown. */
    public Optional<Integer> creditsSpent() {
        if (!isConfigured()) return Optional.empty();
        try {
            JsonNode response = post("/v1/credit-spent", Map.of("clientId", clientId, "clientSecret", clientSecret), "credit-spent");
            return response.path("used").isNumber() ? Optional.of(response.path("used").asInt()) : Optional.empty();
        } catch (ExecutionProviderException ex) {
            return Optional.empty();
        }
    }

    /**
     * Program outcome from JDoodle's combined output. Order: timeout, compile error, runtime error
     * (memory), otherwise accepted. Text before a runtime error stays stdout; the rest is stderr.
     */
    ExecutionResult classify(CodeLanguage language, String output, Boolean isCompiled, Boolean isExecutionSuccess, Long timeMs, Long memoryKb) {
        String text = output == null ? "" : output;
        if (TIMEOUT.matcher(text).find()) {
            Matcher matcher = TIMEOUT.matcher(text);
            int at = matcher.find() ? lineStart(text, matcher.start()) : text.length();
            return new ExecutionResult(ExecutionStatus.TIME_LIMIT_EXCEEDED, truncate(text.substring(0, at)), "", "", timeMs, memoryKb, "Time limit exceeded");
        }
        if (Boolean.FALSE.equals(isCompiled) || COMPILE_ERRORS.get(language).matcher(text).find()) {
            return new ExecutionResult(ExecutionStatus.COMPILATION_ERROR, "", "", truncate(text), timeMs, memoryKb, "");
        }
        Matcher runtime = RUNTIME_ERRORS.get(language).matcher(text);
        boolean runtimeFound = runtime.find();
        if (runtimeFound || Boolean.FALSE.equals(isExecutionSuccess)) {
            int at = runtimeFound ? lineStart(text, runtime.start()) : 0;
            String stderr = text.substring(at);
            ExecutionStatus status = MEMORY.matcher(stderr).find() ? ExecutionStatus.MEMORY_LIMIT_EXCEEDED : ExecutionStatus.RUNTIME_ERROR;
            return new ExecutionResult(status, truncate(text.substring(0, at)), truncate(stderr), "", timeMs, memoryKb, "");
        }
        return new ExecutionResult(ExecutionStatus.ACCEPTED, truncate(text), "", "", timeMs, memoryKb, "");
    }

    /**
     * Java: drop any package line and the "public" modifier of top-level types so the program
     * compiles whatever file name JDoodle uses (a public class must match its file name).
     * The combined harness keeps its own public Main class.
     */
    static String prepareScript(CodeLanguage language, String source) {
        if (language != CodeLanguage.JAVA || source == null) return source;
        String script = source.replaceAll("(?m)^\\s*package\\s+[\\w.]+\\s*;\\s*$", "");
        if (script.contains("class CareerAiCandidate")) return script; // harness: Main stays public
        return script.replaceAll("(?m)^public\\s+((?:final\\s+|abstract\\s+)*(?:class|interface|enum|record)\\s)", "$1");
    }

    private JsonNode post(String path, Object body, String operation) {
        try {
            String response = restClient.post()
                .uri(baseUrl + path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (RestClientResponseException ex) {
            throw requestFailure(ex.getStatusCode().value(), ex.getResponseBodyAsString(), operation);
        } catch (ResourceAccessException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof HttpTimeoutException && !(cause instanceof HttpConnectTimeoutException)) {
                log.warn("JDOODLE_TIMEOUT: {} got no response in time.", operation);
                throw ExecutionProviderException.timeout();
            }
            log.warn("JDOODLE_UNREACHABLE: {} failed: {}", operation, cause == null ? ex.getClass().getSimpleName() : cause.getClass().getSimpleName());
            throw ExecutionProviderException.unavailable();
        } catch (RestClientException ex) {
            log.warn("JDOODLE_UNREACHABLE: {} failed: {}", operation, ex.getClass().getSimpleName());
            throw ExecutionProviderException.unavailable();
        } catch (ExecutionProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("JDOODLE_BAD_RESPONSE: {} returned unreadable data: {}", operation, ex.getClass().getSimpleName());
            throw ExecutionProviderException.unavailable();
        }
    }

    private ExecutionProviderException requestFailure(int status, String body, String operation) {
        return switch (status) {
            case 401, 403 -> {
                log.warn("JDoodle authentication failed ({}): check JDOODLE_CLIENT_ID / JDOODLE_CLIENT_SECRET.", operation);
                yield ExecutionProviderException.unavailable();
            }
            case 429 -> {
                log.warn("JDOODLE_LIMIT_REACHED: daily JDoodle credits are used up ({}).", operation);
                yield ExecutionProviderException.limitReached();
            }
            default -> {
                log.warn("JDOODLE_HTTP_ERROR: {} failed with status {}: {}", operation, status, safe(body));
                yield ExecutionProviderException.unavailable();
            }
        };
    }

    private static Boolean flag(JsonNode response, String field) {
        JsonNode node = response.path(field);
        return node.isBoolean() ? node.asBoolean() : null;
    }

    private static int lineStart(String text, int index) {
        int newline = text.lastIndexOf('\n', Math.max(0, index - 1));
        return index == 0 ? 0 : newline < 0 ? 0 : newline + 1;
    }

    private static Long parseTimeMs(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            return Math.round(Double.parseDouble(node.asString("").trim()) * 1000);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long parseLong(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        try {
            return Long.parseLong(node.asString("").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > maxOutputChars ? text.substring(0, maxOutputChars) + "\n… (output truncated)" : text;
    }

    /** Provider text for logs: one line, short, never containing the credentials. */
    private String safe(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ");
        if (!clientId.isEmpty()) value = value.replace(clientId, "[redacted]");
        if (!clientSecret.isEmpty()) value = value.replace(clientSecret, "[redacted]");
        return value.length() > 300 ? value.substring(0, 300) + "..." : value;
    }

    private String describeLanguages() {
        List<String> parts = new ArrayList<>();
        languages.all().forEach((language, target) -> parts.add(language + "=" + target.language() + ":" + target.versionIndex()));
        return String.join(", ", parts);
    }
}
