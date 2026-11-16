package com.careerai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * POST /api/ai/practice/system-design/evaluate. sections maps the workspace section ids
 * (requirements, api, database, architecture, caching, scaling, security, tradeoffs) to text.
 */
public record SystemDesignEvaluationRequest(
    @NotBlank(message = "Topic is required")
    @Size(max = 60, message = "Topic is too long")
    String topic,

    @NotBlank(message = "Difficulty is required")
    @Pattern(regexp = "^(Beginner|Intermediate|Advanced)$", message = "Difficulty must be Beginner, Intermediate or Advanced")
    String difficulty,

    @NotEmpty(message = "Write at least one section of your design")
    @Size(max = 12, message = "Too many sections")
    Map<String, @Size(max = 4000, message = "Each section must be at most 4000 characters") String> sections
) {
}
