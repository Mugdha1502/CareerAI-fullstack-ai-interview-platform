package com.careerai.backend.exception;

/** Unknown session id, or a session owned by another user (reported the same way). */
public class PracticeSessionNotFoundException extends RuntimeException {

    public PracticeSessionNotFoundException() {
        super("Practice session not found");
    }
}
