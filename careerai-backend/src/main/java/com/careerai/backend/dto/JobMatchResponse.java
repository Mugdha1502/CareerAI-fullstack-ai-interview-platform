package com.careerai.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Job match: objective keyword matching (JobMatchRuleEngine) plus optional AI semantic
 * interpretation. keywordMatchScore is always the rule-based result; when AI feedback is
 * available, overallMatchScore = 60% keyword match + 40% semantic match (see scoringMethod),
 * otherwise overallMatchScore = keywordMatchScore.
 * jobDescription is only included on the detail endpoint.
 */
public record JobMatchResponse(
    Long id,
    Long resumeId,
    String resumeTitle,
    String jobTitle,
    String company,
    String engine,
    String label,
    String disclaimer,
    int overallMatchScore,
    int keywordMatchScore,
    Integer semanticMatchScore,
    String scoringMethod,
    int keywordCoverage,
    Integer skillCoverage,
    List<String> matchedSkills,
    List<String> missingSkills,
    List<String> matchedKeywords,
    List<String> missingKeywords,
    String experienceAlignment,
    String experienceNote,
    String educationAlignment,
    String educationNote,
    List<String> recommendations,
    SemanticFeedback semanticFeedback,
    String jobDescription,
    LocalDateTime createdAt
) {

    public static final String KEYWORD_ONLY_METHOD = "Overall match = keyword-based match (AI semantic feedback unavailable).";
    public static final String COMBINED_METHOD = "Overall match = 60% keyword-based match + 40% AI semantic match.";

    /** status AVAILABLE or UNAVAILABLE (message says why). */
    public record SemanticFeedback(
        String status,
        String message,
        Integer semanticMatch,
        List<String> strengths,
        List<String> gaps,
        List<String> tailoringSuggestions,
        String summary
    ) {

        public static SemanticFeedback unavailable(String message) {
            return new SemanticFeedback("UNAVAILABLE", message, null, List.of(), List.of(), List.of(), null);
        }

        public boolean available() {
            return "AVAILABLE".equals(status);
        }
    }

    /** Fields stored in job_match_analyses.details_json (everything without its own column). */
    public record Details(
        String label,
        String disclaimer,
        Integer skillCoverage,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        String experienceNote,
        String educationNote,
        Integer keywordMatchScore,
        String scoringMethod,
        SemanticFeedback semanticFeedback
    ) {
    }
}
