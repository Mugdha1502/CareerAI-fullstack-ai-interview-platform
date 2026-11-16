package com.careerai.backend.exception;

public class PracticeResultAlreadySavedException extends RuntimeException {

    public PracticeResultAlreadySavedException() {
        super("A result has already been saved for this practice session");
    }
}
