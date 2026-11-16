package com.careerai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Named snapshot of a resume (e.g. "Original Resume", "Java Developer Version").
 * Ownership comes from the parent Resume; there is no user column of its own.
 */
@Entity
@Table(
    name = "resume_versions",
    uniqueConstraints = @UniqueConstraint(name = "uk_resume_version_number", columnNames = { "resume_id", "version_number" })
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = { "resume", "content", "extractedText" })
public class ResumeVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false, foreignKey = @ForeignKey(name = "fk_resume_versions_resume"))
    private Resume resume;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "version_name", nullable = false, length = 120)
    private String versionName;

    /** Resume Builder JSON at the time of the snapshot (null for uploaded resumes). */
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

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
