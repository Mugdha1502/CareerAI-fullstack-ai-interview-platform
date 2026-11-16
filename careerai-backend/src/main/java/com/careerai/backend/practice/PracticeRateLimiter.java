package com.careerai.backend.practice;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user, per-action sliding one-minute window (in memory; fits the single-instance app).
 * Protects the external execution provider and the SQL sandbox from being hammered.
 */
@Component
public class PracticeRateLimiter {

    private static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /** Records one request or throws 429 RATE_LIMITED when the user already used limitPerMinute. */
    public void check(Long userId, String action, int limitPerMinute) {
        long now = System.currentTimeMillis();
        Deque<Long> window = windows.computeIfAbsent(userId + ":" + action, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MS) window.pollFirst();
            if (window.size() >= limitPerMinute) {
                long retryAfter = Math.max(1, (WINDOW_MS - (now - window.peekFirst())) / 1000);
                throw new PracticeExecutionException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Too many requests. Please wait about " + retryAfter + " seconds and try again.");
            }
            window.addLast(now);
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty() || now - entry.getValue().peekLast() >= WINDOW_MS;
                }
            });
        }
    }
}
