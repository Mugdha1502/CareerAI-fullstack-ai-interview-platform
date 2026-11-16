package com.careerai.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Answers from the onboarding wizard. The user comes from the JWT, never from this body. */
@Getter
@Setter
public class OnboardingRequest {

    // Allowed values match the options offered by the frontend.
    public static final String CURRENT_ROLES = "Student|Fresher|Working Professional|Career Switcher";
    public static final String EXPERIENCE_LEVELS =
        "No professional experience|Less than 1 year|1-3 years|3-5 years|5\\+ years";
    public static final String DIFFICULTIES = "Beginner|Intermediate|Advanced";

    public static final int MIN_DAILY_MINUTES = 15;
    public static final int MAX_DAILY_MINUTES = 480;
    public static final int MAX_SKILLS = 30;

    @NotBlank(message = "Please choose your current role")
    @Pattern(regexp = CURRENT_ROLES, message = "Current role is not a supported option")
    private String currentRole;

    @Size(max = 150, message = "Education must be at most 150 characters")
    private String education;

    @NotBlank(message = "Please choose your experience level")
    @Pattern(regexp = EXPERIENCE_LEVELS, message = "Experience level is not a supported option")
    private String experienceLevel;

    @Size(max = MAX_SKILLS, message = "Add at most 30 skills")
    // Blank entries are allowed here and dropped by ProfileService.
    private List<@Size(max = 60, message = "Each skill must be at most 60 characters") String> skills = new ArrayList<>();

    @NotBlank(message = "Please choose a target role")
    @Size(max = 100, message = "Target role must be at most 100 characters")
    private String targetRole;

    @NotBlank(message = "Please choose a career goal")
    @Size(max = 100, message = "Career goal must be at most 100 characters")
    private String careerGoal;

    @NotNull(message = "Please choose your daily preparation time")
    @Min(value = MIN_DAILY_MINUTES, message = "Daily preparation time must be at least 15 minutes")
    @Max(value = MAX_DAILY_MINUTES, message = "Daily preparation time must be at most 8 hours")
    private Integer dailyPreparationMinutes;

    @NotBlank(message = "Please choose a preferred difficulty")
    @Pattern(regexp = DIFFICULTIES, message = "Difficulty must be Beginner, Intermediate or Advanced")
    private String preferredDifficulty;
}
