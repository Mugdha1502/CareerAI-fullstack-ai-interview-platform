package com.careerai.backend.dto;

/**
 * Result of ingesting a voice transcript.
 * status COMPLETED: report contains the evaluation (AI, or FALLBACK when the AI was unavailable).
 * status CANCELLED: the call had no usable answers, so no score or report was created.
 * Repeated submissions return the same result without creating new rows.
 */
public record VoiceInterviewResultResponse(
    Long sessionId,
    String status,
    String message,
    InterviewReportResponse report
) {
}
