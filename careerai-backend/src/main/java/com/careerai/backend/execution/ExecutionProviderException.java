package com.careerai.backend.execution;

/**
 * The execution provider could not produce a result: not configured, unreachable, rejected our
 * credentials, quota used up, or did not finish in time. Messages are safe for users.
 */
public class ExecutionProviderException extends RuntimeException {

    public enum Reason { UNAVAILABLE, TIMEOUT, LIMIT_REACHED }

    private final Reason reason;

    public ExecutionProviderException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public static ExecutionProviderException unavailable() {
        return new ExecutionProviderException(Reason.UNAVAILABLE, "Code execution is temporarily unavailable.");
    }

    /** The provider's execution quota is used up (e.g. JDoodle daily credits → HTTP 429). Never retried. */
    public static ExecutionProviderException limitReached() {
        return new ExecutionProviderException(Reason.LIMIT_REACHED, "Code execution provider daily limit reached. Please try again later.");
    }

    public static ExecutionProviderException timeout() {
        return new ExecutionProviderException(Reason.TIMEOUT, "Code execution took too long. Please try again.");
    }
}
