package com.careerai.backend.dto;

import java.time.LocalDateTime;

/**
 * GET /api/performance/summary. Category scores are null when that category has
 * not been attempted; overallReadiness averages only the attempted categories.
 */
public record PerformanceSummaryResponse(
    boolean hasData,
    Integer overallReadiness,
    Integer technical,
    Integer aptitude,
    Integer coding,
    Integer sql,
    Integer systemDesign,
    Integer voice,
    int categoriesAttempted,
    long totalSessions,
    long totalQuestions,
    Integer averageAccuracy,
    int currentStreak,
    LocalDateTime lastPracticedAt
) {
}
