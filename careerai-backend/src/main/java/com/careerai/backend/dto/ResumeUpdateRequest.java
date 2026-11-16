package com.careerai.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/resumes/{id}: rename and/or replace builder content. Null fields are left unchanged;
 * content can only be changed on BUILDER resumes.
 */
public record ResumeUpdateRequest(
    @Size(max = 120, message = "Resume title must be at most 120 characters")
    String title,

    @Valid
    ResumeContent content
) {
}
