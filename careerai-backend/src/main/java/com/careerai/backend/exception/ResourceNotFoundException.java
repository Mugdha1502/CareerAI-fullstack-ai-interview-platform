package com.careerai.backend.exception;

/** Unknown id, or one owned by another user (reported the same way: 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
