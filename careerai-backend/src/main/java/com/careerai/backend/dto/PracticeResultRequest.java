package com.careerai.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Saves the result of a session (POST /api/practice/results).
 * Accuracy, duration and XP are computed by the server, not sent by the client.
 */
@Getter
@Setter
public class PracticeResultRequest {

    @NotNull(message = "sessionId is required")
    private Long sessionId;

    @NotNull(message = "Score is required")
    @Min(value = 0, message = "Score cannot be negative")
    @Max(value = 1000, message = "Score is too large")
    private Integer score;

    @NotNull(message = "Total questions is required")
    @Min(value = 1, message = "Total questions must be at least 1")
    @Max(value = 1000, message = "Total questions is too large")
    private Integer totalQuestions;

    @NotNull(message = "Correct answers is required")
    @Min(value = 0, message = "Correct answers cannot be negative")
    private Integer correctAnswers;

    /** Optional; defaults to totalQuestions - correctAnswers. */
    @Min(value = 0, message = "Incorrect answers cannot be negative")
    private Integer incorrectAnswers;
}
