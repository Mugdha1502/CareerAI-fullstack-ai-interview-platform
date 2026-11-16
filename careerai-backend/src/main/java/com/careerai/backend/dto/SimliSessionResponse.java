package com.careerai.backend.dto;

/**
 * Browser-safe Simli credentials: a short-lived session token for one avatar session
 * (the face id is bound to it on the server). The permanent API key is never returned.
 */
public record SimliSessionResponse(
    String sessionToken,
    int maxSessionSeconds
) {
}
