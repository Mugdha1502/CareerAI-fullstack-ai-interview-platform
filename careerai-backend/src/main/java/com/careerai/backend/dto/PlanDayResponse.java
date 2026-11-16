package com.careerai.backend.dto;

import java.time.LocalDate;

/** One day of the current week in GET /api/plan/me. */
public record PlanDayResponse(
    LocalDate date,
    String label,
    long practiceSessions,
    long completedTasks,
    boolean today
) {
}
