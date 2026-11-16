package com.careerai.backend.dto;

import com.careerai.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Returned by register and login. Includes the JWT; never the password or its hash. */
@Getter
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private Long userId;
    private String name;
    private String email;
    private String role;
    private String plan;
    private boolean onboardingCompleted;
    private String message;

    public static AuthResponse from(User user, String token, String message) {
        return new AuthResponse(
            token,
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole().name(),
            user.getPlan().name(),
            user.isOnboardingCompleted(),
            message
        );
    }
}
