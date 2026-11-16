package com.careerai.backend.dto;

import java.time.LocalDateTime;

/** One row of GET /api/interviews/history. */
public record InterviewSummaryResponse(
    Long id,
    String type,
    String mode,
    String provider,
    String targetRole,
    String difficulty,
    String status,
    int questionCount,
    Integer overallScore,
    LocalDateTime startedAt,
    LocalDateTime completedAt
) {
}
