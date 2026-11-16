package com.careerai.backend.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/resumes/improve.
 * Give text, or a resumeId with section FULL_RESUME to improve the stored resume text.
 * resumeId, when present, must belong to the caller.
 */
public record ResumeImprovementRequest(
    Long resumeId,

    @Size(max = 15000, message = "Text must be at most 15000 characters")
    String text,

    @Pattern(
        regexp = "^(SUMMARY|EXPERIENCE|PROJECT|PROJECTS|SKILLS|EDUCATION|FULL_RESUME|GENERAL)$",
        message = "Section must be SUMMARY, EXPERIENCE, PROJECT, SKILLS, EDUCATION, FULL_RESUME or GENERAL"
    )
    String section
) {
}
