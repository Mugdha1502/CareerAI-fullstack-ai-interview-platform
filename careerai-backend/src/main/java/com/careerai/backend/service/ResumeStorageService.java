package com.careerai.backend.service;

import com.careerai.backend.exception.InvalidResumeFileException;
import com.careerai.backend.exception.ResumeFileTooLargeException;
import com.careerai.backend.exception.ResumeNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Stores uploaded resume files on local disk (development storage) under
 * app.storage.resume-dir, one folder per user, with random file names.
 * Files are never executed and never served as static content; the only way to read
 * one back is the authenticated download endpoint, which checks ownership first.
 *
 * TODO (production): swap the disk calls for object storage behind this same API.
 */
@Service
public class ResumeStorageService {

    private static final Logger log = LoggerFactory.getLogger(ResumeStorageService.class);

    /** Rejected anywhere in the name, e.g. "resume.exe.pdf". */
    private static final Set<String> EXECUTABLE_EXTENSIONS = Set.of(
        "exe", "bat", "cmd", "com", "sh", "ps1", "msi", "dll", "jar", "js", "vbs", "scr", "php", "py", "html", "htm", "svg"
    );

    /** A validated upload held in memory (at most a few MB). */
    public record ValidatedUpload(byte[] bytes, ResumeFileType type, String safeFileName) {
    }

    /** Where a file was written; storagePath is relative to the storage root. */
    public record StoredFile(String storedFileName, String storagePath) {
    }

    private final Path root;
    private final long maxBytes;

    public ResumeStorageService(
        @Value("${app.storage.resume-dir:uploads/resumes}") String resumeDir,
        @Value("${app.storage.resume-max-bytes:5242880}") long maxBytes
    ) {
        this.root = Paths.get(resumeDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }

    /** Checks presence, size, name, extension, MIME type and file signature; never trusts the client. */
    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidResumeFileException("Please choose a resume file to upload.");
        }

        if (file.getSize() > maxBytes) {
            throw new ResumeFileTooLargeException(maxBytes);
        }

        String safeName = sanitizeFileName(file.getOriginalFilename());
        String extension = extensionOf(safeName);

        ResumeFileType type = ResumeFileType.fromExtension(extension)
            .orElseThrow(() -> new InvalidResumeFileException("Unsupported file type. Please upload a PDF, DOC or DOCX resume."));

        boolean executableInside = Arrays.stream(safeName.toLowerCase(Locale.ROOT).split("\\."))
            .skip(1)
            .anyMatch(EXECUTABLE_EXTENSIONS::contains);
        if (executableInside) {
            throw new InvalidResumeFileException("This file name is not allowed. Please rename the file and upload a PDF, DOC or DOCX resume.");
        }

        if (!type.acceptsMimeType(file.getContentType())) {
            throw new InvalidResumeFileException("The file content type does not match a " + type.name() + " document.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidResumeFileException("The uploaded file could not be read. Please try again.");
        }

        if (bytes.length > maxBytes) {
            throw new ResumeFileTooLargeException(maxBytes);
        }

        if (!type.matchesSignature(bytes)) {
            throw new InvalidResumeFileException("This file is not a valid " + type.name() + " document.");
        }

        return new ValidatedUpload(bytes, type, safeName);
    }

    /** Writes the file as {userId}/{random}.{ext}; the client-supplied name is never part of the path. */
    public StoredFile store(Long userId, ValidatedUpload upload) {
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + upload.type().extension();
        String storagePath = userId + "/" + storedName;
        Path target = resolve(storagePath);

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, upload.bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store resume file", ex);
        }

        return new StoredFile(storedName, storagePath);
    }

    public Resource load(String storagePath) {
        Path path = resolve(storagePath);
        if (!Files.isRegularFile(path)) {
            throw new ResumeNotFoundException("Resume file not found");
        }
        return new FileSystemResource(path);
    }

    /** Best effort: a missing file is not an error; failures are logged without the full path. */
    public void delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not delete stored resume file {}: {}", storagePath, ex.getMessage());
        }
    }

    /** Resolves a stored relative path and refuses anything that escapes the storage root. */
    private Path resolve(String storagePath) {
        Path path = root.resolve(storagePath).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return path;
    }

    /**
     * Display-safe file name: last path segment only (no "../" or folders), control and
     * unusual characters replaced, length limited. Used for display and downloads only.
     */
    public static String sanitizeFileName(String raw) {
        String name = raw == null ? "" : raw;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "")
            .replaceAll("[^A-Za-z0-9 ._()\\-]", "_")
            .replaceAll("\\.{2,}", ".")
            .replaceAll("\\s+", " ")
            .strip();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.length() > 120) {
            String extension = extensionOf(name);
            name = name.substring(0, 115 - extension.length()) + "." + extension;
        }
        return name.isBlank() ? "resume" : name;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
