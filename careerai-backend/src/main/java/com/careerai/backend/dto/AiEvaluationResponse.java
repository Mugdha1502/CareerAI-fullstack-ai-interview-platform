package com.careerai.backend.dto;

import java.util.List;

/** Validated AI evaluation of a descriptive answer; score is always 0-100. */
public record AiEvaluationResponse(
    String source,
    String provider,
    String model,
    int score,
    List<String> strengths,
    List<String> missingPoints,
    String feedback,
    String suggestedAnswer
) {
}
