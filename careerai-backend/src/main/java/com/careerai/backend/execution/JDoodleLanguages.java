package com.careerai.backend.execution;

import java.util.EnumMap;
import java.util.Map;

/**
 * The ONLY place CareerAI languages are mapped to JDoodle's language codes and pinned versionIndex
 * values (JDoodle docs → Languages and versions; versionIndex is a zero-based position in that
 * language's version list, pinned so new JDoodle versions cannot silently change behaviour).
 *
 *   JAVA       → java    5  (JDK 21.0.0)
 *   PYTHON     → python3 5  (3.11.5)
 *   CPP        → cpp17   3  (GCC 15.2.1, C++17)
 *   JAVASCRIPT → nodejs  5  (20.9.0)
 *
 * Optional override (app.execution.jdoodle.language-versions), e.g.
 *   JAVA=java:4,PYTHON=python3:6
 */
public final class JDoodleLanguages {

    public record Target(String language, String versionIndex, String label) {
    }

    private static final Map<CodeLanguage, Target> DEFAULTS = Map.of(
        CodeLanguage.JAVA, new Target("java", "5", "JDK 21.0.0"),
        CodeLanguage.PYTHON, new Target("python3", "5", "Python 3.11.5"),
        CodeLanguage.CPP, new Target("cpp17", "3", "GCC 15.2.1 (C++17)"),
        CodeLanguage.JAVASCRIPT, new Target("nodejs", "5", "Node.js 20.9.0")
    );

    private final Map<CodeLanguage, Target> targets;

    public JDoodleLanguages(String overrides) {
        Map<CodeLanguage, Target> map = new EnumMap<>(DEFAULTS);
        if (overrides != null && !overrides.isBlank()) {
            for (String pair : overrides.split(",")) {
                String[] keyValue = pair.split("=");
                if (keyValue.length != 2) continue;
                String[] target = keyValue[1].trim().split(":");
                if (target.length != 2 || target[0].isBlank() || !target[1].trim().matches("\\d+")) continue;
                CodeLanguage.parse(keyValue[0]).ifPresent(language ->
                    map.put(language, new Target(target[0].trim(), target[1].trim(), target[0].trim() + " #" + target[1].trim())));
            }
        }
        this.targets = Map.copyOf(map);
    }

    public Target targetFor(CodeLanguage language) {
        return targets.get(language);
    }

    public Map<CodeLanguage, Target> all() {
        return targets;
    }
}
