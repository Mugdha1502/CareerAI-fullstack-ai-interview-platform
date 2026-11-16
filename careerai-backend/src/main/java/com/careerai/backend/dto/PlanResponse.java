package com.careerai.backend.dto;

import java.time.LocalDate;
import java.util.List;

/** GET /api/plan/me: today's personalized plan, generated from MySQL data with deterministic rules. */
public record PlanResponse(
    LocalDate date,
    String targetRole,
    String trackId,
    boolean targetRoleSelected,
    Integer readiness,
    int assessedSkillCount,
    int totalRequiredSkillCount,
    int dailyPreparationMinutes,
    int plannedMinutes,
    String preferredDifficulty,
    List<PlanTaskResponse> tasks,
    int completedTasks,
    int totalTasks,
    List<SkillGapItemResponse> prioritySkills,
    PlanTaskResponse recommendedNextAction,
    List<PlanDayResponse> weeklyProgress,
    int currentStreak
) {
}
