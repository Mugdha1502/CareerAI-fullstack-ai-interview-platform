package com.careerai.backend.dto;

import java.util.List;

/**
 * Evaluation of one answer plus the next (adaptive) question, from a single AI call when available.
 * nextQuestion is null when the planned number of questions has been reached (readyToComplete).
 */
public record InterviewAnswerResponse(
    Long sessionId,
    Long questionId,
    int score,
    List<String> strengths,
    List<String> missingPoints,
    String feedback,
    String idealAnswer,
    boolean followUpRequired,
    String source,
    InterviewQuestionResponse nextQuestion,
    int answeredCount,
    int questionCount,
    boolean readyToComplete,
    String notice
) {
}
