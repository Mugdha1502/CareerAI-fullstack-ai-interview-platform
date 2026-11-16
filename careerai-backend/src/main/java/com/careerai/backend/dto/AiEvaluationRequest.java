package com.careerai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/ai/practice/evaluate: one written answer to a descriptive question. */
public record AiEvaluationRequest(
    @NotBlank(message = "Module is required")
    @Pattern(regexp = "^(technical|aptitude)$", message = "AI evaluation is available for the technical and aptitude modules")
    String module,

    @NotBlank(message = "Topic is required")
    @Size(max = 60, message = "Topic is too long")
    String topic,

    @NotBlank(message = "Difficulty is required")
    @Pattern(regexp = "^(Beginner|Intermediate|Advanced)$", message = "Difficulty must be Beginner, Intermediate or Advanced")
    String difficulty,

    @NotBlank(message = "Question is required")
    @Size(max = 1000, message = "Question must be at most 1000 characters")
    String question,

    @NotBlank(message = "Answer is required")
    @Size(min = 20, max = 5000, message = "Answer must be between 20 and 5000 characters")
    String answer
) {
}
