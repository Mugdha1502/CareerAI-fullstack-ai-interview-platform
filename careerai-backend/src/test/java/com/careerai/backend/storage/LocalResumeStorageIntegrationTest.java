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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Default provider: files under a (temporary) local folder. */
@SpringBootTest(properties = "app.storage.resume-provider=local")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalResumeStorageIntegrationTest extends ResumeStorageFlowTestBase {

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.resume-dir", () -> root.toString());
    }

    @Override
    void assertStored(Upload upload, boolean present) throws Exception {
        assertThat(store.name()).isEqualTo("local");
        try (Stream<Path> files = Files.walk(root)) {
            boolean found = files.filter(Files::isRegularFile).anyMatch(file -> {
                try {
                    return Arrays.equals(Files.readAllBytes(file), upload.bytes());
                } catch (Exception ex) {
                    return false;
                }
            });
            assertThat(found).isEqualTo(present);
        }
    }

    @Test
    void localStoreNeverLeavesItsRoot() {
        LocalResumeFileStore local = new LocalResumeFileStore(root.toString());
        assertThatThrownBy(() -> local.read("../outside.pdf")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> local.write("../../outside.pdf", new byte[] { 1 })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> local.delete("..")).isInstanceOf(IllegalArgumentException.class);
    }
}
