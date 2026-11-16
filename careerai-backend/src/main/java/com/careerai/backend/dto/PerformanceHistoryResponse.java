package com.careerai.backend.dto;

import java.time.LocalDate;

/** One day of GET /api/performance/history (chronological, for the readiness line chart). */
public record PerformanceHistoryResponse(LocalDate date, Integer averageScore, long sessions) {
}
