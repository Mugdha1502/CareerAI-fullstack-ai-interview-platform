package com.careerai.backend.exception;

/** Readable text could not be extracted from an uploaded resume (422). OCR is not supported. */
public class ResumeTextExtractionException extends RuntimeException {

    public ResumeTextExtractionException(String message) {
        super(message);
    }
}
