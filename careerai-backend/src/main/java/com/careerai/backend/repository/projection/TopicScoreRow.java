package com.careerai.backend.repository.projection;

import java.time.LocalDateTime;

/** Average accuracy per (module, topic) for one user. */
public record TopicScoreRow(
    String module,
    String topic,
    Double averageAccuracy,
    Long results,
    LocalDateTime lastPracticedAt
) {
}
