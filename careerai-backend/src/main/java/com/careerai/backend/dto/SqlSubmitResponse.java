package com.careerai.backend.dto;

/**
 * Result of "Submit Query". status: CORRECT, PARTIAL (right on the visible data only),
 * INCORRECT, or ERROR (the query failed; errorMessage explains why). The reference query and
 * hidden rows are never returned; output is the submitted query's result on the visible data.
 */
public record SqlSubmitResponse(
    String status,
    int passedChecks,
    int totalChecks,
    int score,
    String message,
    String errorMessage,
    SqlQueryResponse output,
    PracticeHistoryItem result
) {
}
