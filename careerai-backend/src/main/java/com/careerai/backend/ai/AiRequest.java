package com.careerai.backend.ai;

import java.util.Map;

/**
 * Provider-neutral request for one JSON-producing generation.
 *
 * @param systemInstruction role, rules and output format (built by AiPromptService)
 * @param userPrompt        the task, with user-supplied text clearly delimited as data
 * @param responseSchema    optional JSON schema (OpenAPI subset) the output must follow
 * @param temperature       0.0-1.0; low for evaluation, higher for question variety
 * @param maxOutputTokens   upper bound on the response length
 */
public record AiRequest(
    String systemInstruction,
    String userPrompt,
    Map<String, Object> responseSchema,
    double temperature,
    int maxOutputTokens
) {
}
