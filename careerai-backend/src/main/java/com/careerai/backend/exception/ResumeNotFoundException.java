package com.careerai.backend.exception;

/** Unknown resume/version/analysis id, or one owned by another user (reported the same way: 404). */
public class ResumeNotFoundException extends RuntimeException {

    public ResumeNotFoundException(String message) {
        super(message);
    }
}
