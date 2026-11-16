package com.careerai.backend.repository.projection;

import java.time.LocalDateTime;

/** Average accuracy and volume per practice module for one user. */
public record ModuleScoreRow(
    String module,
    Double averageAccuracy,
    Long results,
    Long questions,
    LocalDateTime lastPracticedAt
) {
}
