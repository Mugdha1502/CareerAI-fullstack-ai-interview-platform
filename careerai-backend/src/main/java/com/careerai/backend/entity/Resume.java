package com.careerai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * A resume owned by one user: either an uploaded file (text extracted on upload)
 * or a Resume Builder document (structured JSON content).
 * File-system details (stored name, storage path) are never sent to the client.
 */
@Entity
@Table(
    name = "resumes",
    indexes = @Index(name = "idx_resumes_user_updated", columnList = "user_id, updated_at")
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = { "user", "extractedText", "content" })
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_resumes_user"))
    private User user;

    @Column(nullable = false, length = 120)
    private String title;

    /** Sanitized display name of the uploaded file (UPLOADED only). */
    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    /** Random server-side file name, e.g. "3f2a...e1.pdf" (UPLOADED only). */
    @Column(name = "stored_file_name", length = 80)
    private String storedFileName;

    /** PDF, DOC or DOCX (UPLOADED only). */
    @Column(name = "file_type", length = 10)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    /** Path relative to app.storage.resume-dir (UPLOADED only). */
    @Column(name = "storage_path", length = 255)
    private String storagePath;

    /** Plain text used by ATS analysis, job matching and interview preparation. */
    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

    /** Resume Builder JSON (BUILDER only). */
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private ResumeSourceType sourceType;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
