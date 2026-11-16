package com.careerai.backend.service;

import com.careerai.backend.billing.FeatureKey;
import com.careerai.backend.billing.EntitlementService;
import com.careerai.backend.dto.JobMatchRequest;
import com.careerai.backend.dto.JobMatchResponse;
import com.careerai.backend.dto.JobMatchResponse.Details;
import com.careerai.backend.dto.JobMatchResponse.SemanticFeedback;
import com.careerai.backend.ai.AiPromptService;
import com.careerai.backend.ai.AiProvider;
import com.careerai.backend.ai.AiResponseParser;
import com.careerai.backend.ai.AiResponseParser.JobMatchSemantic;
import com.careerai.backend.service.AiUsageService.AiOutcome;
import com.careerai.backend.entity.JobMatchAnalysis;
import com.careerai.backend.entity.Resume;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.InvalidResumeRequestException;
import com.careerai.backend.exception.ResumeNotFoundException;
import com.careerai.backend.repository.JobMatchAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Compares the caller's resume with a job description they pasted and stores the result.
 * Matching rules live in JobMatchRuleEngine; a future AI comparison is added behind this
 * service, not in the controller.
 */
@Service
@RequiredArgsConstructor
public class JobMatchService {

    /** Shorter text is not a job description the rules can compare meaningfully. */
    public static final int MIN_JOB_DESCRIPTION_LENGTH = 80;
    public static final String FEATURE = "RESUME_JOB_MATCH";

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final EntitlementService entitlementService;
    private final ResumeService resumeService;
    private final JobMatchRuleEngine ruleEngine;
    private final JobMatchAnalysisRepository jobMatchRepository;
    private final ObjectMapper objectMapper;
    private final AiProvider aiProvider;
    private final AiPromptService promptService;
    private final AiResponseParser responseParser;
    private final AiUsageService usageService;

    /**
     * Keyword comparison first, then optional AI semantic comparison, then the combined result
     * is stored. Not transactional: no database connection is held while the AI provider is called.
     */
    public JobMatchResponse analyze(User user, Long resumeId, JobMatchRequest request) {
        // Counted once per completed match (keyword and/or AI); a failed request gives the use back.
        return entitlementService.consume(user, FeatureKey.RESUME_JOB_MATCH, () -> doAnalyze(user, resumeId, request));
    }

    private JobMatchResponse doAnalyze(User user, Long resumeId, JobMatchRequest request) {
        Resume resume = resumeService.requireOwnedResume(user, resumeId);
        String resumeText = resumeService.requireText(resume);
        String jobDescription = requireJobDescription(request.jobDescription());

        JobMatchResponse result = ruleEngine.compare(resumeText, jobDescription);
        int keywordScore = result.keywordMatchScore();

        SemanticFeedback semantic = semanticFeedback(user, resumeText, request.jobTitle(), jobDescription, result);
        boolean combined = semantic.available();
        int overall = combined
            ? (int) Math.round(keywordScore * 0.6 + semantic.semanticMatch() * 0.4)
            : keywordScore;

        JobMatchAnalysis match = new JobMatchAnalysis();
        match.setUser(user);
        match.setResume(resume);
        match.setJobTitle(blankToNull(request.jobTitle()));
        match.setCompany(blankToNull(request.company()));
        match.setJobDescription(jobDescription);
        match.setEngine(combined ? JobMatchRuleEngine.ENGINE + "+GEMINI" : JobMatchRuleEngine.ENGINE);
        match.setOverallMatchScore(overall);
        match.setKeywordCoverage(result.keywordCoverage());
        match.setExperienceAlignment(result.experienceAlignment());
        match.setEducationAlignment(result.educationAlignment());
        match.setMatchedSkillsJson(objectMapper.writeValueAsString(result.matchedSkills()));
        match.setMissingSkillsJson(objectMapper.writeValueAsString(result.missingSkills()));
        match.setRecommendationsJson(objectMapper.writeValueAsString(result.recommendations()));
        match.setDetailsJson(objectMapper.writeValueAsString(new Details(
            result.label(), result.disclaimer(), result.skillCoverage(),
            result.matchedKeywords(), result.missingKeywords(), result.experienceNote(), result.educationNote(),
            keywordScore, combined ? JobMatchResponse.COMBINED_METHOD : JobMatchResponse.KEYWORD_ONLY_METHOD, semantic
        )));

        return toResponse(jobMatchRepository.save(match), true);
    }

    @Transactional(readOnly = true)
    public List<JobMatchResponse> list(User user) {
        return jobMatchRepository.findTop50ByUserIdOrderByCreatedAtDescIdDesc(user.getId()).stream()
            .map(match -> toResponse(match, false))
            .toList();
    }

    @Transactional(readOnly = true)
    public JobMatchResponse get(User user, Long matchId) {
        return jobMatchRepository.findByIdAndUserId(matchId, user.getId())
            .map(match -> toResponse(match, true))
            .orElseThrow(() -> new ResumeNotFoundException("Job match not found"));
    }

    /** Trimmed job description of meaningful length (shared with interview preparation). */
    public static String requireJobDescription(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.length() < MIN_JOB_DESCRIPTION_LENGTH) {
            throw new InvalidResumeRequestException(
                "Please paste the full job description (at least " + MIN_JOB_DESCRIPTION_LENGTH + " characters)."
            );
        }
        return text;
    }

    private JobMatchResponse toResponse(JobMatchAnalysis match, boolean includeDescription) {
        Details details = match.getDetailsJson() == null
            ? new Details(JobMatchRuleEngine.LABEL, JobMatchRuleEngine.DISCLAIMER, null, List.of(), List.of(), null, null, null, null, null)
            : objectMapper.readValue(match.getDetailsJson(), Details.class);

        Resume resume = match.getResume();

        return new JobMatchResponse(
            match.getId(),
            resume.getId(),
            resume.getTitle(),
            match.getJobTitle(),
            match.getCompany(),
            match.getEngine(),
            details.label(),
            details.disclaimer(),
            match.getOverallMatchScore(),
            // Rows saved before the AI layer have no separate keyword score: overall was keyword-only.
            details.keywordMatchScore() != null ? details.keywordMatchScore() : match.getOverallMatchScore(),
            details.semanticFeedback() != null && details.semanticFeedback().available() ? details.semanticFeedback().semanticMatch() : null,
            details.scoringMethod() != null ? details.scoringMethod() : JobMatchResponse.KEYWORD_ONLY_METHOD,
            match.getKeywordCoverage(),
            details.skillCoverage(),
            readList(match.getMatchedSkillsJson()),
            readList(match.getMissingSkillsJson()),
            details.matchedKeywords(),
            details.missingKeywords(),
            match.getExperienceAlignment(),
            details.experienceNote(),
            match.getEducationAlignment(),
            details.educationNote(),
            readList(match.getRecommendationsJson()),
            details.semanticFeedback(),
            includeDescription ? match.getJobDescription() : null,
            match.getCreatedAt()
        );
    }

    /** AI semantic comparison, or an UNAVAILABLE marker; never throws for AI problems. */
    private SemanticFeedback semanticFeedback(User user, String resumeText, String jobTitle, String jobDescription, JobMatchResponse rules) {
        AiOutcome<JobMatchSemantic> outcome = usageService.tryRun(user, FEATURE, () ->
            responseParser.parseJobMatchSemantic(aiProvider.generateJson(promptService.jobMatchSemantic(
                resumeText, jobTitle, jobDescription, rules.matchedSkills(), rules.missingSkills()))));

        if (!outcome.succeeded()) {
            return SemanticFeedback.unavailable("AI semantic feedback temporarily unavailable: " + outcome.reason());
        }

        JobMatchSemantic ai = outcome.value();
        return new SemanticFeedback(
            "AVAILABLE",
            "AI interpretation of how your resume content fits this job. It only uses what your resume says.",
            ai.semanticMatch(), ai.strengths(), ai.gaps(), ai.tailoringSuggestions(), ai.summary()
        );
    }

    private List<String> readList(String json) {
        return json == null ? List.of() : objectMapper.readValue(json, STRING_LIST);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
