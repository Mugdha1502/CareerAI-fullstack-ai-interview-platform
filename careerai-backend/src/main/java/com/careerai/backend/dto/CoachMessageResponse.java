package com.careerai.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One stored Coach message. role USER or ASSISTANT; source AI or FALLBACK for assistant messages.
 */
public record CoachMessageResponse(
    Long id,
    String role,
    String content,
    String source,
    List<SuggestedAction> suggestedActions,
    LocalDateTime createdAt
) {

    /** A button that opens a CareerAI page (routes are checked against an allow-list). */
    public record SuggestedAction(String label, String route) {
    }
}
