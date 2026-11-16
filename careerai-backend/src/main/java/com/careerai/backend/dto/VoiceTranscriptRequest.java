package com.careerai.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/interviews/{id}/voice-transcript: the final transcript of a voice call.
 * Only roles and text are accepted; scores and evaluations are always computed on the server.
 * Entries with other roles or blank text are ignored; total size is also capped in the service.
 */
public record VoiceTranscriptRequest(
    @NotBlank(message = "Provider is required")
    @Pattern(regexp = "^(?i)VAPI$", message = "Provider must be VAPI")
    String provider,

    @Size(max = 120, message = "Provider call id is too long")
    @Pattern(regexp = "^[A-Za-z0-9._:-]*$", message = "Provider call id contains invalid characters")
    String providerCallId,

    @NotNull(message = "Transcript is required")
    @Size(max = 400, message = "Transcript has too many entries")
    List<@Valid Entry> transcript
) {

    public record Entry(
        @Size(max = 20, message = "Role is too long")
        String role,

        @Size(max = 4000, message = "A transcript entry is too long")
        String text,

        @Size(max = 40, message = "Timestamp is too long")
        String timestamp
    ) {
    }
}
