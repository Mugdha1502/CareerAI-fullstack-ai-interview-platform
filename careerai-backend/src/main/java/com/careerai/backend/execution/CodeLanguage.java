package com.careerai.backend.execution;

import java.util.Locale;
import java.util.Optional;

/**
 * Languages the coding workspace supports. The frontend sends these names; provider-specific
 * language ids are resolved on the server only (never trusted from the client).
 */
public enum CodeLanguage {
    JAVA("Java"),
    PYTHON("Python"),
    CPP("C++"),
    JAVASCRIPT("JavaScript");

    private final String label;

    CodeLanguage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<CodeLanguage> parse(String value) {
        if (value == null) return Optional.empty();
        String key = value.trim().toUpperCase(Locale.ROOT).replace("+", "P").replace(" ", "");
        return switch (key) {
            case "JAVA" -> Optional.of(JAVA);
            case "PYTHON", "PYTHON3" -> Optional.of(PYTHON);
            case "CPP", "CPLUSPLUS" -> Optional.of(CPP);
            case "JAVASCRIPT", "JS", "NODE" -> Optional.of(JAVASCRIPT);
            default -> Optional.empty();
        };
    }
}
