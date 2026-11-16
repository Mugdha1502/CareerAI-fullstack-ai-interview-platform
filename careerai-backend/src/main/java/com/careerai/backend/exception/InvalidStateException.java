package com.careerai.backend.exception;

/** The request does not fit the current state, e.g. answering a completed interview (409). */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}
