package com.careerai.backend.dto;

import jakarta.validation.constraints.Size;

/** POST /api/coach/conversations (title optional). */
public record CoachConversationRequest(
    @Size(max = 120, message = "Title must be at most 120 characters")
    String title
) {
}
