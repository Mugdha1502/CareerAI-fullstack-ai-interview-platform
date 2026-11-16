package com.careerai.backend.service;

import com.careerai.backend.dto.ResumeImprovementResponse.Change;

import java.util.List;

/**
 * Rewrites resume text for one section. Implementations: GeminiResumeImprovementEngine
 * (AI, preferred when configured) and BasicWordingImprovementEngine (rules, always available,
 * used as the fallback). ResumeImprovementService chooses between them.
 *
 * Every implementation must preserve facts: never add experience, metrics, technologies,
 * certifications or achievements that are not in the input.
 */
public interface ResumeImprovementEngine {

    /** Engine identifier returned to the client, e.g. BASIC_RULES or GEMINI. */
    String id();

    /** True only for an engine backed by an AI provider. */
    boolean isAi();

    Result improve(String text, String section);

    record Result(String improvedText, List<Change> changes, List<String> warnings, List<String> suggestions) {
    }
}
