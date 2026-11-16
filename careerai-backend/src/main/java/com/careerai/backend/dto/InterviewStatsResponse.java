package com.careerai.backend.dto;

import java.time.LocalDateTime;

/**
 * GET /api/interviews/stats: interview performance as a data source separate from practice results
 * (PracticeResult is not modified). Scores are null until an interview is completed.
 */
public record InterviewStatsResponse(
    int completedCount,
    Integer averageScore,
    Integer bestScore,
    Integer lastScore,
    LocalDateTime lastCompletedAt
) {
}
