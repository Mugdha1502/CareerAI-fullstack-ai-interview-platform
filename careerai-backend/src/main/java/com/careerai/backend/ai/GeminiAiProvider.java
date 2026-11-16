package com.careerai.backend.ai;

import com.careerai.backend.ai.AiProviderException.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Google Gemini via the REST generateContent API. All Gemini-specific HTTP, request
 * format, response parsing, timeouts and retries live here.
 *
 * The API key is sent in the x-goog-api-key header (never in the URL) and is never
 * logged or returned. Provider error bodies are not passed on to clients.
 */
@Component
public class GeminiAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiProvider.class);

    /** Retried: rate limiting and temporary server problems. Everything else fails at once. */
    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(429, 500, 502, 503, 504);
    private static final Set<String> BLOCKED_FINISH_REASONS = Set.of(
        "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY"
    );
    private static final long MAX_BACKOFF_MILLIS = 5000;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int maxRetries;
    private final Integer thinkingBudget;
    private final String thinkingLevel;
    private final boolean sendTemperature;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiAiProvider(
        @Value("${app.ai.gemini.api-key:}") String apiKey,
        @Value("${app.ai.gemini.model}") String model,
        @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
        @Value("${app.ai.gemini.connect-timeout-seconds:10}") int connectTimeoutSeconds,
        @Value("${app.ai.gemini.read-timeout-seconds:90}") int readTimeoutSeconds,
        @Value("${app.ai.gemini.max-retries:2}") int maxRetries,
        @Value("${app.ai.gemini.thinking-budget:}") String thinkingBudget,
        @Value("${app.ai.gemini.thinking-level:}") String thinkingLevel,
        @Value("${app.ai.gemini.send-temperature:false}") boolean sendTemperature,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model.trim();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.maxRetries = Math.max(0, Math.min(maxRetries, 3));
        this.thinkingBudget = thinkingBudget == null || thinkingBudget.isBlank() ? null : Integer.valueOf(thinkingBudget.trim());
        this.sendTemperature = sendTemperature;
        this.thinkingLevel = thinkingLevel == null || thinkingLevel.isBlank() ? null : thinkingLevel.trim().toLowerCase();
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();

        if (this.apiKey.isEmpty()) {
            log.warn("GEMINI_API_KEY is not set: AI endpoints will answer 503; all other features work normally.");
        } else {
            log.info("Gemini AI provider configured (model {}, endpoint {}/models/{}:generateContent).", this.model, this.baseUrl, this.model);
        }
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public String generateJson(AiRequest request) {
        if (!isConfigured()) {
            throw new AiUnavailableException();
        }

        String body = objectMapper.writeValueAsString(buildBody(request));
        String url = baseUrl + "/models/" + model + ":generateContent";

        for (int attempt = 0; ; attempt++) {
            try {
                String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);

                return extractText(response);
            } catch (RestClientResponseException ex) {
                // 429 and 5xx are transient; 400/401/403/404 and other statuses are never retried.
                int status = ex.getStatusCode().value();
                if (TRANSIENT_STATUSES.contains(status) && attempt < maxRetries) {
                    retryAfterBackoff("HTTP " + status, attempt, backoffMillis(attempt, ex));
                    continue;
                }
                throw translate(ex.getStatusCode(), ex);
            } catch (ResourceAccessException ex) {
                FailureType failure = classify(ex);

                if (failure == FailureType.READ_TIMEOUT) {
                    // Not retried: the request was sent and already waited the full read timeout.
                    log.warn("Gemini READ_TIMEOUT (attempt {}/{}, model {}), not retrying", attempt + 1, maxRetries + 1, model);
                    throw new AiProviderException(Reason.TIMEOUT, "The AI service took too long to respond. Please try again.", ex);
                }

                // Connect timeouts and other connection failures happen before the request is
                // processed, so they are safe to retry with the same bounded backoff.
                if (attempt < maxRetries) {
                    retryAfterBackoff(failure.name(), attempt, backoffMillis(attempt, null));
                    continue;
                }

                log.warn("Gemini {} (attempt {}/{}, model {}), giving up", failure, attempt + 1, maxRetries + 1, model);
                throw failure == FailureType.CONNECT_TIMEOUT
                    ? new AiProviderException(Reason.TIMEOUT, "The AI service took too long to respond. Please try again.", ex)
                    : new AiProviderException(Reason.ERROR, "The AI service could not be reached. Please try again later.", ex);
            } catch (RestClientException ex) {
                log.warn("Gemini request failed: {}", ex.getClass().getSimpleName());
                throw new AiProviderException(Reason.ERROR, "The AI service could not complete the request.", ex);
            }
        }
    }


    /* ---------- Request ---------- */

    private Map<String, Object> buildBody(AiRequest request) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        if (request.responseSchema() != null) {
            generationConfig.put("responseSchema", request.responseSchema());
        }
        if (sendTemperature) {
            generationConfig.put("temperature", request.temperature());
        }
        generationConfig.put("maxOutputTokens", request.maxOutputTokens());
        // Gemini 3 models use thinkingLevel; older 2.5 models use thinkingBudget.
        if (thinkingLevel != null) {
            generationConfig.put("thinkingConfig", Map.of("thinkingLevel", thinkingLevel));
        } else if (thinkingBudget != null) {
            generationConfig.put("thinkingConfig", Map.of("thinkingBudget", thinkingBudget));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.systemInstruction()))));
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", request.userPrompt())))));
        body.put("generationConfig", generationConfig);
        return body;
    }


    /* ---------- Response ---------- */

    /** Joins the candidate's text parts; rejects blocked, truncated or empty responses. */
    private String extractText(String response) {
        JsonNode root;
        try {
            root = objectMapper.readTree(response == null ? "" : response);
        } catch (JacksonException ex) {
            throw new AiProviderException(Reason.INVALID_RESPONSE, "The AI service returned an unreadable response.", ex);
        }

        if (root == null || !root.isObject()) {
            throw new AiProviderException(Reason.INVALID_RESPONSE, "The AI service returned an unreadable response.");
        }

        if (!root.path("promptFeedback").path("blockReason").isMissingNode()) {
            log.info("Gemini blocked the prompt: {}", root.path("promptFeedback").path("blockReason").asString());
            throw new AiProviderException(Reason.BLOCKED, "The AI service declined this request. Please rephrase and try again.");
        }

        JsonNode candidate = root.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            throw new AiProviderException(Reason.INVALID_RESPONSE, "The AI service returned no answer. Please try again.");
        }

        String finishReason = candidate.path("finishReason").asString("");
        if (BLOCKED_FINISH_REASONS.contains(finishReason)) {
            log.info("Gemini stopped with finish reason {}", finishReason);
            throw new AiProviderException(Reason.BLOCKED, "The AI service declined to answer this request.");
        }
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new AiProviderException(Reason.INVALID_RESPONSE, "The AI answer was cut off. Please try a smaller request.");
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            // Skip internal reasoning parts if the model returns any.
            if (part.path("thought").asBoolean(false)) continue;
            text.append(part.path("text").asString(""));
        }

        if (text.toString().isBlank()) {
            throw new AiProviderException(Reason.INVALID_RESPONSE, "The AI service returned an empty answer. Please try again.");
        }
        return text.toString();
    }


    /* ---------- Errors and retries ---------- */

    private AiProviderException translate(HttpStatusCode status, RestClientResponseException ex) {
        int code = status.value();
        // Log the status and a short error summary only; never the request or the key.
        ProviderError error = providerError(ex.getResponseBodyAsString());
        log.warn("Gemini HTTP {} {} (model {}): {}", code, error.status(), model, error.message());

        if (code == 429 || code == 503) {
            return new AiProviderException(Reason.BUSY, "The AI service is busy right now. Please try again in a minute.", ex);
        }
        if (code == 504) {
            return new AiProviderException(Reason.TIMEOUT, "The AI service took too long to respond. Please try again.", ex);
        }
        if (code == 400 || code == 401 || code == 403 || code == 404) {
            return new AiProviderException(Reason.CONFIGURATION, "The AI service is not configured correctly. Please contact support.", ex);
        }
        return new AiProviderException(Reason.ERROR, "The AI service could not complete the request.", ex);
    }

    /** Provider error status (e.g. NOT_FOUND) and a short message, for server logs only. */
    private record ProviderError(String status, String message) {
    }

    private ProviderError providerError(String errorBody) {
        try {
            JsonNode error = objectMapper.readTree(errorBody).path("error");
            String text = error.path("message").asString("");
            // Defensive: never log anything that looks like a key, even if a provider echoed one.
            text = text.replaceAll("AIza[0-9A-Za-z_\\-]{20,}", "[redacted]");
            if (text.length() > 200) text = text.substring(0, 200) + "...";
            return new ProviderError(error.path("status").asString("-"), text.isBlank() ? "(no details)" : text);
        } catch (RuntimeException ex) {
            return new ProviderError("-", "(no details)");
        }
    }

    /** Network-level failure types (no HTTP response was received). */
    private enum FailureType {
        /** TCP/TLS connection not established in time: the request never reached Gemini. */
        CONNECT_TIMEOUT,
        /** Connected, but no response within the read timeout. */
        READ_TIMEOUT,
        /** Other connection problems (DNS failure, connection refused or reset). */
        CONNECTION_ERROR
    }

    private static FailureType classify(ResourceAccessException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            // HttpConnectTimeoutException extends HttpTimeoutException, so check it first.
            if (cause instanceof HttpConnectTimeoutException) {
                return FailureType.CONNECT_TIMEOUT;
            }
            if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
                return FailureType.READ_TIMEOUT;
            }
        }
        return FailureType.CONNECTION_ERROR;
    }

    /** Logs a retry (type, attempt, model, delay; never the key) and waits. */
    private void retryAfterBackoff(String failureType, int attempt, long delayMillis) {
        log.info("Gemini {} (attempt {}/{}, model {}), retrying in {} ms",
            failureType, attempt + 1, maxRetries + 1, model, delayMillis);
        sleep(delayMillis);
    }

    /**
     * Exponential backoff (1s, 2s, 4s ...) plus up to 25% random jitter, capped at
     * MAX_BACKOFF_MILLIS. A short Retry-After header (429/503) is honoured as the minimum.
     */
    private static long backoffMillis(int attempt, RestClientResponseException ex) {
        long base = Math.min(1000L << attempt, MAX_BACKOFF_MILLIS);

        if (ex != null && ex.getResponseHeaders() != null) {
            String retryAfter = ex.getResponseHeaders().getFirst("Retry-After");
            if (retryAfter != null && retryAfter.matches("\\d{1,3}")) {
                base = Math.min(Long.parseLong(retryAfter) * 1000, MAX_BACKOFF_MILLIS);
            }
        }

        long jitter = ThreadLocalRandom.current().nextLong(base / 4 + 1);
        return Math.min(base + jitter, MAX_BACKOFF_MILLIS);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(Reason.ERROR, "The AI request was interrupted.", ex);
        }
    }
}
