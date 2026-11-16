package com.careerai.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Final report of a text interview.
 * overallScore = average of answered-question scores; technicalScore = average of technical questions
 * (null when none). writtenCommunicationScore rates the clarity of typed answers and is only set when
 * the AI wrote the report; voice, tone or confidence are never scored in a text interview.
 * roleReadiness: READY (>= 75), DEVELOPING (>= 55) or NOT_YET.
 */
public record InterviewReportResponse(
    Long sessionId,
    String type,
    String mode,
    String targetRole,
    String difficulty,
    String resumeTitle,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String source,
    int overallScore,
    Integer technicalScore,
    Integer writtenCommunicationScore,
    String roleReadiness,
    int answeredCount,
    int questionCount,
    List<String> strongAreas,
    List<String> weakAreas,
    List<RecommendedSkill> recommendedSkills,
    List<QuestionResult> questionResults,
    String summary,
    List<String> nextSteps,
    String notice
) {

    public record RecommendedSkill(String name, String practiceRoute) {
    }

    public record QuestionResult(
        int sequenceNumber,
        String question,
        String questionType,
        String topic,
        String answer,
        int score,
        String feedback,
        List<String> strengths,
        List<String> missingPoints,
        String idealAnswer,
        String source
    ) {
    }
}
