package com.careerai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Starts a practice session (POST /api/practice/sessions). The user comes from the JWT. */
@Getter
@Setter
public class PracticeSessionRequest {

    public static final String MODULES =
        "technical|aptitude|coding|sql|system-design|voice|daily-challenge|mock-assessment";

    @NotBlank(message = "Module is required")
    @Pattern(regexp = MODULES, message = "Unknown practice module")
    private String module;

    @Size(max = 100, message = "Topic must be at most 100 characters")
    private String topic;

    @Pattern(regexp = "^$|Beginner|Intermediate|Advanced", message = "Difficulty must be Beginner, Intermediate or Advanced")
    private String difficulty;

    @Size(max = 60, message = "Question type must be at most 60 characters")
    private String questionType;
}
