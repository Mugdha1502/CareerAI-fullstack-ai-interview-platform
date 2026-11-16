package com.careerai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/practice/coding/submit — evaluates against the question's hidden tests and saves a
 * PracticeResult. sessionId (optional) is the caller's own practice session started for this
 * attempt; the user always comes from the JWT.
 */
public record CodeSubmitRequest(
    @NotBlank(message = "Question id is required")
    @Size(max = 100, message = "Question id is invalid")
    String questionId,

    @NotBlank(message = "Language is required")
    String language,

    @NotBlank(message = "Source code is required")
    @Size(max = 51200, message = "Source code must be at most 50 KB")
    String sourceCode,

    Long sessionId
) {
}
