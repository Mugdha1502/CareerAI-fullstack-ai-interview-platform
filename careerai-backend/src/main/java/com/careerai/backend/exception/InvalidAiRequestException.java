package com.careerai.backend.exception;

/** An AI request with an unknown topic or unsupported option (400). Checked before any provider call. */
public class InvalidAiRequestException extends RuntimeException {

    public InvalidAiRequestException(String message) {
        super(message);
    }
}
