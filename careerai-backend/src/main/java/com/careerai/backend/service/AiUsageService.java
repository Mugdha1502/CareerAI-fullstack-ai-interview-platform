package com.careerai.backend.service;

import com.careerai.backend.ai.AiProvider;
import com.careerai.backend.ai.AiProviderException;
import com.careerai.backend.ai.AiRateLimitException;
import com.careerai.backend.ai.AiResponseInvalidException;
import com.careerai.backend.ai.AiUnavailableException;
import com.careerai.backend.entity.AiUsageLog;
import com.careerai.backend.entity.User;
import com.careerai.backend.repository.AiUsageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Runs AI tasks for a user: checks availability and the hourly limit, then records
 * feature, provider, success/failure category and latency in ai_usage_logs.
 * Prompts and AI output are never stored.
 *
 * Not transactional on purpose: no database connection is held while waiting for the provider.
 */
@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    private final AiUsageLogRepository repository;
    private final AiProvider provider;
    private final int maxRequestsPerHour;

    public AiUsageService(
        AiUsageLogRepository repository,
        AiProvider provider,
        @Value("${app.ai.max-requests-per-hour:60}") int maxRequestsPerHour
    ) {
        this.repository = repository;
        this.provider = provider;
        this.maxRequestsPerHour = maxRequestsPerHour;
    }

    /**
     * Result of an optional AI step: either a value or a safe failure code with a
     * user-facing reason. Used where the feature must keep working without AI.
     */
    public record AiOutcome<T>(T value, String errorCode) {

        public boolean succeeded() {
            return value != null;
        }

        /** Short, safe explanation for the UI; never contains provider text. */
        public String reason() {
            if (errorCode == null) return null;
            return switch (errorCode) {
                case "UNAVAILABLE" -> "AI is not configured on the server.";
                case "BUSY" -> "The AI service is busy or its rate limit was reached. Try again in a minute.";
                case "TIMEOUT" -> "The AI service took too long to respond.";
                case "RATE_LIMITED" -> "You reached the hourly limit for AI requests.";
                case "INVALID_RESPONSE" -> "The AI response failed quality or safety checks.";
                case "BLOCKED" -> "The AI service declined this content.";
                case "CONFIGURATION" -> "The AI service is not configured correctly.";
                default -> "The AI service could not complete the request.";
            };
        }
    }

    /**
     * Like run(), but never throws for AI problems (unavailable, rate limits, provider
     * errors, invalid output): the caller gets the failure code and falls back.
     */
    public <T> AiOutcome<T> tryRun(User user, String feature, Supplier<T> task) {
        try {
            return new AiOutcome<>(run(user, feature, task), null);
        } catch (AiUnavailableException ex) {
            return new AiOutcome<>(null, "UNAVAILABLE");
        } catch (AiRateLimitException ex) {
            return new AiOutcome<>(null, "RATE_LIMITED");
        } catch (AiProviderException ex) {
            return new AiOutcome<>(null, ex.reason().name());
        } catch (AiResponseInvalidException ex) {
            return new AiOutcome<>(null, "INVALID_RESPONSE");
        }
    }

    public <T> T run(User user, String feature, Supplier<T> task) {
        if (!provider.isConfigured()) {
            record(user, feature, false, "UNAVAILABLE", 0);
            throw new AiUnavailableException();
        }

        long recent = repository.countByUserIdAndCreatedAtAfter(user.getId(), LocalDateTime.now().minusHours(1));
        if (recent >= maxRequestsPerHour) {
            throw new AiRateLimitException();
        }

        long start = System.nanoTime();
        try {
            T result = task.get();
            record(user, feature, true, null, start);
            return result;
        } catch (AiProviderException ex) {
            record(user, feature, false, ex.reason().name(), start);
            throw ex;
        } catch (AiResponseInvalidException ex) {
            // The detail (which check failed) is for the server log only.
            log.warn("AI response rejected for {}: {}", feature, ex.getMessage());
            record(user, feature, false, "INVALID_RESPONSE", start);
            throw ex;
        } catch (AiUnavailableException ex) {
            record(user, feature, false, "UNAVAILABLE", start);
            throw ex;
        } catch (RuntimeException ex) {
            record(user, feature, false, "ERROR", start);
            throw ex;
        }
    }

    private void record(User user, String feature, boolean success, String errorCode, long startNanos) {
        try {
            AiUsageLog entry = new AiUsageLog();
            entry.setUser(user);
            entry.setFeature(feature);
            entry.setProvider(provider.name());
            entry.setModel(provider.isConfigured() ? provider.model() : null);
            entry.setSuccess(success);
            entry.setErrorCode(errorCode);
            entry.setLatencyMs(startNanos == 0 ? null : (System.nanoTime() - startNanos) / 1_000_000);
            repository.save(entry);
        } catch (RuntimeException ex) {
            // Usage logging must never break the feature itself.
            log.warn("Could not record AI usage: {}", ex.getClass().getSimpleName());
        }
    }
}
