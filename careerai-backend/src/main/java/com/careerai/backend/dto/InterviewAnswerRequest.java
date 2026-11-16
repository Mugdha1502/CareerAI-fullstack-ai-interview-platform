package com.careerai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/interviews/{sessionId}/answers: the typed answer to the current question. */
public record InterviewAnswerRequest(
    @NotNull(message = "Question id is required")
    Long questionId,

    @NotBlank(message = "Answer is required")
    @Size(max = 5000, message = "Answer must be at most 5000 characters")
    String answer
) {
}
