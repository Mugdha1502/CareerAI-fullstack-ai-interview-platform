package com.careerai.backend.repository.projection;

import java.time.LocalDate;

/** Number of saved results (scored or not) on one day. */
public record DailyActivityRow(LocalDate date, Long results) {
}
