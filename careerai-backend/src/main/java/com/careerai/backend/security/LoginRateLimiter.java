package com.careerai.backend.security;

import com.careerai.backend.exception.LoginRateLimitedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles repeated FAILED logins with sliding windows (in memory, per backend instance):
 *
 *  - account + client IP: after {@code max-failures-per-account} failures in the window that
 *    combination gets 429. Keyed with the IP so an attacker elsewhere cannot lock the real user out.
 *  - client IP alone: after {@code max-failures-per-ip} failures across any accounts (password
 *    spraying), that IP gets 429. Skipped when the IP is only an unconfigured proxy's address,
 *    so one attacker never blocks every user behind the same proxy.
 *
 * Unknown and existing emails are counted the same way, so the limit reveals nothing about which
 * accounts exist. A successful login clears that account + IP counter. Multiple backend instances
 * each keep their own counters; a shared store (e.g. Redis) is needed for one global limit.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxPerAccount;
    private final int maxPerIp;
    private final long windowMillis;
    private final int maxTrackedKeys;

    public LoginRateLimiter(
        Clock clock,
        @Value("${app.security.login.max-failures-per-account:5}") int maxPerAccount,
        @Value("${app.security.login.max-failures-per-ip:30}") int maxPerIp,
        @Value("${app.security.login.window:PT15M}") Duration window,
        @Value("${app.security.login.max-tracked-keys:100000}") int maxTrackedKeys
    ) {
        if (maxPerAccount < 1 || maxPerIp < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalStateException("app.security.login limits and window must be positive.");
        }
        this.clock = clock;
        this.maxPerAccount = maxPerAccount;
        this.maxPerIp = maxPerIp;
        this.windowMillis = window.toMillis();
        this.maxTrackedKeys = maxTrackedKeys;
    }

    /** Throws 429 when this account + IP (or this IP) already used up its failed attempts. */
    public void checkAllowed(String email, ClientIpResolver.ClientAddress client) {
        long now = clock.millis();
        long retry = retryAfter(accountKey(email, client), maxPerAccount, now);
        if (!client.untrustedProxy()) retry = Math.max(retry, retryAfter(ipKey(client), maxPerIp, now));
        if (retry > 0) {
            log.info("LOGIN_RATE_LIMITED: client {} ({} s)", client.ip(), retry);
            throw new LoginRateLimitedException(retry);
        }
    }

    public void recordFailure(String email, ClientIpResolver.ClientAddress client) {
        long now = clock.millis();
        add(accountKey(email, client), now);
        if (!client.untrustedProxy()) add(ipKey(client), now);
        if (failures.size() > maxTrackedKeys) prune(now);
    }

    /** A correct password clears this account's counter from this IP (the per-IP spray counter stays). */
    public void recordSuccess(String email, ClientIpResolver.ClientAddress client) {
        failures.remove(accountKey(email, client));
    }

    private long retryAfter(String key, int limit, long now) {
        Deque<Long> window = failures.get(key);
        if (window == null) return 0;
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) window.pollFirst();
            if (window.size() < limit) return 0;
            // Allowed again once enough of the oldest failures have left the window.
            long releaseAt = window.stream().skip(window.size() - limit).findFirst().orElse(now) + windowMillis;
            return Math.max(1, (releaseAt - now + 999) / 1000);
        }
    }

    private void add(String key, long now) {
        Deque<Long> window = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) window.pollFirst();
            window.addLast(now);
        }
    }

    private void prune(long now) {
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || now - entry.getValue().peekLast() >= windowMillis;
            }
        });
    }

    private static String accountKey(String email, ClientIpResolver.ClientAddress client) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return "account:" + normalized + "|" + client.ip();
    }

    private static String ipKey(ClientIpResolver.ClientAddress client) {
        return "ip:" + client.ip();
    }
}
