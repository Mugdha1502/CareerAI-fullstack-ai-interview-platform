package com.careerai.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/ai/practice/questions. Topic and module are checked against the practice catalogue. */
public record AiQuestionRequest(
    @NotBlank(message = "Module is required")
    @Pattern(regexp = "^(technical|aptitude)$", message = "AI questions are available for the technical and aptitude modules")
    String module,

    @NotBlank(message = "Topic is required")
    @Size(max = 60, message = "Topic is too long")
    String topic,

    @NotBlank(message = "Difficulty is required")
    @Pattern(regexp = "^(Beginner|Intermediate|Advanced)$", message = "Difficulty must be Beginner, Intermediate or Advanced")
    String difficulty,

    @NotBlank(message = "Question type is required")
    @Pattern(regexp = "^(?i)(MCQ|DESCRIPTIVE)$", message = "Question type must be MCQ or DESCRIPTIVE")
    String questionType,

    @Min(value = 1, message = "Count must be at least 1")
    @Max(value = 20, message = "Count must be at most 20")
    Integer count
) {
}
