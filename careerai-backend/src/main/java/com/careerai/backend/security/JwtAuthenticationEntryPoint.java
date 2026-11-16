package com.careerai.backend.security;

import com.careerai.backend.security.JwtAuthenticationFilter.AuthError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Answers unauthenticated requests to protected endpoints with a 401 and a
 * JSON message that explains why (missing, expired or invalid token).
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException {

        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);

        String message;

        if (reason == AuthError.EXPIRED) {
            message = "Token has expired. Please log in again.";
        } else if (reason == AuthError.INVALID) {
            message = "Invalid or malformed token";
        } else if (reason == AuthError.INVALID_SCHEME) {
            message = "Authorization header must use the Bearer scheme";
        } else if (reason == AuthError.USER_NOT_FOUND) {
            message = "User for this token no longer exists";
        } else {
            message = "Authentication required";
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader("WWW-Authenticate", "Bearer");
        writeJson(response, message);
    }

    // Messages are fixed strings above, so no JSON escaping of user input is needed.
    static void writeJson(HttpServletResponse response, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
