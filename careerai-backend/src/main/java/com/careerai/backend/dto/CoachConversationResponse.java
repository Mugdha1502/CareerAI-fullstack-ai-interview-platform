package com.careerai.backend.dto;

import java.time.LocalDateTime;

/** Conversation summary (list) or header (detail). */
public record CoachConversationResponse(
    Long id,
    String title,
    long messageCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
