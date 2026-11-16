package com.careerai.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * Create (name optional, defaults to "Version N") or rename (name required) a resume version.
 */
public record ResumeVersionRequest(
    @Size(max = 120, message = "Version name must be at most 120 characters")
    String versionName
) {
}
