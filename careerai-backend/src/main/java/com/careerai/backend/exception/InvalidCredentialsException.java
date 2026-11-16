package com.careerai.backend.exception;

/**
 * Thrown for both an unknown email and a wrong password, so the response
 * never reveals which accounts exist.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
