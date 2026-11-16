package com.careerai.backend.dto;

import java.util.List;

/** GET /api/coach/conversations/{id}: the conversation with its messages (oldest first). */
public record CoachHistoryResponse(
    CoachConversationResponse conversation,
    List<CoachMessageResponse> messages
) {
}
