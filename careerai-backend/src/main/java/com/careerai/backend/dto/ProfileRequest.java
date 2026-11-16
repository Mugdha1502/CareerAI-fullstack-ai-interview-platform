package com.careerai.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static com.careerai.backend.dto.OnboardingRequest.CURRENT_ROLES;
import static com.careerai.backend.dto.OnboardingRequest.DIFFICULTIES;
import static com.careerai.backend.dto.OnboardingRequest.EXPERIENCE_LEVELS;
import static com.careerai.backend.dto.OnboardingRequest.MAX_DAILY_MINUTES;
import static com.careerai.backend.dto.OnboardingRequest.MAX_SKILLS;
import static com.careerai.backend.dto.OnboardingRequest.MIN_DAILY_MINUTES;

/**
 * Full replacement of the current user's profile (PUT /api/profile/me).
 * Every field is optional: a null or blank value clears that field.
 */
@Getter
@Setter
public class ProfileRequest {

    // Empty string is allowed so a cleared select ("Not set") is accepted.
    @Pattern(regexp = "^$|" + CURRENT_ROLES, message = "Current role is not a supported option")
    private String currentRole;

    @Size(max = 150, message = "Education must be at most 150 characters")
    private String education;

    @Pattern(regexp = "^$|" + EXPERIENCE_LEVELS, message = "Experience level is not a supported option")
    private String experienceLevel;

    @Size(max = MAX_SKILLS, message = "Add at most 30 skills")
    // Blank entries are allowed here and dropped by ProfileService.
    private List<@Size(max = 60, message = "Each skill must be at most 60 characters") String> skills = new ArrayList<>();

    @Size(max = 100, message = "Target role must be at most 100 characters")
    private String targetRole;

    @Size(max = 100, message = "Career goal must be at most 100 characters")
    private String careerGoal;

    @Min(value = MIN_DAILY_MINUTES, message = "Daily preparation time must be at least 15 minutes")
    @Max(value = MAX_DAILY_MINUTES, message = "Daily preparation time must be at most 8 hours")
    private Integer dailyPreparationMinutes;

    @Pattern(regexp = "^$|" + DIFFICULTIES, message = "Difficulty must be Beginner, Intermediate or Advanced")
    private String preferredDifficulty;
}
