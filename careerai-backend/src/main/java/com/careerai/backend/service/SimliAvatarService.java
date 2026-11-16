package com.careerai.backend.service;

import com.careerai.backend.dto.SimliSessionRequest;
import com.careerai.backend.dto.SimliSessionResponse;
import com.careerai.backend.entity.InterviewSession;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.AvatarUnavailableException;
import com.careerai.backend.exception.InvalidStateException;
import com.careerai.backend.exception.ResourceNotFoundException;
import com.careerai.backend.repository.InterviewSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates Simli avatar sessions for voice interviews. Simli is only the visual layer: Vapi keeps
 * the conversation, the voice and the transcript.
 *
 * The permanent SIMLI_API_KEY stays on the server: it is sent to Simli's token endpoint
 * (POST {base}/compose/token, header x-simli-api-key — the same call simli-client's
 * generateSimliSessionToken makes) and only the short-lived session token goes to the browser.
 * The key is never logged or returned.
 *
 * Free-plan protection: sessions are only created for the caller's own IN_PROGRESS voice
 * interviews; ONE "initial" session per interview (a reloaded page or second tab gets 409
 * AVATAR_ALREADY_STARTED instead of a new session), at most
 * app.avatar.simli.max-sessions-per-interview sessions in total (initial + explicit Retry Avatar),
 * and each session is capped by maxSessionLength on Simli's side.
 *
 * Log categories (never the key, a session token or a JWT): SIMLI_SESSION_CREATED,
 * LOCAL_AVATAR_ALREADY_STARTED, LOCAL_AVATAR_LIMIT_REACHED, SIMLI_UPSTREAM_429, SIMLI_QUOTA,
 * SIMLI_AUTH_FAILURE, SIMLI_REQUEST_REJECTED, SIMLI_UPSTREAM_ERROR.
 */
@Service
public class SimliAvatarService {

    private static final Logger log = LoggerFactory.getLogger(SimliAvatarService.class);
    private static final Duration COUNTER_TTL = Duration.ofHours(3);

    private final String apiKey;
    private final String faceId;
    private final String baseUrl;
    private final int maxSessionSeconds;
    private final int maxIdleSeconds;
    private final int maxSessionsPerInterview;
    private final InterviewSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /** interviewId → avatar sessions created (in memory; a restart only resets the cap). */
    private final Map<Long, Counter> sessionsByInterview = new ConcurrentHashMap<>();

    private record Counter(int count, Instant firstAt) { }

    public SimliAvatarService(
        @Value("${app.avatar.simli.api-key:}") String apiKey,
        @Value("${app.avatar.simli.face-id:}") String faceId,
        @Value("${app.avatar.simli.base-url:https://api.simli.ai}") String baseUrl,
        @Value("${app.avatar.simli.max-session-seconds:1800}") int maxSessionSeconds,
        @Value("${app.avatar.simli.max-idle-seconds:120}") int maxIdleSeconds,
        @Value("${app.avatar.simli.max-sessions-per-interview:3}") int maxSessionsPerInterview,
        @Value("${app.avatar.simli.timeout-seconds:15}") int timeoutSeconds,
        InterviewSessionRepository sessionRepository,
        ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.faceId = faceId == null ? "" : faceId.trim();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.maxSessionSeconds = Math.max(60, maxSessionSeconds);
        this.maxIdleSeconds = Math.max(30, maxIdleSeconds);
        this.maxSessionsPerInterview = Math.max(1, maxSessionsPerInterview);
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 10)))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();

        if (!isConfigured()) {
            log.warn("SIMLI_API_KEY or face id is not set: voice interviews use the built-in interviewer instead of the avatar.");
        } else {
            log.info("Simli avatar configured (endpoint {}/compose/token, face {}).", this.baseUrl, mask(this.faceId));
        }
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty() && !faceId.isEmpty();
    }

    public SimliSessionResponse createSession(User user, SimliSessionRequest request) {
        Long interviewId = request.interviewId();
        int attempt = request.attempt() == null ? 0 : request.attempt();
        String reason = request.isInitial() ? "initial" : "retry";
        InterviewSession interview = sessionRepository.findByIdAndUserId(interviewId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));
        if (!interview.isVoice()) {
            throw new InvalidStateException("The avatar is only available for voice interviews.");
        }
        if (!InterviewSession.Status.IN_PROGRESS.name().equals(interview.getStatus())) {
            throw new InvalidStateException("This interview has already ended.");
        }
        if (!isConfigured()) {
            throw new AvatarUnavailableException(HttpStatus.SERVICE_UNAVAILABLE, "AVATAR_NOT_CONFIGURED", "The interviewer avatar is not configured.");
        }
        int sessionNumber = reserveSession(user, interviewId, attempt, request.isInitial());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("faceId", faceId);
        // The browser streams Vapi's assistant track continuously (listen-to-track style), which
        // Simli documents with handleSilence=false to avoid audio artifacts.
        body.put("handleSilence", false);
        body.put("maxSessionLength", maxSessionSeconds);
        body.put("maxIdleTime", maxIdleSeconds);

        try {
            String response = restClient.post()
                .uri(baseUrl + "/compose/token")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("x-simli-api-key", apiKey)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);

            JsonNode json = objectMapper.readTree(response == null ? "" : response);
            String token = json.path("session_token").asString("");
            if (token.isBlank()) {
                log.warn("SIMLI_UPSTREAM_ERROR: token response had no session_token (user {}, interview {}, attempt {}).", user.getId(), interviewId, attempt);
                throw busy();
            }
            JsonNode warnings = json.path("warnings");
            if (warnings.isArray() && !warnings.isEmpty()) {
                log.info("Simli token warnings (interview {}, attempt {}): {}", interviewId, attempt, safe(warnings.toString()));
            }
            log.info("SIMLI_SESSION_CREATED: user {}, interview {}, attempt {} ({}), session {} of {} for this interview.",
                user.getId(), interviewId, attempt, reason, sessionNumber, maxSessionsPerInterview);
            return new SimliSessionResponse(token, maxSessionSeconds);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("{}: Simli token request failed with HTTP {} (user {}, interview {}, attempt {}): {}",
                category(status), status, user.getId(), interviewId, attempt, safe(ex.getResponseBodyAsString()));
            throw translate(status);
        } catch (RestClientException ex) {
            log.warn("SIMLI_UPSTREAM_ERROR: Simli token request failed (user {}, interview {}, attempt {}): {}",
                user.getId(), interviewId, attempt, ex.getClass().getSimpleName());
            throw busy();
        } catch (AvatarUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Unreadable JSON and similar: never pass provider details to the client.
            log.warn("SIMLI_UPSTREAM_ERROR: token response could not be read (user {}, interview {}, attempt {}): {}",
                user.getId(), interviewId, attempt, ex.getClass().getSimpleName());
            throw busy();
        }
    }

    /**
     * Counts an avatar session for the interview and returns its number. Refuses a second
     * "initial" session (only an explicit retry may open another) and anything over the cap.
     * Refused requests do not use up the allowance. Counters are per interview id.
     */
    private int reserveSession(User user, Long interviewId, int attempt, boolean initial) {
        Instant now = Instant.now();
        sessionsByInterview.entrySet().removeIf(entry -> entry.getValue().firstAt().plus(COUNTER_TTL).isBefore(now));

        String[] refusal = new String[1];
        Counter counter = sessionsByInterview.compute(interviewId, (id, current) -> {
            int used = current == null ? 0 : current.count();
            if (initial && used > 0) {
                refusal[0] = "LOCAL_AVATAR_ALREADY_STARTED";
                return current;
            }
            if (used >= maxSessionsPerInterview) {
                refusal[0] = "LOCAL_AVATAR_LIMIT_REACHED";
                return current;
            }
            return current == null ? new Counter(1, now) : new Counter(used + 1, current.firstAt());
        });

        if ("LOCAL_AVATAR_ALREADY_STARTED".equals(refusal[0])) {
            log.info("LOCAL_AVATAR_ALREADY_STARTED: user {}, interview {}, attempt {}: initial session already created ({} so far); Simli not called.",
                user.getId(), interviewId, attempt, counter.count());
            throw new AvatarUnavailableException(HttpStatus.CONFLICT, "AVATAR_ALREADY_STARTED",
                "The interviewer avatar was already started for this interview.");
        }
        if ("LOCAL_AVATAR_LIMIT_REACHED".equals(refusal[0])) {
            log.info("LOCAL_AVATAR_LIMIT_REACHED: user {}, interview {}, attempt {}: {} sessions already created; Simli not called.",
                user.getId(), interviewId, attempt, maxSessionsPerInterview);
            throw new AvatarUnavailableException(HttpStatus.TOO_MANY_REQUESTS, "AVATAR_LIMIT_REACHED",
                "The interviewer avatar was restarted too many times for this interview.");
        }
        return counter.count();
    }

    private static String category(int status) {
        return switch (status) {
            case 429 -> "SIMLI_UPSTREAM_429";
            case 402 -> "SIMLI_QUOTA";
            case 401, 403 -> "SIMLI_AUTH_FAILURE";
            case 400, 404, 422 -> "SIMLI_REQUEST_REJECTED";
            default -> "SIMLI_UPSTREAM_ERROR";
        };
    }

    private AvatarUnavailableException translate(int status) {
        if (status == 401 || status == 403) {
            log.warn("SIMLI_AUTH_FAILURE: Simli rejected the configured credentials; check SIMLI_API_KEY.");
            return new AvatarUnavailableException(HttpStatus.SERVICE_UNAVAILABLE, "AVATAR_NOT_CONFIGURED", "The interviewer avatar is not configured correctly.");
        }
        if (status == 402) {
            return new AvatarUnavailableException(HttpStatus.TOO_MANY_REQUESTS, "AVATAR_QUOTA", "The interviewer avatar is out of capacity right now.");
        }
        if (status == 429) {
            return new AvatarUnavailableException(HttpStatus.TOO_MANY_REQUESTS, "AVATAR_RATE_LIMITED", "The interviewer avatar is busy right now. Please try again shortly.");
        }
        if (status == 400 || status == 404 || status == 422) {
            // Usually an invalid face id or session option.
            return new AvatarUnavailableException(HttpStatus.SERVICE_UNAVAILABLE, "AVATAR_NOT_CONFIGURED", "The interviewer avatar is not configured correctly.");
        }
        return busy();
    }

    private static AvatarUnavailableException busy() {
        return new AvatarUnavailableException(HttpStatus.SERVICE_UNAVAILABLE, "AVATAR_BUSY", "The interviewer avatar is temporarily unavailable.");
    }

    /** Provider text for logs: one line, short, and never containing the key. */
    private String safe(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ");
        if (!apiKey.isEmpty()) value = value.replace(apiKey, "[redacted]");
        return value.length() > 200 ? value.substring(0, 200) + "..." : value;
    }

    private static String mask(String value) {
        return value.length() <= 12 ? "(set)" : value.substring(0, 6) + "..." + value.substring(value.length() - 6);
    }
}
