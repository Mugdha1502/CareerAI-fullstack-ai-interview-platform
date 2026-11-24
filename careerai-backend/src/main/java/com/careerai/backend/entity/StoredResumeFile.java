package com.careerai.backend.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Resume bytes for the "database" storage provider (app.storage.resume-provider=database).
 * Keyed by the same storage_path that resumes.storage_path holds. Files are at most a few MB.
 */
@Entity
@Table(name = "resume_file_blobs")
@Getter
@Setter
@NoArgsConstructor
public class StoredResumeFile {

    @Id
    @Column(name = "storage_path", length = 255)
    private String storagePath;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "content", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] content;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
