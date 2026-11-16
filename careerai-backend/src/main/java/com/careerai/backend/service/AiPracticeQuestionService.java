package com.careerai.backend.service;

import com.careerai.backend.ai.AiPromptService;
import com.careerai.backend.ai.AiProvider;
import com.careerai.backend.ai.AiRequest;
import com.careerai.backend.ai.AiResponseParser;
import com.careerai.backend.dto.AiQuestionRequest;
import com.careerai.backend.dto.AiQuestionsResponse;
import com.careerai.backend.dto.AiQuestionsResponse.Question;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.InvalidAiRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Generates practice questions (technical and aptitude; MCQ and descriptive) through the
 * configured AiProvider. Every response is validated by AiResponseParser before it is
 * returned; nothing is stored. Questions only test knowledge; they never decide whether
 * code or SQL runs correctly.
 */
@Service
@RequiredArgsConstructor
public class AiPracticeQuestionService {

    public static final String SOURCE = "AI";
    static final int DEFAULT_MCQ_COUNT = 10;
    static final int MAX_DESCRIPTIVE_COUNT = 5;

    private final AiProvider aiProvider;
    private final AiPromptService promptService;
    private final AiResponseParser responseParser;
    private final AiUsageService usageService;
    private final PracticeTopicCatalog topicCatalog;

    public AiQuestionsResponse generate(User user, AiQuestionRequest request) {
        String module = request.module();
        String topic = topicCatalog.requireTopic(module, request.topic());
        String difficulty = request.difficulty();
        String type = request.questionType().toUpperCase(Locale.ROOT);
        boolean descriptive = "DESCRIPTIVE".equals(type);

        if (descriptive && !"technical".equals(module)) {
            throw new InvalidAiRequestException("Descriptive questions are available for the technical module only.");
        }

        int count = request.count() == null ? (descriptive ? 3 : DEFAULT_MCQ_COUNT) : request.count();
        if (descriptive && count > MAX_DESCRIPTIVE_COUNT) {
            throw new InvalidAiRequestException("Request at most " + MAX_DESCRIPTIVE_COUNT + " descriptive questions at a time.");
        }

        AiRequest aiRequest = descriptive
            ? promptService.descriptiveQuestions(module, topic, difficulty, count)
            : promptService.mcqQuestions(module, topic, difficulty, count);

        String feature = descriptive ? "PRACTICE_QUESTIONS_DESCRIPTIVE" : "PRACTICE_QUESTIONS_MCQ";

        List<Question> questions = usageService.run(user, feature, () -> {
            String json = aiProvider.generateJson(aiRequest);
            return descriptive
                ? responseParser.parseDescriptive(json, count)
                : responseParser.parseMcqs(json, count);
        });

        return new AiQuestionsResponse(
            SOURCE, aiProvider.name(), aiProvider.model(),
            module, topic, difficulty, type, count, questions
        );
    }
}
