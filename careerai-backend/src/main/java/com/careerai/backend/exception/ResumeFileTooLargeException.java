package com.careerai.backend.exception;

/** Uploaded resume exceeds the configured size limit (413). */
public class ResumeFileTooLargeException extends RuntimeException {

    public ResumeFileTooLargeException(long maxBytes) {
        super("Resume file is too large. Maximum size is " + (maxBytes / (1024 * 1024)) + " MB.");
    }
}
