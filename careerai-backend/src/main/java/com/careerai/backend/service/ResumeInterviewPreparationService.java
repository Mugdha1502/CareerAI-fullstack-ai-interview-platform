package com.careerai.backend.service;

import com.careerai.backend.billing.FeatureKey;
import com.careerai.backend.billing.EntitlementService;
import com.careerai.backend.ai.AiPromptService;
import com.careerai.backend.ai.AiProvider;
import com.careerai.backend.ai.AiResponseParser;
import com.careerai.backend.ai.AiResponseParser.InterviewPreparation;
import com.careerai.backend.dto.InterviewPreparationRequest;
import com.careerai.backend.service.AiUsageService.AiOutcome;
import com.careerai.backend.dto.InterviewPreparationResponse;
import com.careerai.backend.dto.InterviewPreparationResponse.FocusTopic;
import com.careerai.backend.entity.Resume;
import com.careerai.backend.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds interview-preparation context from a stored resume and an optional job description:
 * skills, sections and job keywords (rules), plus AI focus topics and question lists when the
 * AI provider is available. Without AI, rule-based focus topics are returned instead.
 * No interview is started here.
 */
@Service
@RequiredArgsConstructor
public class ResumeInterviewPreparationService {

    public static final String ENGINE = "RULE_BASED";
    public static final String FEATURE = "RESUME_INTERVIEW_PREP";
    private static final int MAX_TOPICS = 8;

    private final EntitlementService entitlementService;
    private final ResumeService resumeService;
    private final ResumeTextAnalyzer analyzer;
    private final CareerRequirementService careerRequirementService;
    private final AiProvider aiProvider;
    private final AiPromptService promptService;
    private final AiResponseParser responseParser;
    private final AiUsageService usageService;

    /**
     * AI preparation context when available, otherwise rule-based focus topics.
     * Not transactional: no database connection is held while the AI provider is called.
     */
    public InterviewPreparationResponse prepare(User user, Long resumeId, InterviewPreparationRequest request) {
        // Counted once per completed preparation (AI or rule-based); a failed request gives the use back.
        return entitlementService.consume(user, FeatureKey.RESUME_INTERVIEW_PREP, () -> doPrepare(user, resumeId, request));
    }

    private InterviewPreparationResponse doPrepare(User user, Long resumeId, InterviewPreparationRequest request) {
        Resume resume = resumeService.requireOwnedResume(user, resumeId);
        String resumeText = resumeService.requireText(resume);

        String rawJob = request == null ? null : request.jobDescription();
        boolean hasJob = rawJob != null && !rawJob.isBlank();
        String jobDescription = hasJob ? JobMatchService.requireJobDescription(rawJob) : null;
        String targetRole = request == null || request.targetRole() == null || request.targetRole().isBlank()
            ? null : request.targetRole().strip();

        List<String> resumeSkills = analyzer.findSkills(resumeText);
        Set<String> sections = analyzer.headingsFound(resumeText);
        List<String> jobSkills = hasJob ? analyzer.findSkills(jobDescription) : List.of();
        List<String> jobKeywords = hasJob ? analyzer.topTerms(jobDescription, 12, analyzer.wordCount(jobDescription) > 150 ? 2 : 1) : List.of();
        List<String> matched = jobSkills.stream().filter(resumeSkills::contains).toList();
        List<String> missing = jobSkills.stream().filter(skill -> !resumeSkills.contains(skill)).toList();

        List<FocusTopic> topics = new ArrayList<>();

        if (hasJob) {
            missing.stream().limit(3).forEach(skill -> topics.add(topic(skill, "GAP",
                "Required in the job description but not found on your resume. Expect questions on it.")));
            matched.stream().limit(2).forEach(skill -> topics.add(topic(skill, "CORE",
                "In both the job description and your resume. Be ready to explain how you used it.")));
        } else {
            resumeSkills.stream().limit(3).forEach(skill -> topics.add(topic(skill, "CORE",
                "Listed on your resume. Interviewers often ask how you used the skills you list.")));
        }

        if (sections.contains("projects")) {
            topics.add(new FocusTopic("Project walkthrough", "CORE",
                "Your resume has a projects section. Prepare to explain one project end to end: goal, your role, technologies, challenges.", null));
        }
        if (sections.contains("experience")) {
            topics.add(new FocusTopic("Experience walkthrough", "CORE",
                "Your resume lists experience. Prepare to describe your responsibilities and what you delivered.", null));
        }
        topics.add(new FocusTopic("Behavioural questions", "GENERAL",
            "Most interviews include teamwork, problem-solving and conflict questions.", "/question-bank"));

        String interviewType = hasJob ? "job" : "resume";

        AiOutcome<InterviewPreparation> ai = usageService.tryRun(user, FEATURE, () ->
            responseParser.parseInterviewPreparation(aiProvider.generateJson(
                promptService.resumeInterviewPreparation(resumeText, jobDescription, targetRole))));

        if (ai.succeeded()) {
            InterviewPreparation prep = ai.value();
            List<FocusTopic> aiTopics = new ArrayList<>();
            for (String name : prep.focusTopics()) {
                String priority = missing.stream().anyMatch(name::equalsIgnoreCase) ? "GAP"
                    : resumeSkills.stream().anyMatch(name::equalsIgnoreCase) ? "CORE" : "GENERAL";
                aiTopics.add(topic(name, priority, "Suggested by AI from your resume" + (hasJob ? " and the job description." : ".")));
            }

            return new InterviewPreparationResponse(
                resume.getId(), resume.getTitle(), interviewType, targetRole,
                "GEMINI",
                "AI-generated preparation context based only on your resume" + (hasJob ? " and the job description" : "") + ". Review it before your interview.",
                "AVAILABLE", null,
                resumeSkills, List.copyOf(sections), jobSkills, jobKeywords, matched, missing,
                aiTopics.stream().limit(MAX_TOPICS).toList(),
                prep.likelyResumeQuestions(), prep.projectQuestions(), prep.experienceQuestions(),
                prep.skillQuestions(), prep.potentialWeakAreas()
            );
        }

        String note = hasJob
            ? "Focus topics come from comparing the words in your resume and the job description."
            : "Focus topics come from the skills and sections found on your resume. Add a job description for job-specific topics.";

        return new InterviewPreparationResponse(
            resume.getId(),
            resume.getTitle(),
            interviewType,
            targetRole,
            ENGINE,
            note,
            "UNAVAILABLE",
            "AI preparation temporarily unavailable: " + ai.reason() + " Showing rule-based focus topics.",
            resumeSkills,
            List.copyOf(sections),
            jobSkills,
            jobKeywords,
            matched,
            missing,
            topics.stream().limit(MAX_TOPICS).toList(),
            List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    /** Topic for a skill, linking to its practice route when the skill has a practice module. */
    private FocusTopic topic(String skillName, String priority, String reason) {
        String route = careerRequirementService.findSkill(skillName)
            .filter(CareerRequirementService.SkillDefinition::isPracticable)
            .map(skill -> careerRequirementService.practiceRoute(skill, CareerRequirementService.DEFAULT_TRACK_ID))
            .orElse(null);
        return new FocusTopic(skillName, priority, reason, route);
    }
}
