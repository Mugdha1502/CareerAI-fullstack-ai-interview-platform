package com.careerai.backend.storage;

import com.careerai.backend.repository.StoredResumeFileRepository;
import com.careerai.backend.service.ResumeStorageService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Upload → owner-only download → delete through the real endpoints. Run for each storage provider
 * (temporary folder / in-memory test database only — nothing leaves the machine).
 */
abstract class ResumeStorageFlowTestBase {

    static final ObjectMapper JSON = JsonMapper.builder().build();

    static byte[] pdf(String... lines) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.setLeading(14);
                content.newLineAtOffset(50, 750);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }



    @Autowired
    MockMvc mvc;

    @Autowired
    ResumeFileStore store;

    @Autowired
    StoredResumeFileRepository blobs;

    record Upload(long id, byte[] bytes) {
    }

    String register() throws Exception {
        String email = "storage-" + System.nanoTime() + "@example.test";
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("name", "Storage Tester", "email", email, "password", "secret123", "confirmPassword", "secret123")))).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).path("token").asString();
    }

    Upload upload(String token) throws Exception {
        byte[] bytes = pdf("Asha Test", "Java Developer", "Skills: Java, Spring Boot, MySQL, REST APIs, Git",
            "Projects: CareerAI built with React and Spring Boot", "Education: B.Tech Computer Science 2026");
        MvcResult result = mvc.perform(multipart("/api/resumes/upload")
            .file(new MockMultipartFile("file", "../../etc/passwd/resume.pdf", "application/pdf", bytes))
            .header("Authorization", "Bearer " + token)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(body.toString()).doesNotContain("storagePath").doesNotContain("passwd/");
        return new Upload(body.path("id").asLong(), bytes);
    }

    int status(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    abstract void assertStored(Upload upload, boolean present) throws Exception;

    @Test
    void uploadedFileIsDownloadableByItsOwnerOnlyAndDeletedWithTheResume() throws Exception {
        String owner = register();
        String stranger = register();
        Upload upload = upload(owner);
        assertStored(upload, true);

        MvcResult download = mvc.perform(get("/api/resumes/" + upload.id() + "/file").header("Authorization", "Bearer " + owner)).andReturn();
        assertThat(download.getResponse().getStatus()).isEqualTo(200);
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(upload.bytes());
        assertThat(download.getResponse().getHeader("Content-Disposition")).contains("attachment").doesNotContain("..");
        assertThat(download.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");

        assertThat(status(get("/api/resumes/" + upload.id() + "/file").header("Authorization", "Bearer " + stranger))).isEqualTo(404);
        assertThat(status(get("/api/resumes/" + upload.id() + "/file"))).isEqualTo(401);
        assertThat(status(delete("/api/resumes/" + upload.id()).header("Authorization", "Bearer " + stranger))).isEqualTo(404);
        assertStored(upload, true);

        assertThat(status(delete("/api/resumes/" + upload.id()).header("Authorization", "Bearer " + owner))).isEqualTo(204);
        assertStored(upload, false);
    }

    @Test
    void invalidFilesAreRejectedBeforeAnythingIsStored() throws Exception {
        String token = register();
        long before = blobs.count();
        for (MockMultipartFile file : new MockMultipartFile[] {
            new MockMultipartFile("file", "resume.pdf", "application/pdf", "MZ this is not a pdf".getBytes()),
            new MockMultipartFile("file", "resume.exe.pdf", "application/pdf", pdf("Java developer")),
            new MockMultipartFile("file", "resume.txt", "text/plain", "plain text".getBytes()),
            new MockMultipartFile("file", "resume.pdf", "text/html", pdf("Java developer")),
            new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[0])
        }) {
            int status = status(multipart("/api/resumes/upload").file(file).header("Authorization", "Bearer " + token));
            assertThat(status).as(file.getOriginalFilename() + " " + file.getContentType()).isEqualTo(400);
        }
        assertThat(blobs.count()).isEqualTo(before);
    }

    @Test
    void storesRefuseTraversalPaths() {
        for (String path : new String[] { "../secret.pdf", "1/../../secret.pdf", "/etc/passwd", "1/abc.pdf", "1\\..\\x.pdf", "1/" + "a".repeat(32) + ".exe" }) {
            assertThat(ResumeStorageService.isValidStoragePath(path)).as(path).isFalse();
        }
        assertThat(ResumeStorageService.isValidStoragePath("12/" + "0123456789abcdef".repeat(2) + ".pdf")).isTrue();
    }
}
