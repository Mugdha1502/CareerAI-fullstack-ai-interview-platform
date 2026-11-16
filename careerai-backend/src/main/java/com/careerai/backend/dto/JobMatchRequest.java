package com.careerai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/resumes/{id}/job-match: the user pastes the job description; nothing is scraped. */
public record JobMatchRequest(
    @Size(max = 120, message = "Job title must be at most 120 characters")
    String jobTitle,

    @Size(max = 120, message = "Company must be at most 120 characters")
    String company,

    @NotBlank(message = "Job description is required")
    @Size(max = 20000, message = "Job description must be at most 20000 characters")
    String jobDescription
) {
}
