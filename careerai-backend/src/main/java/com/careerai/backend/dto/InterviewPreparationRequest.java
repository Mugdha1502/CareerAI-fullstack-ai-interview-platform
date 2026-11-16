package com.careerai.backend.dto;

import jakarta.validation.constraints.Size;

/** POST /api/resumes/{id}/interview-preparation. The job description is optional. */
public record InterviewPreparationRequest(
    @Size(max = 120, message = "Target role must be at most 120 characters")
    String targetRole,

    @Size(max = 20000, message = "Job description must be at most 20000 characters")
    String jobDescription
) {
}
