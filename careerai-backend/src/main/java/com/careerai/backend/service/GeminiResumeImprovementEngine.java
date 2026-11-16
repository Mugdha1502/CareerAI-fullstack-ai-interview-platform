package com.careerai.backend.service;

import com.careerai.backend.ai.AiPromptService;
import com.careerai.backend.ai.AiProvider;
import com.careerai.backend.ai.AiResponseInvalidException;
import com.careerai.backend.ai.AiResponseParser;
import com.careerai.backend.ai.AiResponseParser.ResumeImprovement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI resume rewriting through the shared AiProvider (Gemini). The prompt forbids new facts;
 * this engine also enforces that on the server before anything is returned:
 * - no number, percentage or metric that is not in the original text,
 * - no recognised technology/skill that is not in the original text,
 * - no large growth in length (a sign of added content).
 * A violation is treated as an invalid AI response, so the caller falls back to basic rules.
 */
@Component
@RequiredArgsConstructor
public class GeminiResumeImprovementEngine implements ResumeImprovementEngine {

    public static final String ID = "GEMINI";

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    private final AiProvider aiProvider;
    private final AiPromptService promptService;
    private final AiResponseParser responseParser;
    private final ResumeTextAnalyzer analyzer;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAi() {
        return true;
    }

    public boolean isAvailable() {
        return aiProvider.isConfigured();
    }

    @Override
    public Result improve(String text, String section) {
        int maxLength = Math.max(400, (int) (text.length() * 1.6) + 200);
        ResumeImprovement improvement = responseParser.parseResumeImprovement(
            aiProvider.generateJson(promptService.resumeImprovement(section, text)), maxLength);

        verifyNoNewFacts(text, improvement.improvedText());

        return new Result(improvement.improvedText(), improvement.changes(), improvement.warnings(), List.of());
    }

    /** Rejects rewrites that introduce numbers or technologies absent from the original. */
    void verifyNoNewFacts(String original, String improved) {
        Set<String> originalNumbers = numbers(original);
        for (String number : numbers(improved)) {
            if (!originalNumbers.contains(number)) {
                throw new AiResponseInvalidException("improved text added a number not in the original: " + number);
            }
        }

        Set<String> originalSkills = new HashSet<>(analyzer.findSkills(original));
        for (String skill : analyzer.findSkills(improved)) {
            if (!originalSkills.contains(skill)) {
                throw new AiResponseInvalidException("improved text added a technology not in the original: " + skill);
            }
        }
    }

    private static Set<String> numbers(String text) {
        Set<String> result = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group().replace(',', '.'));
        }
        return result;
    }
}
