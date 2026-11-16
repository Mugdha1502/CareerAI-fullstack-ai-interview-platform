package com.careerai.backend.service;

import com.careerai.backend.ai.AiPromptService;
import com.careerai.backend.ai.AiProvider;
import com.careerai.backend.ai.AiResponseParser;
import com.careerai.backend.ai.AiResponseParser.DesignReview;
import com.careerai.backend.ai.AiResponseParser.Evaluation;
import com.careerai.backend.dto.AiEvaluationRequest;
import com.careerai.backend.dto.AiEvaluationResponse;
import com.careerai.backend.dto.SystemDesignEvaluationRequest;
import com.careerai.backend.dto.SystemDesignEvaluationResponse;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.InvalidAiRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI evaluation of written practice answers: descriptive answers and system designs.
 * Uses the same AiProvider as question generation; results are validated before return.
 */
@Service
@RequiredArgsConstructor
public class AiPracticeEvaluationService {

    /** Workspace section ids accepted for system design, in display order. */
    static final List<String> DESIGN_SECTIONS = List.of(
        "requirements", "api", "database", "architecture", "caching", "scaling", "security", "tradeoffs"
    );

    private static final Map<String, String> CRITERION_LABELS = Map.of(
        "requirements", "Requirements",
        "api", "API Design",
        "database", "Database",
        "scalability", "Scalability",
        "security", "Security",
        "tradeoffs", "Trade-offs"
    );

    /** Below this many words in total there is nothing meaningful for a reviewer to assess. */
    private static final int MIN_DESIGN_WORDS = 15;

    private final AiProvider aiProvider;
    private final AiPromptService promptService;
    private final AiResponseParser responseParser;
    private final AiUsageService usageService;
    private final PracticeTopicCatalog topicCatalog;

    public AiEvaluationResponse evaluateAnswer(User user, AiEvaluationRequest request) {
        String topic = topicCatalog.requireTopic(request.module(), request.topic());

        Evaluation evaluation = usageService.run(user, "PRACTICE_EVALUATION", () ->
            responseParser.parseEvaluation(aiProvider.generateJson(promptService.descriptiveEvaluation(
                request.module(), topic, request.difficulty(), request.question(), request.answer()))));

        return new AiEvaluationResponse(
            AiPracticeQuestionService.SOURCE, aiProvider.name(), aiProvider.model(),
            evaluation.score(), evaluation.strengths(), evaluation.missingPoints(),
            evaluation.feedback(), evaluation.suggestedAnswer()
        );
    }

    public SystemDesignEvaluationResponse evaluateSystemDesign(User user, SystemDesignEvaluationRequest request) {
        String topic = topicCatalog.requireTopic("system-design", request.topic());

        Map<String, String> sections = new LinkedHashMap<>();
        int words = 0;
        for (String id : DESIGN_SECTIONS) {
            String text = request.sections().getOrDefault(id, "");
            text = text == null ? "" : text.strip();
            sections.put(id, text);
            words += text.isEmpty() ? 0 : text.split("\\s+").length;
        }

        for (String key : request.sections().keySet()) {
            if (!DESIGN_SECTIONS.contains(key)) {
                throw new InvalidAiRequestException("Unknown design section: " + key);
            }
        }

        if (words < MIN_DESIGN_WORDS) {
            throw new InvalidAiRequestException("Write a little more of your design (at least " + MIN_DESIGN_WORDS + " words) before requesting a review.");
        }

        DesignReview review = usageService.run(user, "SYSTEM_DESIGN_EVALUATION", () ->
            responseParser.parseDesignReview(
                aiProvider.generateJson(promptService.systemDesignEvaluation(topic, request.difficulty(), sections)),
                AiPromptService.SYSTEM_DESIGN_CRITERIA,
                CRITERION_LABELS));

        return new SystemDesignEvaluationResponse(
            AiPracticeQuestionService.SOURCE, aiProvider.name(), aiProvider.model(),
            review.overallScore(), review.rubric(), review.strengths(), review.recommendations(), review.summary()
        );
    }
}
