package com.careerai.backend.dto;

import java.time.LocalDateTime;

/** Average performance for one practised topic (GET /api/performance/skills). */
public record SkillPerformanceResponse(
    String skill,
    String module,
    Integer score,
    long attempts,
    String status,
    LocalDateTime lastPracticedAt
) {
}
