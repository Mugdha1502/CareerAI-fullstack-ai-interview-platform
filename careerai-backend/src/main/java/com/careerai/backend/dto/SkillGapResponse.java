package com.careerai.backend.dto;

import java.util.List;

/**
 * GET /api/skill-gap. readiness averages only assessed skills and is null when
 * none are assessed; assessedSkillCount / totalRequiredSkillCount show coverage.
 */
public record SkillGapResponse(
    String targetRole,
    String trackId,
    boolean targetRoleSelected,
    Integer readiness,
    int assessedSkillCount,
    int totalRequiredSkillCount,
    List<String> strongSkills,
    List<String> prioritySkills,
    List<String> unassessedSkills,
    List<SkillGapItemResponse> skills,
    String recommendedNextSkill,
    String recommendedNextRoute,
    String recommendedNextReason
) {
}
