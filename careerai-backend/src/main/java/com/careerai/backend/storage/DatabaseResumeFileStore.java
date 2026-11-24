package com.careerai.backend.storage;

import com.careerai.backend.entity.StoredResumeFile;
import com.careerai.backend.exception.ResumeNotFoundException;
import com.careerai.backend.repository.StoredResumeFileRepository;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

/**
 * Resume bytes in the application database (table resume_file_blobs). Survives redeploys on hosts
 * with an ephemeral filesystem, with no extra storage service. Writes join the caller's transaction,
 * so a failed upload leaves no row behind.
 *
 * Files uploaded earlier to local disk stay readable through the optional local fallback, so
 * switching providers does not break existing resumes.storage_path references.
 */
public class DatabaseResumeFileStore implements ResumeFileStore {

    private final StoredResumeFileRepository repository;
    private final LocalResumeFileStore legacyFiles; // may be null
    private final Clock clock;
    private final TransactionTemplate ownTransaction;

    public DatabaseResumeFileStore(StoredResumeFileRepository repository, LocalResumeFileStore legacyFiles, Clock clock,
                                   PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.legacyFiles = legacyFiles;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void write(String storagePath, byte[] bytes) {
        if (repository.existsById(storagePath)) {
            throw new IllegalStateException("Resume file already exists");
        }
        StoredResumeFile file = new StoredResumeFile();
        file.setStoragePath(storagePath);
        file.setContent(bytes);
        file.setSizeBytes(bytes.length);
        file.setCreatedAt(Instant.now(clock));
        repository.saveAndFlush(file);
    }

    @Override
    public Resource read(String storagePath) {
        return repository.findById(storagePath)
            .<Resource>map(file -> new ByteArrayResource(file.getContent()))
            .orElseGet(() -> {
                if (legacyFiles != null && legacyFiles.exists(storagePath)) return legacyFiles.read(storagePath);
                throw new ResumeNotFoundException("Resume file not found");
            });
    }

    /**
     * Runs in its own transaction: resume deletion calls this after the outer transaction has
     * committed, where work joining that finished transaction would never be committed.
     */
    @Override
    public void delete(String storagePath) {
        ownTransaction.executeWithoutResult(status -> repository.deleteById(storagePath));
        if (legacyFiles != null) legacyFiles.delete(storagePath);
    }

    @Override
    public String name() {
        return "database";
    }
}
