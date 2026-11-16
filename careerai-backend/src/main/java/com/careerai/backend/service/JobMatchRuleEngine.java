package com.careerai.backend.service;

import com.careerai.backend.dto.JobMatchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic resume-vs-job comparison: literal skill and keyword overlap, stated years
 * of experience and recognised degree levels. It does not understand meaning, so synonyms
 * or implied skills are not matched; that is the job of the future AI comparison step.
 */
@Component
@RequiredArgsConstructor
public class JobMatchRuleEngine {

    public static final String ENGINE = "RULE_BASED";
    public static final String LABEL = "Rule-based keyword match";
    public static final String DISCLAIMER =
        "Scores compare words that literally appear in your resume and the job description. Semantic AI matching is not connected yet.";

    private static final Pattern JOB_YEARS = Pattern.compile(
        "(?i)(\\d{1,2})\\s*\\+?\\s*(?:(?:-|to)\\s*\\d{1,2}\\s*)?\\+?\\s*(?:years?|yrs?)");
    private static final Pattern RESUME_YEARS = Pattern.compile(
        "(?i)(\\d{1,2}(?:\\.\\d)?)\\s*\\+?\\s*(?:years?|yrs?)\\s+(?:of\\s+)?(?:professional\\s+|industry\\s+|work\\s+)?(?:experience|exp)");
    private static final Pattern ENTRY_LEVEL = Pattern.compile(
        "(?i)\\b(fresher|freshers|entry[ -]level|graduate trainee|new grad|recent graduate|internship|intern|0\\s*(?:-|to)\\s*1\\s*(?:years?|yrs?))\\b");

    /** Degree levels in ascending order, with patterns that recognise them. */
    private static final Map<String, Pattern> DEGREE_LEVELS = Map.of(
        "DIPLOMA", Pattern.compile("(?i)\\bdiploma\\b"),
        "BACHELOR", Pattern.compile("(?i)(\\bbachelor|\\bb\\.?\\s?tech\\b|\\bb\\.e\\.?(?![a-z])|\\bb\\.?\\s?sc\\b|\\bbca\\b|\\bb\\.?\\s?com\\b|\\bundergraduate\\b|\\bgraduate degree\\b|\\bengineering degree\\b|\\bdegree in\\b)"),
        "MASTER", Pattern.compile("(?i)(\\bmaster|\\bm\\.?\\s?tech\\b|\\bm\\.e\\.?(?![a-z])|\\bm\\.?\\s?sc\\b|\\bmca\\b|\\bmba\\b|\\bpost[- ]?graduate\\b)"),
        "PHD", Pattern.compile("(?i)(\\bph\\.?\\s?d\\b|\\bdoctorate\\b)")
    );
    private static final List<String> LEVEL_ORDER = List.of("DIPLOMA", "BACHELOR", "MASTER", "PHD");

    private final ResumeTextAnalyzer analyzer;

    /** The comparison result; id/resume/job fields are filled in by JobMatchService. */
    public JobMatchResponse compare(String resumeText, String jobDescription) {
        List<String> jobSkills = analyzer.findSkills(jobDescription);
        List<String> resumeSkills = analyzer.findSkills(resumeText);
        List<String> matchedSkills = jobSkills.stream().filter(resumeSkills::contains).toList();
        List<String> missingSkills = jobSkills.stream().filter(skill -> !resumeSkills.contains(skill)).toList();

        // Keywords: frequent non-skill terms of the job description (skills are scored separately).
        int minCount = analyzer.wordCount(jobDescription) > 150 ? 2 : 1;
        List<String> jobTerms = analyzer.topTerms(jobDescription, 15, minCount);
        List<String> matchedKeywords = new ArrayList<>(matchedSkills);
        List<String> missingKeywords = new ArrayList<>(missingSkills);
        for (String term : jobTerms) {
            (analyzer.containsTerm(resumeText, term) ? matchedKeywords : missingKeywords).add(term);
        }

        int totalKeywords = matchedKeywords.size() + missingKeywords.size();
        int keywordCoverage = totalKeywords == 0 ? 0 : Math.round(matchedKeywords.size() * 100f / totalKeywords);
        Integer skillCoverage = jobSkills.isEmpty() ? null : Math.round(matchedSkills.size() * 100f / jobSkills.size());

        Alignment experience = experienceAlignment(resumeText, jobDescription);
        Alignment education = educationAlignment(resumeText, jobDescription);

        double weighted = 0;
        double weights = 0;
        if (skillCoverage != null) { weighted += skillCoverage * 0.45; weights += 0.45; }
        weighted += keywordCoverage * 0.35; weights += 0.35;
        if (experience.points() != null) { weighted += experience.points() * 0.10; weights += 0.10; }
        if (education.points() != null) { weighted += education.points() * 0.10; weights += 0.10; }
        int overall = (int) Math.round(weighted / weights);

        List<String> recommendations = recommendations(matchedSkills, missingSkills, missingKeywords, jobSkills, experience, education);

        return new JobMatchResponse(
            null, null, null, null, null, ENGINE, LABEL, DISCLAIMER,
            overall, overall, null, JobMatchResponse.KEYWORD_ONLY_METHOD, keywordCoverage, skillCoverage,
            matchedSkills, missingSkills, List.copyOf(matchedKeywords), List.copyOf(missingKeywords),
            experience.value(), experience.note(), education.value(), education.note(),
            recommendations, null, null, null
        );
    }


    /* ---------- Experience ---------- */

    private record Alignment(String value, String note, Integer points) {
    }

    private Alignment experienceAlignment(String resumeText, String jobDescription) {
        Integer jobYears = firstNumber(JOB_YEARS, jobDescription);
        boolean entryLevel = ENTRY_LEVEL.matcher(jobDescription).find() || (jobYears != null && jobYears == 0);
        Double resumeYears = firstDecimal(RESUME_YEARS, resumeText);
        Set<String> sections = analyzer.headingsFound(resumeText);
        boolean hasExperience = sections.contains("experience");
        boolean hasProjects = sections.contains("projects");

        if (entryLevel && (jobYears == null || jobYears <= 1)) {
            if (hasExperience || hasProjects) {
                return new Alignment("GOOD", "Entry-level role; your resume shows " + (hasExperience ? "experience" : "projects") + ".", 100);
            }
            return new Alignment("PARTIAL", "Entry-level role, but no experience or projects section was found on your resume.", 60);
        }

        if (jobYears == null) {
            return new Alignment("NOT_SPECIFIED", "The job description does not state an experience requirement.", null);
        }

        if (resumeYears != null) {
            String stated = formatYears(resumeYears);
            return resumeYears >= jobYears
                ? new Alignment("GOOD", "The job asks for " + jobYears + "+ years; your resume states " + stated + ".", 100)
                : new Alignment("LOW", "The job asks for " + jobYears + "+ years; your resume states " + stated + ".", 20);
        }

        if (hasExperience) {
            return new Alignment("PARTIAL",
                "The job asks for " + jobYears + "+ years. Your resume has an experience section but does not state total years, so this could not be verified.", 60);
        }

        return new Alignment("LOW", "The job asks for " + jobYears + "+ years; no experience section was found on your resume.", 20);
    }


    /* ---------- Education ---------- */

    private Alignment educationAlignment(String resumeText, String jobDescription) {
        Integer required = lowestLevel(jobDescription);
        Integer held = highestLevel(resumeText);
        boolean educationSection = analyzer.headingsFound(resumeText).contains("education");

        if (required == null) {
            return new Alignment("NOT_SPECIFIED", "The job description does not name a degree requirement.", null);
        }

        String requiredName = levelName(required);

        if (held == null) {
            return educationSection
                ? new Alignment("PARTIAL", "The job mentions a " + requiredName + " degree; your education section does not name a recognised degree level.", 60)
                : new Alignment("NOT_FOUND", "The job mentions a " + requiredName + " degree; no education details were found on your resume.", 20);
        }

        return held >= required
            ? new Alignment("MATCHED", "The job mentions a " + requiredName + " degree; your resume lists a " + levelName(held) + " degree.", 100)
            : new Alignment("PARTIAL", "The job mentions a " + requiredName + " degree; your resume lists a " + levelName(held) + " qualification.", 60);
    }

    private Integer lowestLevel(String text) {
        for (int i = 0; i < LEVEL_ORDER.size(); i++) {
            if (DEGREE_LEVELS.get(LEVEL_ORDER.get(i)).matcher(text).find()) return i;
        }
        return null;
    }

    private Integer highestLevel(String text) {
        for (int i = LEVEL_ORDER.size() - 1; i >= 0; i--) {
            if (DEGREE_LEVELS.get(LEVEL_ORDER.get(i)).matcher(text).find()) return i;
        }
        return null;
    }

    private static String levelName(int index) {
        return switch (LEVEL_ORDER.get(index)) {
            case "DIPLOMA" -> "diploma";
            case "BACHELOR" -> "bachelor's";
            case "MASTER" -> "master's";
            default -> "doctoral";
        };
    }


    /* ---------- Recommendations ---------- */

    private List<String> recommendations(List<String> matched, List<String> missing, List<String> missingKeywords,
                                         List<String> jobSkills, Alignment experience, Alignment education) {
        Set<String> result = new LinkedHashSet<>();

        if (!missing.isEmpty()) {
            String list = String.join(", ", missing.subList(0, Math.min(5, missing.size())));
            result.add("Skills in the job description not found on your resume: " + list
                + ". If you have genuinely used them, add them to your Skills section or project descriptions.");
            result.add("For skills you have not used yet, practise them before the interview rather than listing them.");
        }

        if (!matched.isEmpty()) {
            result.add("Make sure matched skills (" + String.join(", ", matched.subList(0, Math.min(5, matched.size())))
                + ") also appear in your project or experience bullet points, not only in the skills list.");
        }

        List<String> terms = missingKeywords.stream().filter(term -> !missing.contains(term)).limit(5).toList();
        if (!terms.isEmpty()) {
            result.add("Job keywords not found on your resume: " + String.join(", ", terms)
                + ". Use the job's wording where it truthfully describes your work.");
        }

        if (jobSkills.isEmpty()) {
            result.add("No recognised technical skills were found in the job description; paste the full description including requirements for a better comparison.");
        }

        if ("PARTIAL".equals(experience.value()) || "LOW".equals(experience.value())) {
            result.add("Show your experience clearly: list internships, jobs and projects with dates. Do not overstate your years of experience.");
        }

        if ("PARTIAL".equals(education.value()) || "NOT_FOUND".equals(education.value())) {
            result.add("Write your degree name and institution in an Education section so the requirement can be checked.");
        }

        return List.copyOf(result);
    }


    /* ---------- helpers ---------- */

    private static Integer firstNumber(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        Integer lowest = null;
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value <= 30 && (lowest == null || value < lowest)) lowest = value;
        }
        return lowest;
    }

    private static Double firstDecimal(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        Double highest = null;
        while (matcher.find()) {
            double value = Double.parseDouble(matcher.group(1));
            if (value <= 50 && (highest == null || value > highest)) highest = value;
        }
        return highest;
    }

    private static String formatYears(double years) {
        String number = years == Math.floor(years) ? String.valueOf((int) years) : String.valueOf(years);
        return number + (years == 1 ? " year" : " years");
    }
}
