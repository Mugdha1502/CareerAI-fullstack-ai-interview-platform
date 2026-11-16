package com.careerai.backend.service;

import com.careerai.backend.dto.ResumeAnalysisResponse;
import com.careerai.backend.dto.ResumeAnalysisResponse.Check;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rule-based ATS readiness: checks that a resume's text is parseable and complete
 * (contact details, standard headings, length, bullets, skills, action verbs, dates).
 * Every score comes from the transparent checks returned with it.
 *
 * Future: an AI feedback step can add qualitative comments next to these rules;
 * the rules stay as the deterministic baseline.
 */
@Component
@RequiredArgsConstructor
public class AtsRuleEngine {

    public static final String ENGINE = "RULE_BASED";
    public static final String LABEL = "Rule-based ATS readiness";
    public static final String DISCLAIMER =
        "This is a rule-based readiness check of your resume text. It does not reproduce any specific company's applicant tracking system.";

    private static final Set<String> ACTION_VERBS = Set.of(
        "achieved", "analysed", "analyzed", "architected", "automated", "built", "collaborated", "configured",
        "contributed", "created", "debugged", "delivered", "deployed", "designed", "developed", "documented",
        "engineered", "enhanced", "established", "executed", "implemented", "improved", "integrated", "launched",
        "led", "maintained", "managed", "mentored", "migrated", "optimised", "optimized", "organised", "organized",
        "planned", "presented", "reduced", "refactored", "researched", "resolved", "streamlined", "supported",
        "tested", "trained", "wrote", "coordinated", "conducted", "prepared", "modelled", "modeled", "secured"
    );

    private static final Pattern BULLET = Pattern.compile("^\\s*([\\u2022\\u25AA\\u25CF\\u25E6\\u2023\\u2013\\u2014*\\-]|\\d+[.)])\\s+");
    private static final Pattern YEAR = Pattern.compile("\\b(19[89]\\d|20[0-4]\\d)\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d");
    private static final Pattern FIRST_PERSON = Pattern.compile("(?i)(?<![A-Za-z])(i|me|my|myself)(?![A-Za-z])");

    private final ResumeTextAnalyzer analyzer;

    public ResumeAnalysisResponse analyze(String text) {
        String safeText = text == null ? "" : text;
        List<String> lines = safeText.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();

        Map<String, Boolean> sections = analyzer.detectSections(safeText);
        int wordCount = analyzer.wordCount(safeText);
        List<String> skills = analyzer.findSkills(safeText);
        List<String> overused = analyzer.overusedTerms(safeText);

        List<String> bulletLines = lines.stream().filter(line -> BULLET.matcher(line).find()).toList();
        List<String> statementLines = bulletLines.isEmpty()
            ? lines.stream().filter(line -> line.split("\\s+").length >= 6).toList()
            : bulletLines;
        long actionLines = statementLines.stream().filter(this::startsWithActionVerb).count();
        long quantifiedLines = statementLines.stream().filter(this::hasMeasurableDetail).count();
        long years = YEAR.matcher(safeText).results().count();
        long firstPerson = FIRST_PERSON.matcher(safeText).results().count();
        long longLines = lines.stream().filter(line -> line.length() > 350).count();
        double garbledRatio = safeText.isEmpty() ? 0 : safeText.chars().filter(c -> c == '�' || (Character.isISOControl(c) && c != '\n' && c != '\t')).count() / (double) safeText.length();

        List<Check> checks = new ArrayList<>();

        /* Formatting / parseability (100) */
        int formatting = 0;
        formatting += score(checks, "email", "formatting", "Email address", sections.get("email"), 8,
            "An email address was found.", "No email address was found.");
        formatting += score(checks, "phone", "formatting", "Phone number", sections.get("phone"), 7,
            "A phone number was found.", "No phone number (10-15 digits) was found.");
        formatting += score(checks, "skills-section", "formatting", "Skills heading", sections.get("skills"), 15,
            "A Skills section heading was found.", "No Skills section heading was found.");
        boolean experienceOrProjects = sections.get("experience") || sections.get("projects");
        formatting += score(checks, "experience-section", "formatting", "Experience or Projects heading", experienceOrProjects, 15,
            "An Experience or Projects heading was found.", "No Experience or Projects heading was found.");
        formatting += score(checks, "education-section", "formatting", "Education heading", sections.get("education"), 15,
            "An Education heading was found.", "No Education heading was found.");

        int lengthPoints = wordCount >= 300 && wordCount <= 900 ? 15 : (wordCount >= 200 && wordCount <= 1200 ? 9 : 3);
        formatting += lengthPoints;
        checks.add(new Check("length", "formatting", "Resume length", lengthPoints == 15,
            wordCount + " words (300-900 is typical for a one- to two-page resume)."));

        int bulletPoints = bulletLines.size() >= 3 ? 10 : (bulletLines.isEmpty() ? 0 : 5);
        formatting += bulletPoints;
        checks.add(new Check("bullets", "formatting", "Bullet points", bulletLines.size() >= 3,
            bulletLines.size() + " bullet point lines detected."));

        formatting += score(checks, "text-quality", "formatting", "Readable text", garbledRatio < 0.005, 10,
            "The text was extracted cleanly.", "Some characters could not be read; the file may use unusual fonts or graphics.");
        formatting += score(checks, "line-length", "formatting", "Paragraph length", longLines == 0, 5,
            "No overly long paragraphs.", longLines + " very long paragraph(s); bullets are easier to scan.");

        /* Keywords (100) */
        int keyword = skills.isEmpty() ? 0 : Math.min(100, 25 + skills.size() * 10);
        keyword = Math.max(0, keyword - Math.min(20, overused.size() * 10));
        checks.add(new Check("skills-detected", "keywords", "Recognised skills", skills.size() >= 5,
            skills.isEmpty() ? "No recognised technical skills were found." : skills.size() + " recognised skills: " + String.join(", ", skills) + "."));
        checks.add(new Check("repetition", "keywords", "Keyword repetition", overused.isEmpty(),
            overused.isEmpty() ? "No word is heavily repeated." : "Heavily repeated: " + String.join(", ", overused) + "."));

        /* Content (100) */
        int content = 0;
        content += score(checks, "summary", "content", "Summary or objective", sections.get("summary"), 20,
            "A summary/objective section was found.", "No summary or objective section was found.");
        double actionRatio = statementLines.isEmpty() ? 0 : actionLines / (double) statementLines.size();
        int actionPoints = actionRatio >= 0.6 ? 30 : actionRatio >= 0.3 ? 18 : actionLines > 0 ? 8 : 0;
        content += actionPoints;
        checks.add(new Check("action-verbs", "content", "Action verbs", actionRatio >= 0.6,
            actionLines + " of " + statementLines.size() + " statements start with an action verb."));
        int measurePoints = quantifiedLines >= 3 ? 25 : quantifiedLines > 0 ? 12 : 0;
        content += measurePoints;
        checks.add(new Check("measurable", "content", "Measurable details", quantifiedLines >= 3,
            quantifiedLines + " statements include numbers (counts, percentages, durations)."));
        content += score(checks, "first-person", "content", "Neutral tone", firstPerson <= 3, 15,
            "Little or no first-person wording.", firstPerson + " first-person words (I/me/my); resumes usually omit them.");
        content += score(checks, "links", "content", "Profile links", sections.get("links"), 10,
            "A LinkedIn, GitHub or portfolio link was found.", "No LinkedIn, GitHub or portfolio link was found.");

        /* Experience (100) */
        int experience = 0;
        experience += score(checks, "experience", "experience", "Work or internship experience", sections.get("experience"), 45,
            "An experience/internship section was found.", "No experience or internship section was found.");
        experience += score(checks, "projects", "experience", "Projects", sections.get("projects"), 35,
            "A projects section was found.", "No projects section was found.");
        int datePoints = years >= 2 ? 20 : years == 1 ? 10 : 0;
        experience += datePoints;
        checks.add(new Check("dates", "experience", "Dates and timeline", years >= 2,
            years + " year references found (e.g. 2023)."));

        int overall = (int) Math.round(formatting * 0.30 + keyword * 0.25 + content * 0.25 + experience * 0.20);

        List<String> strengths = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        summarize(checks, skills, sections, strengths, issues, recommendations);

        return new ResumeAnalysisResponse(
            null, null, null, "ATS", ENGINE, LABEL, DISCLAIMER,
            overall, overall, ResumeAnalysisResponse.RULE_ONLY_METHOD,
            keyword, formatting, content, experience,
            sections, wordCount, skills, overused, checks,
            strengths, issues, recommendations, null, null
        );
    }


    /* ---------- strengths / issues / recommendations ---------- */

    private void summarize(List<Check> checks, List<String> skills, Map<String, Boolean> sections,
                           List<String> strengths, List<String> issues, List<String> recommendations) {
        for (Check check : checks) {
            if (check.passed()) {
                String strength = switch (check.id()) {
                    case "email", "phone" -> null;
                    case "skills-section" -> "Clear Skills section that parsers can find.";
                    case "education-section" -> "Education section is present.";
                    case "bullets" -> "Uses bullet points, which are easy to scan.";
                    case "action-verbs" -> "Most statements start with action verbs.";
                    case "measurable" -> "Several statements include concrete numbers.";
                    case "skills-detected" -> "Lists " + skills.size() + " recognisable technical skills.";
                    case "experience" -> "Includes work or internship experience.";
                    case "projects" -> "Includes a projects section.";
                    case "length" -> "Length is appropriate for a one- to two-page resume.";
                    default -> null;
                };
                if (strength != null) strengths.add(strength);
                continue;
            }

            issues.add(check.label() + ": " + check.detail());
            String recommendation = switch (check.id()) {
                case "email", "phone" -> "Add your contact details (email and phone) as plain text near the top.";
                case "skills-section" -> "Add a section titled \"Skills\" listing technologies you have actually used.";
                case "experience-section" -> "Add an \"Experience\" or \"Projects\" heading so your work can be found.";
                case "education-section" -> "Add an \"Education\" heading with your degree and institution.";
                case "length" -> "Aim for roughly 300-900 words: expand thin sections with real detail, or trim repetition.";
                case "bullets" -> "Describe responsibilities and results as short bullet points.";
                case "text-quality" -> "Export the resume as a text-based PDF or DOCX using standard fonts.";
                case "line-length" -> "Split long paragraphs into bullet points.";
                case "skills-detected" -> "List the specific tools and technologies you know in the Skills section.";
                case "repetition" -> "Vary repeated words; listing a keyword once in the right section is enough.";
                case "summary" -> "Add a 2-3 line summary of your target role and genuine strengths.";
                case "action-verbs" -> "Start bullet points with an accurate action verb such as Developed, Built or Tested.";
                case "measurable" -> "Where you have real figures (users, records, time saved), add them. Do not estimate or invent numbers.";
                case "first-person" -> "Remove first-person words (I, me, my); start statements with the action instead.";
                case "links" -> "Add a LinkedIn, GitHub or portfolio link if you have one.";
                case "experience" -> "If you have internships or work experience, add them under an \"Experience\" heading.";
                case "projects" -> "Add a \"Projects\" section describing projects you built and the technologies used.";
                case "dates" -> "Add dates (e.g. 2023 - 2024) to education and experience entries.";
                default -> null;
            };
            if (recommendation != null && !recommendations.contains(recommendation)) {
                recommendations.add(recommendation);
            }
        }

        if (sections.get("email") && sections.get("phone")) {
            strengths.add(0, "Contact details (email and phone) are easy to find.");
        }
    }


    /* ---------- helpers ---------- */

    private static int score(List<Check> checks, String id, String category, String label, Boolean passed,
                             int points, String passText, String failText) {
        boolean ok = Boolean.TRUE.equals(passed);
        checks.add(new Check(id, category, label, ok, ok ? passText : failText));
        return ok ? points : 0;
    }

    private boolean startsWithActionVerb(String line) {
        String stripped = BULLET.matcher(line).replaceFirst("").strip();
        String first = stripped.split("[\\s,;:]+", 2)[0].toLowerCase(Locale.ROOT);
        return ACTION_VERBS.contains(first);
    }

    /** A number other than a year, e.g. "reduced load time by 30%" or "for 200 users". */
    private boolean hasMeasurableDetail(String line) {
        String withoutYears = YEAR.matcher(line).replaceAll("");
        return NUMBER.matcher(withoutYears).find() && !analyzer.hasPhone(line) && !analyzer.hasEmail(line);
    }
}
