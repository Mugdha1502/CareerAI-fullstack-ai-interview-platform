package com.careerai.backend.dto;

/**
 * Result of "Run Code". status: ACCEPTED, COMPILATION_ERROR, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED,
 * MEMORY_LIMIT_EXCEEDED, INTERNAL_ERROR. expectedOutput/matchesExpected are only set when the
 * stdin equals one of the question's visible sample inputs.
 */
public record CodeExecutionResponse(
    String status,
    String stdout,
    String stderr,
    String compileOutput,
    Long executionTimeMs,
    Long memoryKb,
    String message,
    String expectedOutput,
    Boolean matchesExpected
) {
}
