package com.careerai.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * POST /api/avatar/simli/session. The avatar session belongs to one of the caller's own
 * in-progress voice interviews; the user comes from the JWT, never from this body.
 * reason "initial" is allowed once per interview; "retry" is an explicit Retry Avatar.
 * attempt is the client's own counter and is used for logs only.
 */
public record SimliSessionRequest(
    @NotNull(message = "Interview id is required")
    @Positive(message = "Interview id is invalid")
    Long interviewId,

    @Min(value = 1, message = "Attempt is invalid")
    @Max(value = 10, message = "Attempt is invalid")
    Integer attempt,

    @Pattern(regexp = "^(initial|retry)$", message = "Reason must be initial or retry")
    String reason
) {

    public boolean isInitial() {
        return reason == null || "initial".equals(reason);
    }
}
