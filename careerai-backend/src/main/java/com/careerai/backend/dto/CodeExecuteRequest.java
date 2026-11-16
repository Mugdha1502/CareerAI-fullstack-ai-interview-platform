package com.careerai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/practice/coding/execute — "Run Code" with custom stdin (never scored or saved).
 * language: JAVA | PYTHON | CPP | JAVASCRIPT (mapped to provider ids on the server).
 * questionId is optional; when given, matching sample input shows the expected output.
 */
public record CodeExecuteRequest(
    @NotBlank(message = "Language is required")
    String language,

    @NotBlank(message = "Source code is required")
    @Size(max = 51200, message = "Source code must be at most 50 KB")
    String sourceCode,

    @Size(max = 20480, message = "Input must be at most 20 KB")
    String stdin,

    @Size(max = 100, message = "Question id is invalid")
    String questionId
) {
}
