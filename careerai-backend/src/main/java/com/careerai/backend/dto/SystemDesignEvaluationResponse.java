package com.careerai.backend.dto;

import java.util.List;

/**
 * Validated AI rubric review of a system design. overallScore is the rounded average of
 * the rubric scores (computed on the server, not taken from the model).
 */
public record SystemDesignEvaluationResponse(
    String source,
    String provider,
    String model,
    int overallScore,
    List<RubricScore> rubric,
    List<String> strengths,
    List<String> recommendations,
    String summary
) {

    public record RubricScore(String criterion, String label, int score, String feedback) {
    }
}
