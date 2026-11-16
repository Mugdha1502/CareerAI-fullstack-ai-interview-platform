package com.careerai.backend.dto;

import com.careerai.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** The current user's safe account fields, returned by GET /api/auth/me. */
@Getter
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String role;
    private String plan;
    private boolean onboardingCompleted;

    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole().name(),
            user.getPlan().name(),
            user.isOnboardingCompleted()
        );
    }
}
