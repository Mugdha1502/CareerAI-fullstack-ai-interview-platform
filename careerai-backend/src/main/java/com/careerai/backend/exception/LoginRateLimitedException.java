package com.careerai.backend.exception;

/** Too many failed logins from this client; answered with 429 and Retry-After. */
public class LoginRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super("Too many login attempts. Please wait a few minutes and try again.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
