package com.careerai.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * The talking avatar (Simli) could not be started. The voice interview itself is never affected:
 * the frontend shows its built-in interviewer instead. The code tells the frontend whether a
 * retry can help (AVATAR_NOT_CONFIGURED never does).
 */
public class AvatarUnavailableException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AvatarUnavailableException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
