package com.careerai.backend.exception;

/** The authenticated account no longer exists (e.g. deleted after the token was issued). */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("User not found");
    }
}
