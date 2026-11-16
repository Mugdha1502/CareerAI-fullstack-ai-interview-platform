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

/** Curated SQL datasets and questions from classpath:practice/sql-questions.json (loaded once). */
@Component
public class SqlQuestionCatalog {

    private final Map<String, SqlQuestion.Dataset> datasets = new LinkedHashMap<>();
    private final Map<String, SqlQuestion> questions = new LinkedHashMap<>();

    public SqlQuestionCatalog(ObjectMapper objectMapper) throws IOException {
        try (InputStream in = new ClassPathResource("practice/sql-questions.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            for (JsonNode node : root.path("datasets")) {
                SqlQuestion.Dataset dataset = objectMapper.treeToValue(node, SqlQuestion.Dataset.class);
                datasets.put(dataset.name(), dataset);
            }
            for (JsonNode node : root.path("questions")) {
                SqlQuestion question = objectMapper.treeToValue(node, SqlQuestion.class);
                if (!datasets.containsKey(question.dataset())) {
                    throw new IllegalStateException("SQL question " + question.id() + " uses an unknown dataset");
                }
                questions.put(question.id(), question);
            }
        }
    }

    public Collection<SqlQuestion> all() {
        return questions.values();
    }

    public SqlQuestion require(String id) {
        SqlQuestion question = id == null ? null : questions.get(id.trim());
        if (question == null) {
            throw new PracticeExecutionException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "SQL question not found.");
        }
        return question;
    }

    public SqlQuestion requireByDatasetAndTopic(String dataset, String topic) {
        String datasetKey = dataset == null ? "" : dataset.trim().toLowerCase(Locale.ROOT);
        String topicKey = topic == null ? "" : topic.trim().toLowerCase(Locale.ROOT);
        return questions.values().stream()
            .filter(question -> question.dataset().toLowerCase(Locale.ROOT).equals(datasetKey)
                && question.topic().toLowerCase(Locale.ROOT).equals(topicKey))
            .findFirst()
            .orElseThrow(() -> new PracticeExecutionException(HttpStatus.NOT_FOUND, "QUESTION_NOT_FOUND", "No SQL question for this dataset and topic."));
    }

    public SqlQuestion.Dataset dataset(SqlQuestion question) {
        return datasets.get(question.dataset());
    }
}
