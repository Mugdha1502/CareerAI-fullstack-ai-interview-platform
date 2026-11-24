package com.careerai.backend.storage;

import com.careerai.backend.repository.StoredResumeFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.Locale;

/** Chooses the resume store from app.storage.resume-provider (local | database). */
@Configuration
public class ResumeStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(ResumeStorageConfig.class);

    @Bean
    public ResumeFileStore resumeFileStore(
        @Value("${app.storage.resume-provider:local}") String provider,
        @Value("${app.storage.resume-dir:uploads/resumes}") String resumeDir,
        @Value("${app.storage.database.local-fallback:true}") boolean localFallback,
        StoredResumeFileRepository repository,
        Clock clock,
        PlatformTransactionManager transactionManager
    ) {
        ResumeFileStore store = switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "local" -> new LocalResumeFileStore(resumeDir);
            case "database" -> new DatabaseResumeFileStore(repository,
                localFallback ? new LocalResumeFileStore(resumeDir) : null, clock, transactionManager);
            default -> throw new IllegalStateException("app.storage.resume-provider must be 'local' or 'database', not: " + provider);
        };
        log.info("Resume storage provider: {}", store.name());
        return store;
    }
}
