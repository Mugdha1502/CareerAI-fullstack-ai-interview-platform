package com.careerai.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Validated AI-generated practice questions. MCQ items fill question/options/correctAnswer/explanation;
 * descriptive items fill question/expectedKeyPoints/referenceAnswer.
 */
public record AiQuestionsResponse(
    String source,
    String provider,
    String model,
    String module,
    String topic,
    String difficulty,
    String questionType,
    int requestedCount,
    List<Question> questions
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Question(
        String question,
        List<String> options,
        String correctAnswer,
        String explanation,
        List<String> expectedKeyPoints,
        String referenceAnswer
    ) {

        public static Question mcq(String question, List<String> options, String correctAnswer, String explanation) {
            return new Question(question, options, correctAnswer, explanation, null, null);
        }

        public static Question descriptive(String question, List<String> keyPoints, String referenceAnswer) {
            return new Question(question, null, null, null, keyPoints, referenceAnswer);
        }
    }
}
