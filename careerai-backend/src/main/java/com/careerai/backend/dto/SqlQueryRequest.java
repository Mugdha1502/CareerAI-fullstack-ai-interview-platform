package com.careerai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/practice/sql/execute — "Run Query" in a disposable sandbox (never scored or saved). */
public record SqlQueryRequest(
    @NotBlank(message = "Question id is required")
    @Size(max = 100, message = "Question id is invalid")
    String questionId,

    @NotBlank(message = "Query is required")
    @Size(max = 20480, message = "Query must be at most 20 KB")
    String query
) {
}
