package com.careerai.backend.service;

import com.careerai.backend.dto.ResumeImprovementResponse.Change;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Basic wording assistance (not AI). It only tidies what is already written:
 * spacing, capitalisation, sentence punctuation, first-person openers, repeated words
 * and the standard spelling of well-known technology names. It never changes meaning,
 * adds facts or invents numbers; stronger rewording is left as suggestions.
 */
@Component
public class BasicWordingImprovementEngine implements ResumeImprovementEngine {

    public static final String ID = "BASIC_RULES";

    /** Standard spellings of technology names (only the capitalisation changes). */
    private static final Map<String, String> TECH_SPELLINGS = techSpellings();

    private static final Pattern FIRST_PERSON_OPENER = Pattern.compile("(?i)^(i am|i was|i have|i've|i)\\s+");
    private static final Pattern REPEATED_WORD = Pattern.compile("(?i)\\b([a-z]+)\\s+\\1\\b");
    private static final Pattern BULLET = Pattern.compile("^([\\u2022\\u25AA\\u25CF\\u2023*\\-\\u2013]|\\d+[.)])\\s*");
    private static final Pattern WEAK_OPENER = Pattern.compile("(?i)^(responsible for|worked on|helped( to)?|did|made|was part of|involved in)\\b");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAi() {
        return false;
    }

    @Override
    public Result improve(String text, String section) {
        Set<String> changes = new LinkedHashSet<>();
        Set<String> suggestions = new LinkedHashSet<>();
        boolean skillsSection = "SKILLS".equals(section);

        List<String> lines = new ArrayList<>();
        for (String rawLine : text.replace("\r\n", "\n").split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) continue;

            String bullet = "";
            Matcher bulletMatcher = BULLET.matcher(line);
            if (bulletMatcher.find()) {
                bullet = "- ";
                if (!bulletMatcher.group(1).equals("-")) changes.add("Used a consistent bullet style.");
                line = line.substring(bulletMatcher.end()).strip();
            }

            String collapsed = line.replaceAll("\\s{2,}", " ").replaceAll("\\s+([,.;:])", "$1");
            if (!collapsed.equals(line)) changes.add("Removed extra spaces.");
            line = collapsed;

            String deduped = REPEATED_WORD.matcher(line).replaceAll("$1");
            if (!deduped.equals(line)) changes.add("Removed a repeated word.");
            line = deduped;

            String fixedCase = applyTechSpellings(line);
            if (!fixedCase.equals(line)) changes.add("Used standard capitalisation for technology names.");
            line = fixedCase;

            if (!skillsSection) {
                Matcher opener = FIRST_PERSON_OPENER.matcher(line);
                if (opener.find() && line.length() > opener.end()) {
                    line = line.substring(opener.end());
                    changes.add("Removed first-person wording (I / I was / I have).");
                }

                if (WEAK_OPENER.matcher(line).find()) {
                    suggestions.add("\"" + truncate(line, 50) + "\": if accurate, start with a specific action verb such as Developed, Built, Designed or Tested.");
                }

                if (!line.isEmpty() && Character.isLowerCase(line.charAt(0))) {
                    line = Character.toUpperCase(line.charAt(0)) + line.substring(1);
                    changes.add("Capitalised the first letter of each statement.");
                }

                if (!line.isEmpty() && !line.matches(".*[.!?:]$") && line.split("\\s+").length >= 4) {
                    line = line + ".";
                    changes.add("Ended sentences with a full stop.");
                }
            }

            lines.add(bullet + line);
        }

        if (!skillsSection) {
            boolean hasNumbers = text.matches("(?s).*\\d.*");
            if (!hasNumbers) {
                suggestions.add("If you have real figures (users, records, time saved), you can add them. Do not estimate or invent numbers.");
            }
        }

        if (changes.isEmpty()) {
            changes.add("No basic wording issues were found.");
        }

        List<Change> typed = changes.stream().map(BasicWordingImprovementEngine::typed).toList();
        return new Result(String.join("\n", lines), typed, List.of(), List.copyOf(suggestions));
    }

    /** Maps a rule's description onto the shared change types. */
    private static Change typed(String description) {
        String type;
        if (description.contains("bullet") || description.contains("spaces")) type = "STRUCTURE";
        else if (description.contains("repeated")) type = "CONCISENESS";
        else if (description.contains("Capitalised") || description.contains("full stop")) type = "GRAMMAR";
        else if (description.contains("No basic")) type = "CLARITY";
        else type = "WORDING";
        return new Change(type, description);
    }

    private static String applyTechSpellings(String line) {
        String result = line;
        for (Map.Entry<String, String> entry : TECH_SPELLINGS.entrySet()) {
            Pattern pattern = Pattern.compile("(?i)(?<![A-Za-z0-9.])" + Pattern.quote(entry.getKey()) + "(?![A-Za-z0-9])");
            result = pattern.matcher(result).replaceAll(Matcher.quoteReplacement(entry.getValue()));
        }
        return result;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max).strip() + "...";
    }

    private static Map<String, String> techSpellings() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : List.of(
            "JavaScript", "TypeScript", "Java", "Python", "Spring Boot", "Spring Security", "React", "Node.js",
            "MySQL", "PostgreSQL", "MongoDB", "SQL", "REST API", "REST APIs", "HTML", "CSS", "GitHub", "Git",
            "Docker", "Kubernetes", "AWS", "Azure", "Hibernate", "JUnit", "Jenkins", "Linux", "Redis", "Kafka",
            "GraphQL", "Django", "Flask", "Angular", "Bootstrap", "Tailwind CSS", "JSON", "API", "APIs", "JWT"
        )) {
            map.put(name.toLowerCase(Locale.ROOT), name);
        }
        return map;
    }
}
