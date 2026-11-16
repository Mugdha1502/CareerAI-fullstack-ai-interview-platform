package com.careerai.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/** One interview question; evaluation is null until it has been answered. source: AI or FALLBACK. */
public record InterviewQuestionResponse(
    Long id,
    int sequenceNumber,
    String question,
    String questionType,
    String topic,
    String difficulty,
    String intent,
    String source,
    Evaluation evaluation
) {

    /** The candidate's answer with its evaluation. source: AI or FALLBACK (key-point matching). */
    public record Evaluation(
        String answer,
        int score,
        List<String> strengths,
        List<String> missingPoints,
        String feedback,
        String idealAnswer,
        boolean followUpRequired,
        String source,
        LocalDateTime answeredAt
    ) {
    }
}
