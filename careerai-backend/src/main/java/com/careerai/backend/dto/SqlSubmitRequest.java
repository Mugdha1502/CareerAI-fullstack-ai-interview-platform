package com.careerai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/practice/sql/submit — compares the query's RESULT with the reference result on the
 * visible data and on hidden extra rows, then saves a PracticeResult. The user comes from the JWT.
 */
public record SqlSubmitRequest(
    @NotBlank(message = "Question id is required")
    @Size(max = 100, message = "Question id is invalid")
    String questionId,

    @NotBlank(message = "Query is required")
    @Size(max = 20480, message = "Query must be at most 20 KB")
    String query,

    Long sessionId
) {
}
