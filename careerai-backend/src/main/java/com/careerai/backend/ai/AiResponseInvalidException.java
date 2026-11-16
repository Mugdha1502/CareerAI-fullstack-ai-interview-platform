package com.careerai.backend.ai;

/**
 * The provider answered, but the JSON was malformed or failed validation
 * (missing fields, wrong option count, answer not among options, score out of range).
 * The detail is logged; users see a generic message. Reported as 502.
 */
public class AiResponseInvalidException extends RuntimeException {

    public AiResponseInvalidException(String detail) {
        super(detail);
    }

    public AiResponseInvalidException(String detail, Throwable cause) {
        super(detail, cause);
    }
}
