package com.careerai.backend.dto;

import java.util.List;

/**
 * Result of "Submit Solution". Hidden test inputs and expected outputs are never returned —
 * only pass/fail per test, the overall status and score (percentage), and the saved result.
 * status: PASSED, PARTIAL, FAILED, COMPILATION_ERROR, RUNTIME_ERROR, TIME_LIMIT_EXCEEDED.
 */
public record CodeSubmitResponse(
    String status,
    int passedTests,
    int totalTests,
    int score,
    List<TestOutcome> tests,
    String compileOutput,
    String errorMessage,
    PracticeHistoryItem result
) {

    public record TestOutcome(int index, boolean passed, String status, Long executionTimeMs) {
    }
}
