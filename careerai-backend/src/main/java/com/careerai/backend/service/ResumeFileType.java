package com.careerai.backend.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Accepted resume formats. Each is checked by extension, declared MIME type and file signature. */
public enum ResumeFileType {

    PDF("pdf", "application/pdf"),
    DOC("doc", "application/msword"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    /** Browsers sometimes send these for valid documents; the file signature is checked instead. */
    private static final Set<String> GENERIC_MIME_TYPES = Set.of("application/octet-stream", "binary/octet-stream");

    private static final byte[] OLE2_SIGNATURE = {
        (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1
    };

    private final String extension;
    private final String mimeType;

    ResumeFileType(String extension, String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public static Optional<ResumeFileType> fromExtension(String extension) {
        String normalized = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.extension.equals(normalized)).findFirst();
    }

    /** Missing/generic MIME types are allowed; a specific but different one is not. */
    public boolean acceptsMimeType(String declared) {
        if (declared == null || declared.isBlank()) return true;
        String normalized = declared.toLowerCase(Locale.ROOT).split(";")[0].trim();
        return normalized.equals(mimeType) || GENERIC_MIME_TYPES.contains(normalized);
    }

    /** True when the leading bytes look like this format (PDF header, ZIP for DOCX, OLE2 for DOC). */
    public boolean matchesSignature(byte[] bytes) {
        return switch (this) {
            case PDF -> containsPdfHeader(bytes);
            case DOCX -> startsWith(bytes, new byte[] { 'P', 'K', 3, 4 });
            case DOC -> startsWith(bytes, OLE2_SIGNATURE);
        };
    }

    /** "%PDF-" must appear within the first 1024 bytes (the PDF spec allows leading junk). */
    private static boolean containsPdfHeader(byte[] bytes) {
        byte[] header = { '%', 'P', 'D', 'F', '-' };
        int limit = Math.min(bytes.length, 1024) - header.length;
        for (int i = 0; i <= limit; i++) {
            boolean match = true;
            for (int j = 0; j < header.length && match; j++) {
                match = bytes[i + j] == header[j];
            }
            if (match) return true;
        }
        return false;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }
}
