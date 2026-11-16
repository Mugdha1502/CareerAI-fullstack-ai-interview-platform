package com.careerai.backend.practice;

import java.util.List;
import java.util.Map;

/**
 * A curated coding question. starterCode is keyed by CodeLanguage name and contains a complete
 * program (the function to implement plus a stdin/stdout driver). hiddenTests stay on the server.
 */
public record CodingQuestion(
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
    List<TestCase> sampleTests,
    List<TestCase> hiddenTests,
    double timeLimitSeconds,
    int memoryLimitKb
) {

    public record Example(String input, String output, String explanation) {
    }

    public record TestCase(String stdin, String expectedOutput) {
    }
}
