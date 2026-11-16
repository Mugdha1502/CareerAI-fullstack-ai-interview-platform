package com.careerai.backend.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ATS readiness result: rule-based checks plus optional AI semantic feedback.
 * The same structure is stored as resultJson; id, resumeId, resumeTitle and analyzedAt are
 * filled when it is returned from the database.
 *
 * Scores: ruleBasedScore comes only from AtsRuleEngine. overallScore equals ruleBasedScore,
 * or, when AI feedback is available, 80% ruleBasedScore + 20% the AI writing-quality score
 * (see scoringMethod). The AI never sets the objective checks.
 * It does not reproduce any specific company's applicant tracking system.
 */
public record ResumeAnalysisResponse(
    Long id,
    Long resumeId,
    String resumeTitle,
    String analysisType,
    String engine,
    String label,
    String disclaimer,
    int overallScore,
    int ruleBasedScore,
    String scoringMethod,
    int keywordScore,
    int formattingScore,
    int contentScore,
    int experienceScore,
    Map<String, Boolean> sections,
    int wordCount,
    List<String> detectedSkills,
    List<String> repeatedKeywords,
    List<Check> checks,
    List<String> strengths,
    List<String> issues,
    List<String> recommendations,
    SemanticFeedback semanticFeedback,
    LocalDateTime analyzedAt
) {

    public static final String RULE_ONLY_METHOD = "Overall score = rule-based score (AI feedback unavailable).";
    public static final String COMBINED_METHOD = "Overall score = 80% rule-based checks + 20% AI writing-quality score.";

    /** One transparent rule: what was checked, whether it passed, and why. */
    public record Check(String id, String category, String label, boolean passed, String detail) {
    }

    /** status AVAILABLE (AI feedback present) or UNAVAILABLE (message says why). */
    public record SemanticFeedback(
        String status,
        String message,
        Integer writingQualityScore,
        List<String> strengths,
        List<String> issues,
        List<String> recommendations,
        String summary
    ) {

        public static SemanticFeedback unavailable(String message) {
            return new SemanticFeedback("UNAVAILABLE", message, null, List.of(), List.of(), List.of(), null);
        }

        public boolean available() {
            return "AVAILABLE".equals(status);
        }
    }

    /** Adds the semantic layer and recomputes overallScore with the documented weighting. */
    public ResumeAnalysisResponse withSemantic(SemanticFeedback feedback, String engineName) {
        boolean combined = feedback != null && feedback.available() && feedback.writingQualityScore() != null;
        int overall = combined
            ? (int) Math.round(ruleBasedScore * 0.8 + feedback.writingQualityScore() * 0.2)
            : ruleBasedScore;
        return new ResumeAnalysisResponse(
            id, resumeId, resumeTitle, analysisType, engineName, label, disclaimer,
            overall, ruleBasedScore, combined ? COMBINED_METHOD : RULE_ONLY_METHOD,
            keywordScore, formattingScore, contentScore, experienceScore,
            sections, wordCount, detectedSkills, repeatedKeywords, checks,
            strengths, issues, recommendations, feedback, analyzedAt
        );
    }

    public ResumeAnalysisResponse withMetadata(Long id, Long resumeId, String resumeTitle, LocalDateTime analyzedAt) {
        return new ResumeAnalysisResponse(
            id, resumeId, resumeTitle, analysisType, engine, label, disclaimer,
            overallScore, ruleBasedScore, scoringMethod,
            keywordScore, formattingScore, contentScore, experienceScore,
            sections, wordCount, detectedSkills, repeatedKeywords, checks,
            strengths, issues, recommendations, semanticFeedback, analyzedAt
        );
    }
}
