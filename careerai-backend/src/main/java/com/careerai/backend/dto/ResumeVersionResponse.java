package com.careerai.backend.dto;

import com.careerai.backend.entity.ResumeVersion;

import java.time.LocalDateTime;

/** One stored snapshot of a resume. content is the builder JSON (null for uploaded resumes). */
public record ResumeVersionResponse(
    Long id,
    Long resumeId,
    int versionNumber,
    String versionName,
    int wordCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    ResumeContent content,
    String extractedText
) {

    public static ResumeVersionResponse from(ResumeVersion version, Long resumeId, ResumeContent content) {
        return new ResumeVersionResponse(
            version.getId(),
            resumeId,
            version.getVersionNumber(),
            version.getVersionName(),
            ResumeResponse.wordCount(version.getExtractedText()),
            version.getCreatedAt(),
            version.getUpdatedAt(),
            content,
            version.getExtractedText()
        );
    }
}
