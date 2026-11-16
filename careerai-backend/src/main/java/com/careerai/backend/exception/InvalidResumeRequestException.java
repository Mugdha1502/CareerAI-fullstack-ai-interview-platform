package com.careerai.backend.exception;

/** Resume request that is well-formed JSON but not acceptable, e.g. a too-short job description (400). */
public class InvalidResumeRequestException extends RuntimeException {

    public InvalidResumeRequestException(String message) {
        super(message);
    }
}
