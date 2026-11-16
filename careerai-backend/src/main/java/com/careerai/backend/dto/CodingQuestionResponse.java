package com.careerai.backend.dto;

import java.util.List;
import java.util.Map;

/** A coding question as the browser may see it: no hidden tests (only their count). */
public record CodingQuestionResponse(
    String id,
    String topic,
    String title,
    String difficulty,
    String description,
    String inputFormat,
    String outputFormat,
    List<String> constraints,
    List<Example> examples,
    Map<String, String> starterCode,
    List<String> supportedLanguages,
    int hiddenTestCount,
    double timeLimitSeconds,
    int memoryLimitKb,
    boolean executionAvailable
) {

    public record Example(String input, String output, String explanation) {
    }
}
