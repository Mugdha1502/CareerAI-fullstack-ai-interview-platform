package com.careerai.backend.ai;

/** The user reached the hourly AI request limit (app.ai.max-requests-per-hour). Reported as 429. */
public class AiRateLimitException extends RuntimeException {

    public AiRateLimitException() {
        super("You have reached the hourly limit for AI requests. Please try again later.");
    }
}
