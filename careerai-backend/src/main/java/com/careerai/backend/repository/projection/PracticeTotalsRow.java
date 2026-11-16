package com.careerai.backend.repository.projection;

/** Totals over a user's scored results. Sums and averages are null when there are none. */
public record PracticeTotalsRow(Long results, Long questions, Double averageAccuracy) {
}
