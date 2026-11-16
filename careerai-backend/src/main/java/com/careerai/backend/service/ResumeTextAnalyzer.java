package com.careerai.backend.service;

import com.careerai.backend.service.CareerRequirementService.SkillDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic text helpers shared by ATS analysis, job matching and interview preparation:
 * skill detection (from the CareerRequirementService catalogue plus extra resume vocabulary),
 * section-heading detection, contact details and keyword frequencies.
 * Nothing here is AI; it only looks for words that are literally present.
 */
@Component
public class ResumeTextAnalyzer {

    /** Resume sections, in display order, with the headings that identify each one. */
    public static final Map<String, List<String>> SECTION_HEADINGS = sectionHeadings();

    /**
     * Technologies that are not catalogue skills but are common in resumes and job descriptions.
     * Display names only; they are matched case-insensitively as whole words.
     */
    private static final List<String> EXTRA_TECH_TERMS = List.of(
        "TypeScript", "Angular", "Vue.js", "Redux", "Next.js", "Tailwind CSS", "Bootstrap", "GraphQL",
        "Microservices", "Hibernate", "JPA", "Maven", "Gradle", "JUnit", "Mockito", "Kafka", "RabbitMQ",
        "Redis", "MongoDB", "Oracle", "Elasticsearch", "Nginx", "Jira", "Postman", "Swagger", "OAuth", "JWT",
        "C++", "Golang", "Kotlin", "Swift", "Figma", "Firebase", "TensorFlow", "PyTorch", "Django", "Flask",
        "FastAPI", "Express.js", "Spring MVC", "Thymeleaf", "Servlets", "JDBC", "Unix", "Ansible"
    );

    /** Catalogue aliases too ambiguous to match in free text (e.g. "rest of the team", "os"). */
    private static final Set<String> AMBIGUOUS_ALIASES = Set.of(
        "rest", "os", "spring", "voice", "coding", "requirements", "containers", "networks", "networking",
        "databases", "problem solving", "algorithms", "data structures", "ml", "node", "etl", "elt",
        "communication skills", "soft skills", "aptitude", "system-design", "api design", "apis", "excel",
        "go", "probability", "scrum", "testing"
    );

    /** Matched with exact capitalisation, because the lower-case word is ordinary English. */
    private static final Set<String> CASE_SENSITIVE_TERMS = Set.of("Excel", "Spring", "REST", "Testing", "Communication", "Monitoring", "Cloud", "Agile", "Statistics");

    private static final Set<String> STOPWORDS = Set.copyOf(List.of(
        "a", "an", "and", "are", "as", "at", "be", "been", "but", "by", "can", "for", "from", "has", "have", "in",
        "into", "is", "it", "its", "of", "on", "or", "our", "that", "the", "their", "this", "to", "we", "will",
        "with", "you", "your", "who", "what", "which", "who", "about", "across", "also", "any", "other", "such",
        "than", "then", "these", "those", "they", "them", "were", "was", "while", "within", "would", "should",
        "must", "may", "able", "ability", "strong", "good", "excellent", "great", "plus", "preferred", "required",
        "requirements", "responsibilities", "responsible", "role", "job", "candidate", "candidates", "team",
        "teams", "work", "working", "years", "year", "experience", "experienced", "knowledge", "skills", "skill",
        "understanding", "including", "etc", "using", "use", "used", "new", "well", "based", "looking", "join",
        "company", "opportunity", "location", "apply", "salary", "per", "all", "more", "most", "one", "two",
        "help", "make", "ensure", "within", "like", "least", "minimum", "need", "needs", "want", "how", "via",
        "i", "me", "my", "he", "she", "his", "her", "us", "not", "no", "yes", "if", "so", "do", "does", "did",
        "very", "highly", "day", "days", "time", "full", "part", "level", "related", "relevant", "field",
        "various", "multiple", "excellent", "familiarity", "familiar", "proficiency", "proficient", "hands",
        "summary", "profile", "objective", "education", "projects", "project", "certifications", "contact",
        "email", "phone", "linkedin", "github", "com", "www", "http", "https",
        "against", "hiring", "hire", "write", "writing", "written", "seeking", "offer", "offers", "benefits",
        "apply", "applicants", "position", "positions", "immediate", "joiner", "joiners", "openings", "opening",
        // Degree words are compared by the education rule, not as keywords.
        "bachelor", "bachelors", "master", "masters", "degree", "degrees", "science", "graduate", "graduates",
        "university", "college", "diploma"
    ));

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\d])(?:\\+?\\d{1,3}[\\s.-]?)?(?:\\(?\\d{2,5}\\)?[\\s.-]?)?\\d{3,5}[\\s.-]?\\d{3,5}(?![\\d])");
    private static final Pattern LINK = Pattern.compile("(?i)(linkedin\\.com/|github\\.com/|gitlab\\.com/|https?://|portfolio)");
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9+#.]*[A-Za-z0-9+#]|[A-Za-z]");

    private final List<TermPattern> skillPatterns;

    private record TermPattern(String displayName, List<Pattern> patterns) {
        boolean foundIn(String text) {
            return patterns.stream().anyMatch(pattern -> pattern.matcher(text).find());
        }
    }

    public ResumeTextAnalyzer(CareerRequirementService careerRequirementService) {
        List<TermPattern> patterns = new ArrayList<>();

        for (SkillDefinition skill : careerRequirementService.allSkills()) {
            Set<String> terms = new LinkedHashSet<>();
            terms.add(skill.name());
            skill.aliases().stream().filter(alias -> !AMBIGUOUS_ALIASES.contains(alias)).forEach(terms::add);
            patterns.add(new TermPattern(skill.name(), terms.stream().map(ResumeTextAnalyzer::termPattern).toList()));
        }

        for (String term : EXTRA_TECH_TERMS) {
            patterns.add(new TermPattern(term, List.of(termPattern(term))));
        }

        this.skillPatterns = List.copyOf(patterns);
    }


    /* ---------- Skills ---------- */

    /** Skills literally mentioned in the text, by display name, in catalogue order. */
    public List<String> findSkills(String text) {
        if (text == null || text.isBlank()) return List.of();
        return skillPatterns.stream().filter(term -> term.foundIn(text)).map(TermPattern::displayName).toList();
    }


    /* ---------- Sections ---------- */

    /** Which standard sections have a heading line, plus contact details (email/phone). */
    public Map<String, Boolean> detectSections(String text) {
        Map<String, Boolean> sections = new LinkedHashMap<>();
        boolean email = hasEmail(text);
        boolean phone = hasPhone(text);
        sections.put("contact", email || phone);
        sections.put("email", email);
        sections.put("phone", phone);
        sections.put("links", text != null && LINK.matcher(text).find());

        Set<String> found = headingsFound(text);
        SECTION_HEADINGS.keySet().forEach(section -> sections.put(section, found.contains(section)));
        return sections;
    }

    /** Names of the standard sections whose headings appear, e.g. [summary, skills, education]. */
    public Set<String> headingsFound(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null) return found;

        for (String rawLine : text.split("\\n")) {
            // "Skills: Java, SQL" -> the heading is the part before the colon.
            String candidate = rawLine.contains(":") ? rawLine.substring(0, rawLine.indexOf(':')) : rawLine;
            String line = candidate.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z& ]", " ")
                .replaceAll("\\s+", " ")
                .strip();
            if (line.isEmpty() || line.split(" ").length > 5) continue;

            for (Map.Entry<String, List<String>> entry : SECTION_HEADINGS.entrySet()) {
                boolean heading = entry.getValue().stream().anyMatch(h ->
                    line.equals(h) || line.startsWith(h + " & ") || line.startsWith(h + " and "));
                if (heading) {
                    found.add(entry.getKey());
                }
            }
        }
        return found;
    }

    public boolean hasEmail(String text) {
        return text != null && EMAIL.matcher(text).find();
    }

    public boolean hasPhone(String text) {
        if (text == null) return false;
        Matcher matcher = PHONE.matcher(text);
        while (matcher.find()) {
            long digits = matcher.group().chars().filter(Character::isDigit).count();
            if (digits >= 10 && digits <= 15) return true;
        }
        return false;
    }


    /* ---------- Words and keywords ---------- */

    public int wordCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.strip().split("\\s+").length;
    }

    /** Lower-case words that are not stop words, with their counts. */
    public Map<String, Integer> termFrequencies(String text) {
        Map<String, Integer> counts = new HashMap<>();
        if (text == null) return counts;

        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String word = matcher.group().toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
            if (word.length() < 3 || STOPWORDS.contains(word) || word.chars().allMatch(Character::isDigit)) continue;
            counts.merge(word, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Most frequent non-stop-word terms (excluding words already covered by a detected skill),
     * most frequent first. minCount filters out words used only once in long texts.
     */
    public List<String> topTerms(String text, int limit, int minCount) {
        Set<String> skillWords = new LinkedHashSet<>();
        findSkills(text).forEach(skill -> skillWords.addAll(List.of(skill.toLowerCase(Locale.ROOT).split("[^a-z0-9+#.]+"))));

        // Skills (and their aliases, e.g. "mysql" -> SQL) are scored as skills, not as keywords.
        return termFrequencies(text).entrySet().stream()
            .filter(entry -> entry.getValue() >= minCount && !skillWords.contains(entry.getKey()))
            .filter(entry -> findSkills(entry.getKey()).isEmpty())
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .toList();
    }

    /** Whole-word, case-insensitive presence of a term (a trailing plural "s" is tolerated). */
    public boolean containsTerm(String text, String term) {
        if (text == null || term == null || term.isBlank()) return false;
        String base = term.toLowerCase(Locale.ROOT);
        if (base.endsWith("s") && base.length() > 4) base = base.substring(0, base.length() - 1);
        Pattern pattern = Pattern.compile("(?i)(?<![A-Za-z0-9])" + Pattern.quote(base) + "(?:s|es)?(?![A-Za-z0-9])");
        return pattern.matcher(text).find();
    }

    /** Terms used far more often than the rest of the text, e.g. the same buzzword 10 times. */
    public List<String> overusedTerms(String text) {
        int words = wordCount(text);
        int threshold = Math.max(7, (int) Math.ceil(words * 0.025));
        return termFrequencies(text).entrySet().stream()
            .filter(entry -> entry.getValue() >= threshold)
            .sorted(Comparator.comparing(Map.Entry<String, Integer>::getValue).reversed())
            .limit(5)
            .map(entry -> entry.getKey() + " (" + entry.getValue() + "x)")
            .toList();
    }


    /* ---------- helpers ---------- */

    private static Pattern termPattern(String term) {
        String quoted = Pattern.quote(term);
        String boundaries = "(?<![A-Za-z0-9])" + quoted + "(?![A-Za-z0-9+#])";
        boolean caseSensitive = CASE_SENSITIVE_TERMS.contains(term);
        return caseSensitive ? Pattern.compile(boundaries) : Pattern.compile(boundaries, Pattern.CASE_INSENSITIVE);
    }

    private static Map<String, List<String>> sectionHeadings() {
        Map<String, List<String>> headings = new LinkedHashMap<>();
        headings.put("summary", List.of("summary", "professional summary", "profile", "career objective", "objective", "about me", "profile summary"));
        headings.put("skills", List.of("skills", "technical skills", "key skills", "core competencies", "technologies", "tech stack", "skills & certifications", "skill set"));
        headings.put("experience", List.of("experience", "work experience", "professional experience", "employment", "employment history", "work history", "internship", "internships", "relevant experience", "industry experience", "internship experience"));
        headings.put("education", List.of("education", "academic background", "academics", "educational qualifications", "qualifications", "academic qualifications"));
        headings.put("projects", List.of("projects", "academic projects", "personal projects", "key projects", "project work"));
        headings.put("certifications", List.of("certifications", "certificates", "licenses", "courses", "certifications & courses"));
        headings.put("achievements", List.of("achievements", "awards", "accomplishments", "honors", "honours"));
        return Collections.unmodifiableMap(headings);
    }
}
