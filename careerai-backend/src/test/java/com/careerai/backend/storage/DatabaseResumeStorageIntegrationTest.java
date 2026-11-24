package com.careerai.backend.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** Deployment provider: bytes in the resume_file_blobs table, with read access to older local files. */
@SpringBootTest(properties = "app.storage.resume-provider=database")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatabaseResumeStorageIntegrationTest extends ResumeStorageFlowTestBase {

    @TempDir
    static Path legacyRoot;

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.resume-dir", () -> legacyRoot.toString());
    }

    @Override
    void assertStored(Upload upload, boolean present) {
        assertThat(store.name()).isEqualTo("database");
        boolean found = blobs.findAll().stream().anyMatch(blob -> Arrays.equals(blob.getContent(), upload.bytes()));
        assertThat(found).isEqualTo(present);
    }

    @Test
    void nothingIsWrittenToDiskWithTheDatabaseProvider() throws Exception {
        String token = register();
        upload(token);
        try (var files = Files.walk(legacyRoot)) {
            assertThat(files.filter(Files::isRegularFile).count()).isZero();
        }
    }

    @Test
    void filesUploadedBeforeSwitchingProvidersStayReadable() throws Exception {
        String path = "77/" + "fedcba9876543210".repeat(2) + ".pdf";
        Path legacy = legacyRoot.resolve(path);
        Files.createDirectories(legacy.getParent());
        byte[] bytes = pdf("Legacy resume");
        Files.write(legacy, bytes);
        assertThat(store.read(path).getContentAsByteArray()).isEqualTo(bytes);
        store.delete(path);
        assertThat(Files.exists(legacy)).isFalse();
    }
}
