package com.careerai.backend.practice;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Curated coding questions from classpath:practice/coding-questions.json (loaded once). */
@Component
public class CodingQuestionCatalog {

    private final Map<String, CodingQuestion> byId = new LinkedHashMap<>();

    public CodingQuestionCatalog(ObjectMapper objectMapper) throws IOException {
        try (InputStream in = new ClassPathResource("practice/coding-questions.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            for (JsonNode node : root.path("questions")) {
                CodingQuestion question = objectMapper.treeToValue(node, CodingQuestion.class);
                byId.put(question.id(), question);
            }
        }
    }

    public Collection<CodingQuestion> all() {
        return byId.values();
    }

    public CodingQuestion require(String id) {
        CodingQuestion question = id == null ? null : byId.get(id.trim());
        if (question == null) {
            throw new PracticeExecutionException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "Coding question not found.");
        }
        return question;
    }

    public CodingQuestion requireByTopic(String topic) {
        String key = topic == null ? "" : topic.trim().toLowerCase(Locale.ROOT);
        return byId.values().stream()
            .filter(question -> question.topic().toLowerCase(Locale.ROOT).equals(key))
            .findFirst()
            .orElseThrow(() -> new PracticeExecutionException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "No coding question for this topic."));
    }
}
