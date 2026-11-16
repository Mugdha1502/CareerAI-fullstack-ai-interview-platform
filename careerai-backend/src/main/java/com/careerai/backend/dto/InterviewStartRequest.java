package com.careerai.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/interviews. RESUME needs a resumeId; JOB_SPECIFIC needs a jobDescription
 * (resumeId optional). The user comes from the JWT, never from this body.
 */
public record InterviewStartRequest(
    @NotBlank(message = "Interview type is required")
    @Pattern(regexp = "^(?i)(TECHNICAL|HR|BEHAVIORAL|RESUME|JOB_SPECIFIC|MIXED)$",
        message = "Type must be TECHNICAL, HR, BEHAVIORAL, RESUME, JOB_SPECIFIC or MIXED")
    String type,

    @Size(max = 120, message = "Target role must be at most 120 characters")
    String targetRole,

    @Pattern(regexp = "^(?i)(BEGINNER|INTERMEDIATE|ADVANCED)$", message = "Difficulty must be Beginner, Intermediate or Advanced")
    String difficulty,

    Long resumeId,

    @Size(max = 20000, message = "Job description must be at most 20000 characters")
    String jobDescription,

    @Min(value = 3, message = "An interview has at least 3 questions")
    @Max(value = 10, message = "An interview has at most 10 questions")
    Integer questionCount,

    @Size(max = 10, message = "At most 10 focus topics")
    List<@Size(max = 80, message = "Focus topics must be at most 80 characters") String> focusTopics,

    /** TEXT (default) or VOICE. */
    @Pattern(regexp = "^(?i)(TEXT|VOICE)$", message = "Mode must be TEXT or VOICE")
    String mode
) {
}
