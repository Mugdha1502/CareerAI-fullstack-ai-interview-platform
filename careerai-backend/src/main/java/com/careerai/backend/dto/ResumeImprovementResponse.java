package com.careerai.backend.dto;

import java.util.List;

/**
 * Result of a resume-wording request.
 * engine GEMINI (aiConnected = true, label "AI-assisted improvement") when the AI rewrite
 * passed validation and the factual-safety checks; otherwise BASIC_RULES ("Basic wording
 * assistance") with fallbackReason explaining why AI was not used.
 * Facts are never added or changed by either engine.
 */
public record ResumeImprovementResponse(
    String engine,
    boolean aiConnected,
    String label,
    String message,
    String fallbackReason,
    Long resumeId,
    String section,
    String originalText,
    String improvedText,
    List<Change> changes,
    List<String> warnings,
    List<String> suggestions
) {

    /** type: CLARITY, GRAMMAR, WORDING, ACTION_VERB, CONCISENESS, STRUCTURE (or FORMATTING for basic rules). */
    public record Change(String type, String description) {
    }
}
