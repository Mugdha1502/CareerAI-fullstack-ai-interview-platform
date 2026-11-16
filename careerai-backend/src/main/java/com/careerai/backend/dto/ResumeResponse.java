package com.careerai.backend.dto;

import com.careerai.backend.entity.Resume;

import java.time.LocalDateTime;

/**
 * Resume as sent to its owner. Server storage details (stored file name, storage path)
 * and the owner's id are never included. content/extractedText are only filled on
 * the detail endpoint.
 */
public record ResumeResponse(
    Long id,
    String title,
    String sourceType,
    String originalFileName,
    String fileType,
    Long fileSize,
    boolean primary,
    int wordCount,
    boolean hasText,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    ResumeContent content,
    String extractedText
) {

    public static ResumeResponse summary(Resume resume) {
        return of(resume, null, false);
    }

    public static ResumeResponse detail(Resume resume, ResumeContent content) {
        return of(resume, content, true);
    }

    private static ResumeResponse of(Resume resume, ResumeContent content, boolean includeText) {
        String text = resume.getExtractedText();
        return new ResumeResponse(
            resume.getId(),
            resume.getTitle(),
            resume.getSourceType().name(),
            resume.getOriginalFileName(),
            resume.getFileType(),
            resume.getFileSize(),
            resume.isPrimary(),
            wordCount(text),
            text != null && !text.isBlank(),
            resume.getCreatedAt(),
            resume.getUpdatedAt(),
            content,
            includeText ? text : null
        );
    }

    public static int wordCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.strip().split("\\s+").length;
    }
}
