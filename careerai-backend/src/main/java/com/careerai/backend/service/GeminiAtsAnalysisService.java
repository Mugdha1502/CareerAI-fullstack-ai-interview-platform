package com.careerai.backend.service;

import com.careerai.backend.ai.AiPromptService;
import com.careerai.backend.ai.AiProvider;
import com.careerai.backend.ai.AiResponseParser;
import com.careerai.backend.ai.AiResponseParser.AtsFeedback;
import com.careerai.backend.dto.ResumeAnalysisResponse.SemanticFeedback;
import com.careerai.backend.entity.User;
import com.careerai.backend.repository.ProfileRepository;
import com.careerai.backend.service.AiUsageService.AiOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI semantic layer on top of AtsRuleEngine: qualitative feedback on clarity, impact, tone,
 * section and bullet quality, readability and role alignment. It never replaces the rule
 * checks; when AI is unavailable the result says so and the rule-based analysis stands alone.
 */
@Service
@RequiredArgsConstructor
public class GeminiAtsAnalysisService {

    public static final String FEATURE = "RESUME_ATS_ANALYSIS";

    private final AiProvider aiProvider;
    private final AiPromptService promptService;
    private final AiResponseParser responseParser;
    private final AiUsageService usageService;
    private final ProfileRepository profileRepository;

    /** Semantic feedback, or an UNAVAILABLE marker with a safe reason. Never throws for AI problems. */
    public SemanticFeedback feedback(User user, String resumeText) {
        String targetRole = profileRepository.findByUserId(user.getId())
            .map(profile -> profile.getTargetRole())
            .orElse(null);

        AiOutcome<AtsFeedback> outcome = usageService.tryRun(user, FEATURE, () ->
            responseParser.parseAtsFeedback(aiProvider.generateJson(promptService.atsSemanticFeedback(resumeText, targetRole))));

        if (!outcome.succeeded()) {
            return SemanticFeedback.unavailable("AI feedback temporarily unavailable: " + outcome.reason());
        }

        AtsFeedback ai = outcome.value();
        return new SemanticFeedback(
            "AVAILABLE",
            "AI-assisted feedback on writing quality. Objective checks above come from the rule engine.",
            ai.writingQualityScore(), ai.strengths(), ai.issues(), ai.recommendations(), ai.summary()
        );
    }
}
