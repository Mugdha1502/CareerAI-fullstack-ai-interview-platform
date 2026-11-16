package com.careerai.backend.dto;

import java.util.List;

/**
 * Reply to one Coach message. source AI (Gemini) or FALLBACK (deterministic advice from SkillGap,
 * My Plan and Performance when the AI is unavailable); notice explains a fallback.
 */
public record CoachReplyResponse(
    CoachConversationResponse conversation,
    CoachMessageResponse userMessage,
    CoachMessageResponse reply,
    String message,
    List<CoachMessageResponse.SuggestedAction> suggestedActions,
    boolean usedPersonalization,
    String source,
    String notice
) {
}
