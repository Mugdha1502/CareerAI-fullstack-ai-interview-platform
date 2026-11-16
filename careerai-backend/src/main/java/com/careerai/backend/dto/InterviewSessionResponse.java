package com.careerai.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An interview session with its questions so far. currentQuestion is the unanswered question
 * (null when all asked questions are answered). notice explains when fallback questions are used.
 */
public record InterviewSessionResponse(
    Long id,
    String type,
    String mode,
    String provider,
    String targetRole,
    String difficulty,
    String status,
    int questionCount,
    int answeredCount,
    Long resumeId,
    String resumeTitle,
    boolean hasJobDescription,
    List<String> focusTopics,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    Integer overallScore,
    InterviewQuestionResponse currentQuestion,
    List<InterviewQuestionResponse> questions,
    VoiceContext voiceContext,
    String notice
) {

    /**
     * Compact, sanitised call context for the voice assistant's dynamic variables (VOICE sessions only).
     * Resume and job description appear only as short summaries; no email, ids or tokens.
     */
    public record VoiceContext(
        String candidateName,
        String targetRole,
        String interviewType,
        String difficulty,
        String experienceLevel,
        String focusTopics,
        String resumeContext,
        String jobDescriptionContext,
        int questionCount
    ) {
    }
}
