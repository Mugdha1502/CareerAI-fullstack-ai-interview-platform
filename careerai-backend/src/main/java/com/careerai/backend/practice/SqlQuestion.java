package com.careerai.backend.practice;

import java.util.List;

/**
 * A curated SQL question. referenceQuery and the dataset's hiddenSeedSql are evaluation data:
 * they stay on the server and are never returned to the browser.
 */
public record SqlQuestion(
    String id,
    String dataset,
    String topic,
    String difficulty,
    String question,
    String hint,
    EvaluationMode evaluationMode,
    String referenceQuery
) {

    /** How a submitted result is compared with the reference result. */
    public enum EvaluationMode { ORDERED, UNORDERED, SCALAR, ROW_COUNT }

    public record Dataset(
        String name,
        String description,
        List<Table> tables,
        List<String> schemaSql,
        List<String> seedSql,
        List<String> hiddenSeedSql
    ) {
    }

    public record Table(String name, List<String> columns) {
    }
}
