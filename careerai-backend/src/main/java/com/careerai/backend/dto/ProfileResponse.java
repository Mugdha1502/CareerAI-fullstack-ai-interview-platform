package com.careerai.backend.dto;

import com.careerai.backend.entity.Profile;
import com.careerai.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The current user's career profile. Contains no user id or account secrets;
 * account fields (name, email, plan) come from GET /api/auth/me.
 * When no profile has been saved yet, every field is null and skills is empty.
 */
@Getter
@AllArgsConstructor
public class ProfileResponse {

    private String currentRole;
    private String education;
    private String experienceLevel;
    private List<String> skills;
    private String targetRole;
    private String careerGoal;
    private Integer dailyPreparationMinutes;
    private String preferredDifficulty;
    private boolean onboardingCompleted;
    private LocalDateTime updatedAt;

    public static ProfileResponse from(Profile profile, User user) {
        return new ProfileResponse(
            profile.getCurrentRole(),
            profile.getEducation(),
            profile.getExperienceLevel(),
            List.copyOf(profile.getSkills()),
            profile.getTargetRole(),
            profile.getCareerGoal(),
            profile.getDailyPreparationMinutes(),
            profile.getPreferredDifficulty(),
            user.isOnboardingCompleted(),
            profile.getUpdatedAt()
        );
    }

    public static ProfileResponse empty(User user) {
        return new ProfileResponse(
            null, null, null, List.of(), null, null, null, null,
            user.isOnboardingCompleted(),
            null
        );
    }
}
