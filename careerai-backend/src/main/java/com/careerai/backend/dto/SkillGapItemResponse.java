package com.careerai.backend.dto;

import java.time.LocalDateTime;

/**
 * One required skill in a skill gap.
 * score is null unless measured by practice (source PRACTICE).
 * status: STRONG | GOOD | IMPROVE | PRIORITY | SELF_REPORTED | UNASSESSED.
 */
public record SkillGapItemResponse(
    String name,
    Integer score,
    String status,
    String source,
    long attempts,
    String module,
    String topic,
    boolean practicable,
    String practiceRoute,
    LocalDateTime lastPracticedAt
) {
}
