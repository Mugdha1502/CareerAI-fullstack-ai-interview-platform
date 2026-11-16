package com.careerai.backend.ai;

import com.careerai.backend.dto.AiQuestionsResponse.Question;
import com.careerai.backend.dto.ResumeImprovementResponse.Change;
import com.careerai.backend.dto.SystemDesignEvaluationResponse.RubricScore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns model JSON into validated values. Nothing the model writes reaches the browser
 * unless it passes these checks: required fields present, text non-empty and bounded,
 * exactly four distinct options, the correct answer among the options, scores 0-100.
 * Individual bad questions are dropped; a response with too few usable items is rejected.
 */
@Component
@RequiredArgsConstructor
public class AiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AiResponseParser.class);

    public static final int MCQ_OPTION_COUNT = 4;

    private final ObjectMapper objectMapper;

    /** Parsed evaluation of one descriptive answer. */
    public record Evaluation(int score, List<String> strengths, List<String> missingPoints, String feedback, String suggestedAnswer) {
    }

    /** Parsed system design review (overall score computed from the rubric). */
    public record DesignReview(int overallScore, List<RubricScore> rubric, List<String> strengths, List<String> recommendations, String summary) {
    }


    /* ---------- Questions ---------- */

    public List<Question> parseMcqs(String json, int requested) {
        JsonNode items = readArray(readObject(json), "questions");
        List<Question> questions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int rejected = 0;

        for (JsonNode item : items) {
            try {
                Question question = toMcq(item);
                if (seen.add(fingerprint(question.question()))) {
                    questions.add(question);
                } else {
                    rejected++;
                }
            } catch (AiResponseInvalidException ex) {
                rejected++;
                log.info("Dropped invalid AI MCQ: {}", ex.getMessage());
            }
        }

        return requireEnough(questions, requested, rejected, "MCQ");
    }

    public List<Question> parseDescriptive(String json, int requested) {
        JsonNode items = readArray(readObject(json), "questions");
        List<Question> questions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int rejected = 0;

        for (JsonNode item : items) {
            try {
                String question = text(item, "question", 10, 600);
                List<String> keyPoints = textList(item, "expectedKeyPoints", 2, 8, 300);
                String reference = text(item, "referenceAnswer", 30, 3000);
                if (seen.add(fingerprint(question))) {
                    questions.add(Question.descriptive(question, keyPoints.subList(0, Math.min(6, keyPoints.size())), reference));
                } else {
                    rejected++;
                }
            } catch (AiResponseInvalidException ex) {
                rejected++;
                log.info("Dropped invalid AI descriptive question: {}", ex.getMessage());
            }
        }

        return requireEnough(questions, requested, rejected, "descriptive");
    }

    private Question toMcq(JsonNode item) {
        String question = text(item, "question", 10, 600);
        List<String> options = textList(item, "options", MCQ_OPTION_COUNT, MCQ_OPTION_COUNT, 300);

        Set<String> distinct = new HashSet<>();
        for (String option : options) {
            if (!distinct.add(option.toLowerCase(Locale.ROOT))) {
                throw new AiResponseInvalidException("duplicate options");
            }
        }

        String answer = text(item, "correctAnswer", 1, 300);
        String correct = matchOption(answer, options);
        String explanation = text(item, "explanation", 5, 1200);

        return Question.mcq(question, options, correct, explanation);
    }

    /** The option the answer refers to: exact text, case-insensitive text, or a lone letter A-D. */
    private static String matchOption(String answer, List<String> options) {
        for (String option : options) {
            if (option.equals(answer)) return option;
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(answer)) return option;
        }
        if (answer.matches("(?i)^[A-D][).]?$")) {
            return options.get(Character.toUpperCase(answer.charAt(0)) - 'A');
        }
        throw new AiResponseInvalidException("correctAnswer is not one of the options");
    }

    private static List<Question> requireEnough(List<Question> questions, int requested, int rejected, String kind) {
        int minimum = Math.max(1, (int) Math.ceil(requested / 2.0));
        if (questions.size() < minimum) {
            throw new AiResponseInvalidException(
                "only " + questions.size() + " usable " + kind + " questions of " + requested + " requested (" + rejected + " rejected)");
        }
        return List.copyOf(questions.subList(0, Math.min(requested, questions.size())));
    }


    /* ---------- Evaluations ---------- */

    public Evaluation parseEvaluation(String json) {
        JsonNode root = readObject(json);
        return new Evaluation(
            score(root, "score"),
            textList(root, "strengths", 0, 8, 400),
            textList(root, "missingPoints", 0, 8, 400),
            text(root, "feedback", 5, 2000),
            text(root, "suggestedAnswer", 10, 4000)
        );
    }

    public DesignReview parseDesignReview(String json, Map<String, String> criteria, Map<String, String> labels) {
        JsonNode root = readObject(json);
        JsonNode items = readArray(root, "rubric");

        Map<String, RubricScore> byCriterion = new LinkedHashMap<>();
        for (JsonNode item : items) {
            String criterion = text(item, "criterion", 1, 40).toLowerCase(Locale.ROOT);
            if (!criteria.containsKey(criterion)) {
                throw new AiResponseInvalidException("unknown rubric criterion " + criterion);
            }
            if (byCriterion.containsKey(criterion)) {
                throw new AiResponseInvalidException("duplicate rubric criterion " + criterion);
            }
            byCriterion.put(criterion, new RubricScore(criterion, labels.get(criterion), score(item, "score"), text(item, "feedback", 5, 1200)));
        }

        List<RubricScore> rubric = new ArrayList<>();
        for (String criterion : criteria.keySet()) {
            RubricScore score = byCriterion.get(criterion);
            if (score == null) {
                throw new AiResponseInvalidException("missing rubric criterion " + criterion);
            }
            rubric.add(score);
        }

        int overall = (int) Math.round(rubric.stream().mapToInt(RubricScore::score).average().orElse(0));

        return new DesignReview(
            overall,
            List.copyOf(rubric),
            textList(root, "strengths", 0, 8, 400),
            textList(root, "recommendations", 1, 8, 500),
            text(root, "summary", 5, 2000)
        );
    }


    /* ---------- Resume ---------- */

    public record ResumeImprovement(String improvedText, List<Change> changes, List<String> warnings) {
    }

    public record AtsFeedback(List<String> strengths, List<String> issues, List<String> recommendations,
                              int writingQualityScore, String summary) {
    }

    public record JobMatchSemantic(int semanticMatch, List<String> strengths, List<String> gaps,
                                   List<String> tailoringSuggestions, String summary) {
    }

    public record InterviewPreparation(List<String> focusTopics, List<String> likelyResumeQuestions,
                                       List<String> projectQuestions, List<String> experienceQuestions,
                                       List<String> skillQuestions, List<String> potentialWeakAreas) {
    }

    public ResumeImprovement parseResumeImprovement(String json, int maxLength) {
        JsonNode root = readObject(json);
        String improved = text(root, "improvedText", 1, maxLength);

        JsonNode items = readArray(root, "changes");
        List<Change> changes = new ArrayList<>();
        for (JsonNode item : items) {
            String type = text(item, "type", 1, 30).toUpperCase(Locale.ROOT);
            if (!AiPromptService.IMPROVEMENT_CHANGE_TYPES.contains(type)) {
                throw new AiResponseInvalidException("unknown change type " + type);
            }
            changes.add(new Change(type, text(item, "description", 3, 300)));
        }
        if (changes.size() > 10) {
            throw new AiResponseInvalidException("too many changes");
        }

        return new ResumeImprovement(improved, List.copyOf(changes), textList(root, "warnings", 0, 8, 300));
    }

    public AtsFeedback parseAtsFeedback(String json) {
        JsonNode root = readObject(json);
        return new AtsFeedback(
            textList(root, "strengths", 1, 8, 400),
            textList(root, "issues", 1, 10, 400),
            textList(root, "recommendations", 1, 10, 400),
            score(root, "writingQualityScore"),
            text(root, "summary", 5, 1500)
        );
    }

    public JobMatchSemantic parseJobMatchSemantic(String json) {
        JsonNode root = readObject(json);
        return new JobMatchSemantic(
            score(root, "semanticMatch"),
            textList(root, "strengths", 1, 8, 400),
            textList(root, "gaps", 1, 8, 400),
            textList(root, "tailoringSuggestions", 1, 8, 400),
            text(root, "summary", 5, 1500)
        );
    }

    public InterviewPreparation parseInterviewPreparation(String json) {
        JsonNode root = readObject(json);
        return new InterviewPreparation(
            textList(root, "focusTopics", 1, 10, 80),
            textList(root, "likelyResumeQuestions", 1, 8, 400),
            textList(root, "projectQuestions", 0, 8, 400),
            textList(root, "experienceQuestions", 0, 8, 400),
            textList(root, "skillQuestions", 1, 8, 400),
            textList(root, "potentialWeakAreas", 0, 8, 300)
        );
    }


    /* ---------- Coach and interview ---------- */

    public record CoachAction(String label, String route) {
    }

    public record CoachReply(String message, List<CoachAction> actions) {
    }

    public record InterviewQuestionDraft(String question, String questionType, String topic, String difficulty,
                                         String intent, List<String> expectedKeyPoints) {
    }

    /** nextQuestion is null when it was not requested or failed validation (the caller then uses a fallback). */
    public record InterviewTurn(int score, List<String> strengths, List<String> missingPoints, String feedback,
                                String idealAnswer, boolean followUpRequired, InterviewQuestionDraft nextQuestion) {
    }

    public record InterviewReportDraft(String summary, List<String> strongAreas, List<String> weakAreas,
                                       List<String> recommendedSkills, List<String> nextSteps, int writtenCommunicationScore) {
    }

    private static final Set<String> QUESTION_TYPES = Set.of("TECHNICAL", "BEHAVIORAL", "HR", "RESUME", "JOB_SPECIFIC");

    /** Route syntax only; CoachService additionally checks routes against its allow-list. */
    private static final java.util.regex.Pattern SAFE_ROUTE = java.util.regex.Pattern.compile("^/[A-Za-z0-9/_\\-?=&%.]{0,199}$");

    public CoachReply parseCoachReply(String json) {
        JsonNode root = readObject(json);
        String message = text(root, "message", 2, 4000);

        List<CoachAction> actions = new ArrayList<>();
        JsonNode items = root.path("suggestedActions");
        if (items.isArray()) {
            for (JsonNode item : items) {
                // Invalid buttons are dropped rather than failing the whole reply.
                if (!item.path("label").isString() || !item.path("route").isString()) continue;
                String label = item.path("label").asString().strip();
                String route = item.path("route").asString().strip();
                if (label.isEmpty() || label.length() > 60 || !SAFE_ROUTE.matcher(route).matches() || route.contains("//")) continue;
                actions.add(new CoachAction(label, route));
                if (actions.size() == 3) break;
            }
        }
        return new CoachReply(message, List.copyOf(actions));
    }

    public InterviewQuestionDraft parseInterviewQuestion(String json) {
        return toQuestionDraft(readObject(json));
    }

    public InterviewTurn parseInterviewTurn(String json, boolean nextRequired) {
        JsonNode root = readObject(json);
        int score = score(root, "score");
        List<String> strengths = textList(root, "strengths", 0, 8, 400);
        List<String> missing = textList(root, "missingPoints", 0, 8, 400);
        String feedback = text(root, "feedback", 5, 2000);
        String ideal = text(root, "idealAnswer", 10, 3000);
        boolean followUp = root.path("followUpRequired").asBoolean(false);

        InterviewQuestionDraft next = null;
        if (nextRequired && root.path("nextQuestion").isObject()) {
            try {
                next = toQuestionDraft(root.path("nextQuestion"));
            } catch (AiResponseInvalidException ex) {
                log.info("AI next interview question rejected: {}", ex.getMessage());
            }
        }
        return new InterviewTurn(score, strengths, missing, feedback, ideal, followUp, next);
    }

    public InterviewReportDraft parseInterviewReport(String json) {
        JsonNode root = readObject(json);
        return new InterviewReportDraft(
            text(root, "summary", 10, 2000),
            textList(root, "strongAreas", 0, 6, 120),
            textList(root, "weakAreas", 0, 6, 120),
            textList(root, "recommendedSkills", 0, 6, 80),
            textList(root, "nextSteps", 1, 6, 400),
            score(root, "writtenCommunicationScore")
        );
    }

    public record VoiceQuestionEvaluation(int score, List<String> strengths, List<String> missingPoints,
                                          String feedback, String idealAnswer) {
    }

    /** questions are in transcript order (validated: exactly one entry per question, indices 1..n). */
    public record VoiceEvaluation(List<VoiceQuestionEvaluation> questions, String summary, List<String> strongAreas,
                                  List<String> weakAreas, List<String> recommendedSkills, List<String> nextSteps) {
    }

    public VoiceEvaluation parseVoiceEvaluation(String json, int questionCount) {
        JsonNode root = readObject(json);
        JsonNode items = readArray(root, "questions");
        if (items.size() != questionCount) {
            throw new AiResponseInvalidException("expected " + questionCount + " question evaluations, got " + items.size());
        }

        VoiceQuestionEvaluation[] ordered = new VoiceQuestionEvaluation[questionCount];
        for (JsonNode item : items) {
            int index = score(item, "index");
            if (index < 1 || index > questionCount || ordered[index - 1] != null) {
                throw new AiResponseInvalidException("invalid or duplicate question index " + index);
            }
            ordered[index - 1] = new VoiceQuestionEvaluation(
                score(item, "score"),
                textList(item, "strengths", 0, 8, 400),
                textList(item, "missingPoints", 0, 8, 400),
                text(item, "feedback", 5, 2000),
                text(item, "idealAnswer", 10, 3000)
            );
        }

        return new VoiceEvaluation(
            List.of(ordered),
            text(root, "summary", 10, 2000),
            textList(root, "strongAreas", 0, 6, 120),
            textList(root, "weakAreas", 0, 6, 120),
            textList(root, "recommendedSkills", 0, 6, 80),
            textList(root, "nextSteps", 1, 6, 400)
        );
    }

    private static InterviewQuestionDraft toQuestionDraft(JsonNode node) {
        String type = text(node, "questionType", 2, 20).toUpperCase(Locale.ROOT);
        if (!QUESTION_TYPES.contains(type)) {
            throw new AiResponseInvalidException("unknown question type " + type);
        }
        return new InterviewQuestionDraft(
            text(node, "question", 10, 800),
            type,
            text(node, "topic", 1, 80),
            text(node, "difficulty", 1, 20),
            text(node, "intent", 1, 300),
            textList(node, "expectedKeyPoints", 1, 8, 200)
        );
    }


    /* ---------- JSON helpers ---------- */

    /** Parses the model output as one JSON object, tolerating a stray code fence around it. */
    private JsonNode readObject(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiResponseInvalidException("empty response");
        }

        String text = raw.strip();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }

        try {
            JsonNode node = objectMapper.readTree(text);
            if (node == null || !node.isObject()) {
                throw new AiResponseInvalidException("response is not a JSON object");
            }
            return node;
        } catch (JacksonException ex) {
            throw new AiResponseInvalidException("response is not valid JSON", ex);
        }
    }

    private static JsonNode readArray(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isArray() || node.isEmpty()) {
            throw new AiResponseInvalidException("missing or empty array " + field);
        }
        return node;
    }

    private static String text(JsonNode node, String field, int min, int max) {
        JsonNode value = node.path(field);
        if (!value.isString()) {
            throw new AiResponseInvalidException("missing text field " + field);
        }
        String text = value.asString().strip();
        if (text.length() < min) {
            throw new AiResponseInvalidException("field " + field + " is empty or too short");
        }
        if (text.length() > max) {
            throw new AiResponseInvalidException("field " + field + " is too long");
        }
        return text;
    }

    private static List<String> textList(JsonNode node, String field, int min, int max, int maxLength) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw new AiResponseInvalidException("missing list field " + field);
        }

        List<String> result = new ArrayList<>();
        for (JsonNode element : value) {
            if (!element.isString() || element.asString().isBlank()) {
                throw new AiResponseInvalidException("blank entry in " + field);
            }
            String text = element.asString().strip();
            if (text.length() > maxLength) {
                throw new AiResponseInvalidException("entry in " + field + " is too long");
            }
            result.add(text);
        }

        if (result.size() < min || result.size() > max) {
            throw new AiResponseInvalidException(field + " has " + result.size() + " entries (expected " + min + "-" + max + ")");
        }
        return List.copyOf(result);
    }

    /** Score 0-100 (rounded); a whole number written as a string ("78") is accepted. */
    private static int score(JsonNode node, String field) {
        JsonNode value = node.path(field);
        double number;

        if (value.isNumber()) {
            number = value.asDouble();
        } else if (value.isString() && value.asString().strip().matches("\\d{1,3}")) {
            number = Integer.parseInt(value.asString().strip());
        } else {
            throw new AiResponseInvalidException("missing numeric field " + field);
        }

        if (Double.isNaN(number) || number < 0 || number > 100) {
            throw new AiResponseInvalidException("field " + field + " is out of range: " + number);
        }
        return (int) Math.round(number);
    }

    private static String fingerprint(String question) {
        return question.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }
}
