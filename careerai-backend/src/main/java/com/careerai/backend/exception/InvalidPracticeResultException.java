package com.careerai.backend.exception;

/** Result numbers that are individually valid but inconsistent with each other. */
public class InvalidPracticeResultException extends RuntimeException {

    public InvalidPracticeResultException(String message) {
        super(message);
    }
}
