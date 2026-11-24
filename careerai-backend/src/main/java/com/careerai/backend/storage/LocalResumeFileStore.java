package com.careerai.backend.storage;

import com.careerai.backend.exception.ResumeNotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Files on local disk under a root folder (app.storage.resume-dir). Persistent only if that folder
 * is on durable storage (a mounted volume/disk in deployment). Never served as static content.
 */
public class LocalResumeFileStore implements ResumeFileStore {

    private final Path root;

    public LocalResumeFileStore(String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    @Override
    public void write(String storagePath, byte[] bytes) {
        Path target = resolve(storagePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store resume file", ex);
        }
    }

    @Override
    public Resource read(String storagePath) {
        Path path = resolve(storagePath);
        if (!Files.isRegularFile(path)) {
            throw new ResumeNotFoundException("Resume file not found");
        }
        return new FileSystemResource(path);
    }

    /** Lets the database store fall back to files uploaded before it was enabled. */
    boolean exists(String storagePath) {
        return Files.isRegularFile(resolve(storagePath));
    }

    @Override
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not delete resume file", ex);
        }
    }

    @Override
    public String name() {
        return "local";
    }

    /** Second line of defence after path validation: nothing may escape the storage root. */
    private Path resolve(String storagePath) {
        Path path = root.resolve(storagePath).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return path;
    }
}
