package com.careerai.backend.ai;

/**
 * A provider call failed. The message is safe to show to users: it never contains the
 * provider's raw error body, request details or keys (those are only logged in summary).
 */
public class AiProviderException extends RuntimeException {

    public enum Reason {
        /** The provider did not answer in time. */
        TIMEOUT,
        /** Rate limit or temporary overload, still failing after retries. */
        BUSY,
        /** The provider rejected the key or configuration (401/403/404, bad request). */
        CONFIGURATION,
        /** The provider blocked the prompt or the output for safety reasons. */
        BLOCKED,
        /** Empty, truncated or otherwise unusable response envelope. */
        INVALID_RESPONSE,
        /** Any other failure (network error, unexpected status). */
        ERROR
    }

    private final Reason reason;

    public AiProviderException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiProviderException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
