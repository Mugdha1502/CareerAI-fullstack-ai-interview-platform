package com.careerai.backend.service;

import java.time.LocalDateTime;

/** A measured score (0–100), how many results it is based on, and when it was last practised. */
public record ScoreStat(int score, long attempts, LocalDateTime lastPracticedAt) {
}
