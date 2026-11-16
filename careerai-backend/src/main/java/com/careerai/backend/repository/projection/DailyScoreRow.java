package com.careerai.backend.repository.projection;

import java.time.LocalDate;

/** Average accuracy and number of scored results on one day. */
public record DailyScoreRow(LocalDate date, Double averageAccuracy, Long results) {
}
