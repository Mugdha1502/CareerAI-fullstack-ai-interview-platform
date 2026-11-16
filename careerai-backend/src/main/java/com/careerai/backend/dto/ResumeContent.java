package com.careerai.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Structured Resume Builder content, stored as JSON on BUILDER resumes and their versions:
 * { personal: {}, summary: "", education: [], experience: [], projects: [], skills: [], certifications: [] }.
 * The server never adds content; it only trims text and drops empty entries.
 */
public record ResumeContent(
    @Valid Personal personal,
    @Size(max = 3000, message = "Summary must be at most 3000 characters") String summary,
    @Valid @Size(max = 20, message = "At most 20 education entries") List<Education> education,
    @Valid @Size(max = 20, message = "At most 20 experience entries") List<Experience> experience,
    @Valid @Size(max = 20, message = "At most 20 projects") List<Project> projects,
    @Size(max = 80, message = "At most 80 skills") List<@Size(max = 60, message = "Each skill must be at most 60 characters") String> skills,
    @Size(max = 40, message = "At most 40 certifications") List<@Size(max = 150, message = "Each certification must be at most 150 characters") String> certifications
) {

    public record Personal(
        @Size(max = 100) String name,
        @Size(max = 120) String role,
        @Size(max = 255) String email,
        @Size(max = 40) String phone,
        @Size(max = 120) String location,
        @Size(max = 255) String linkedin
    ) {
        Personal normalized() {
            return new Personal(clean(name), clean(role), clean(email), clean(phone), clean(location), clean(linkedin));
        }
    }

    public record Education(
        @Size(max = 150) String degree,
        @Size(max = 150) String institution,
        @Size(max = 40) String year,
        @Size(max = 40) String grade
    ) {
        Education normalized() {
            return new Education(clean(degree), clean(institution), clean(year), clean(grade));
        }

        boolean isEmpty() {
            return allBlank(degree, institution, year, grade);
        }
    }

    public record Experience(
        @Size(max = 120) String title,
        @Size(max = 120) String company,
        @Size(max = 60) String period,
        @Size(max = 3000) String description
    ) {
        Experience normalized() {
            return new Experience(clean(title), clean(company), clean(period), cleanBlock(description));
        }

        boolean isEmpty() {
            return allBlank(title, company, period, description);
        }
    }

    public record Project(
        @Size(max = 150) String name,
        @Size(max = 200) String tech,
        @Size(max = 3000) String description
    ) {
        Project normalized() {
            return new Project(clean(name), clean(tech), cleanBlock(description));
        }

        boolean isEmpty() {
            return allBlank(name, tech, description);
        }
    }

    /** Trimmed copy with empty entries removed; never adds data. */
    public ResumeContent normalized() {
        Personal p = personal == null ? new Personal(null, null, null, null, null, null) : personal.normalized();
        return new ResumeContent(
            p,
            cleanBlock(summary),
            normalizeList(education, Education::normalized, Education::isEmpty),
            normalizeList(experience, Experience::normalized, Experience::isEmpty),
            normalizeList(projects, Project::normalized, Project::isEmpty),
            cleanStrings(skills),
            cleanStrings(certifications)
        );
    }

    /**
     * Plain-text rendering with standard section headings. This text is what ATS analysis,
     * job matching and interview preparation read for builder resumes.
     */
    public String toPlainText() {
        ResumeContent c = normalized();
        StringBuilder text = new StringBuilder();
        Personal p = c.personal();

        appendLine(text, p.name());
        appendLine(text, p.role());
        appendLine(text, String.join(" | ", nonBlank(p.email(), p.phone(), p.location(), p.linkedin())));

        if (c.summary() != null) {
            heading(text, "SUMMARY");
            appendLine(text, c.summary());
        }

        if (!c.education().isEmpty()) {
            heading(text, "EDUCATION");
            for (Education e : c.education()) {
                appendLine(text, String.join(" - ", nonBlank(e.degree(), e.institution())));
                appendLine(text, String.join(" | ", nonBlank(e.year(), e.grade())));
            }
        }

        if (!c.experience().isEmpty()) {
            heading(text, "EXPERIENCE");
            for (Experience e : c.experience()) {
                appendLine(text, String.join(" - ", nonBlank(e.title(), e.company())));
                appendLine(text, e.period());
                appendLine(text, e.description());
            }
        }

        if (!c.projects().isEmpty()) {
            heading(text, "PROJECTS");
            for (Project project : c.projects()) {
                appendLine(text, project.name());
                appendLine(text, project.tech() == null ? null : "Technologies: " + project.tech());
                appendLine(text, project.description());
            }
        }

        if (!c.skills().isEmpty()) {
            heading(text, "SKILLS");
            appendLine(text, String.join(", ", c.skills()));
        }

        if (!c.certifications().isEmpty()) {
            heading(text, "CERTIFICATIONS");
            c.certifications().forEach(cert -> appendLine(text, cert));
        }

        return text.toString().strip();
    }


    /* ---------- helpers ---------- */

    private static void heading(StringBuilder text, String title) {
        text.append('\n').append(title).append('\n');
    }

    private static void appendLine(StringBuilder text, String line) {
        if (line != null && !line.isBlank()) {
            text.append(line).append('\n');
        }
    }

    private static List<String> nonBlank(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) result.add(value);
        }
        return result;
    }

    private static <T> List<T> normalizeList(List<T> items, Function<T, T> normalize, java.util.function.Predicate<T> isEmpty) {
        if (items == null) return List.of();
        return items.stream().filter(Objects::nonNull).map(normalize).filter(isEmpty.negate()).toList();
    }

    private static List<String> cleanStrings(List<String> values) {
        if (values == null) return List.of();
        return values.stream().map(ResumeContent::clean).filter(Objects::nonNull).distinct().toList();
    }

    private static boolean allBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return false;
        }
        return true;
    }

    /** Single-line value: trimmed, inner whitespace collapsed, null when blank. */
    private static String clean(String value) {
        if (value == null) return null;
        String result = value.replaceAll("\\s+", " ").strip();
        return result.isEmpty() ? null : result;
    }

    /** Multi-line value: line breaks kept, each line trimmed, null when blank. */
    private static String cleanBlock(String value) {
        if (value == null) return null;
        String result = value.replace("\r\n", "\n").replace('\r', '\n')
            .lines()
            .map(line -> line.replaceAll("[ \\t]+", " ").strip())
            .filter(line -> !line.isEmpty())
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
        return result.isEmpty() ? null : result;
    }
}
