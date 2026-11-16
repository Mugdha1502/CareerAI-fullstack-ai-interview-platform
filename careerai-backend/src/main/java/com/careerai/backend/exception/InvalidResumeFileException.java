package com.careerai.backend.exception;

/** Uploaded file has an unsupported extension, MIME type or content signature (400). */
public class InvalidResumeFileException extends RuntimeException {

    public InvalidResumeFileException(String message) {
        super(message);
    }
}
