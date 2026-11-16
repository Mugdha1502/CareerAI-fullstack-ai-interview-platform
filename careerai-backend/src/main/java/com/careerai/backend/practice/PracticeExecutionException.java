package com.careerai.backend.practice;

import org.springframework.http.HttpStatus;

/**
 * A coding/SQL practice request that cannot be served, with a safe user message and a code
 * (INVALID_LANGUAGE, SQL_NOT_ALLOWED, SQL_TIMEOUT, SQL_EXECUTION_ERROR, RATE_LIMITED,
 * QUESTION_NOT_FOUND, VALIDATION_ERROR). Never carries stack traces, credentials or paths.
 */
public class PracticeExecutionException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public PracticeExecutionException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
