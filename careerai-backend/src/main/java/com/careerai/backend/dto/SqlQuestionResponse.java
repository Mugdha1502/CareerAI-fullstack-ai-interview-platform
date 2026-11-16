package com.careerai.backend.dto;

import java.util.List;

/** A SQL question as the browser may see it: schema and sample rows, no evaluation data. */
public record SqlQuestionResponse(
    String id,
    String dataset,
    String datasetDescription,
    String topic,
    String difficulty,
    String question,
    String hint,
    List<Table> tables
) {

    public record Table(String name, List<String> columns, List<String> sampleColumns, List<List<Object>> sampleRows) {
    }
}
