package com.careerai.backend.service;

import com.careerai.backend.billing.FeatureKey;
import com.careerai.backend.billing.EntitlementService;
import com.careerai.backend.dto.ResumeImprovementRequest;
import com.careerai.backend.dto.ResumeImprovementResponse;
import com.careerai.backend.entity.Resume;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.InvalidResumeRequestException;
import com.careerai.backend.service.AiUsageService.AiOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * POST /api/resumes/improve.
 * Uses GeminiResumeImprovementEngine when AI is available; on any AI problem (not configured,
 * 429/503 after retries, timeout, invalid or fact-adding output) it falls back to
 * BasicWordingImprovementEngine and says why. The feature never fails because of AI.
 */
@Service
@RequiredArgsConstructor
public class ResumeImprovementService {

    public static final String FEATURE = "RESUME_IMPROVEMENT";
    static final int MAX_SECTION_TEXT = 5000;
    static final int MAX_FULL_RESUME_TEXT = 15000;

    private final EntitlementService entitlementService;
    private final ResumeService resumeService;
    private final BasicWordingImprovementEngine basicEngine;
    private final GeminiResumeImprovementEngine geminiEngine;
    private final AiUsageService usageService;

    public ResumeImprovementResponse improve(User user, ResumeImprovementRequest request) {
        // Counted once per completed improvement (AI or the rule-based fallback); a failed request gives the use back.
        return entitlementService.consume(user, FeatureKey.RESUME_AI_IMPROVEMENT, () -> doImprove(user, request));
    }

    private ResumeImprovementResponse doImprove(User user, ResumeImprovementRequest request) {
        String section = normalizeSection(request.section());

        // An optional resume id must belong to the caller (404 otherwise).
        Resume resume = request.resumeId() == null ? null : resumeService.requireOwnedResume(user, request.resumeId());
        String text = resolveText(request.text(), resume, section);

        AiOutcome<ResumeImprovementEngine.Result> ai = usageService.tryRun(user, FEATURE, () -> geminiEngine.improve(text, section));

        if (ai.succeeded()) {
            ResumeImprovementEngine.Result result = ai.value();
            return new ResumeImprovementResponse(
                geminiEngine.id(), true, "AI-assisted improvement",
                "Review every change before using it. The AI was instructed not to add facts, and new numbers or technologies are rejected automatically.",
                null, request.resumeId(), section, text,
                result.improvedText(), result.changes(), result.warnings(), result.suggestions()
            );
        }

        ResumeImprovementEngine.Result basic = basicEngine.improve(text, section);
        return new ResumeImprovementResponse(
            basicEngine.id(), false, "Basic wording assistance",
            "AI improvement was not used: " + ai.reason() + " Showing basic wording assistance (spacing, capitalisation, punctuation).",
            ai.errorCode(), request.resumeId(), section, text,
            basic.improvedText(), basic.changes(), basic.warnings(), basic.suggestions()
        );
    }

    /** Text from the request, or the stored resume text for FULL_RESUME when no text is sent. */
    private String resolveText(String requested, Resume resume, String section) {
        String text = requested == null ? "" : requested.strip();

        if (text.isEmpty() && resume != null && "FULL_RESUME".equals(section)) {
            text = resumeService.requireText(resume);
        }
        if (text.isEmpty()) {
            throw new InvalidResumeRequestException("Enter the text to improve, or choose a saved resume and the Full resume section.");
        }

        int max = "FULL_RESUME".equals(section) ? MAX_FULL_RESUME_TEXT : MAX_SECTION_TEXT;
        if (text.length() > max) {
            throw new InvalidResumeRequestException("Text is too long for this section (maximum " + max + " characters).");
        }
        return text;
    }

    private static String normalizeSection(String section) {
        if (section == null || section.isBlank()) return "GENERAL";
        return "PROJECTS".equals(section) ? "PROJECT" : section;
    }
}
