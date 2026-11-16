package com.careerai.backend.service;

import com.careerai.backend.billing.FeatureKey;
import com.careerai.backend.billing.EntitlementService;
import com.careerai.backend.dto.ResumeAnalysisResponse;
import com.careerai.backend.dto.ResumeAnalysisResponse.SemanticFeedback;
import com.careerai.backend.entity.Resume;
import com.careerai.backend.entity.ResumeAnalysis;
import com.careerai.backend.entity.ResumeAnalysisType;
import com.careerai.backend.entity.User;
import com.careerai.backend.repository.ResumeAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Runs and stores ATS readiness analyses for the caller's own resumes.
 * The scoring itself lives in AtsRuleEngine; a future AI feedback step plugs in here,
 * never in the controller.
 */
@Service
@RequiredArgsConstructor
public class ATSAnalysisService {

    private final EntitlementService entitlementService;
    private final ResumeService resumeService;
    private final AtsRuleEngine atsRuleEngine;
    private final ResumeAnalysisRepository analysisRepository;
    private final GeminiAtsAnalysisService semanticService;
    private final ObjectMapper objectMapper;

    /**
     * Rule checks first, then AI semantic feedback (optional), then the combined result is stored.
     * Not transactional: no database connection is held while the AI provider is called.
     */
    public ResumeAnalysisResponse analyze(User user, Long resumeId) {
        // Counted once per completed analysis (rule-based and/or AI); a failed request gives the use back.
        return entitlementService.consume(user, FeatureKey.RESUME_ATS_ANALYSIS, () -> doAnalyze(user, resumeId));
    }

    private ResumeAnalysisResponse doAnalyze(User user, Long resumeId) {
        Resume resume = resumeService.requireOwnedResume(user, resumeId);
        String text = resumeService.requireText(resume);

        ResumeAnalysisResponse rules = atsRuleEngine.analyze(text);
        SemanticFeedback semantic = semanticService.feedback(user, text);
        ResumeAnalysisResponse result = rules.withSemantic(
            semantic, semantic.available() ? AtsRuleEngine.ENGINE + "+GEMINI" : AtsRuleEngine.ENGINE);

        ResumeAnalysis analysis = new ResumeAnalysis();
        analysis.setResume(resume);
        analysis.setAnalysisType(ResumeAnalysisType.ATS);
        analysis.setEngine(result.engine());
        analysis.setOverallScore(result.overallScore());
        analysis.setKeywordScore(result.keywordScore());
        analysis.setFormattingScore(result.formattingScore());
        analysis.setContentScore(result.contentScore());
        analysis.setExperienceScore(result.experienceScore());
        analysis.setResultJson(objectMapper.writeValueAsString(result));

        ResumeAnalysis saved = analysisRepository.save(analysis);
        return result.withMetadata(saved.getId(), resume.getId(), resume.getTitle(), saved.getAnalyzedAt());
    }

    /** Latest stored ATS analysis, or empty when the resume has not been analysed yet. */
    @Transactional(readOnly = true)
    public Optional<ResumeAnalysisResponse> latest(User user, Long resumeId) {
        Resume resume = resumeService.requireOwnedResume(user, resumeId);

        return analysisRepository
            .findTopByResumeIdAndAnalysisTypeOrderByAnalyzedAtDescIdDesc(resume.getId(), ResumeAnalysisType.ATS)
            .map(analysis -> objectMapper.readValue(analysis.getResultJson(), ResumeAnalysisResponse.class)
                .withMetadata(analysis.getId(), resume.getId(), resume.getTitle(), analysis.getAnalyzedAt()));
    }
}
