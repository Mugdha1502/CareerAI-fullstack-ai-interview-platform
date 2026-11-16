package com.careerai.backend.execution;

import java.util.List;

/**
 * A sandboxed, EXTERNAL code execution service. Candidate code is never compiled or executed
 * inside the CareerAI JVM or on the application host — only sent to the provider.
 */
public interface CodeExecutionProvider {

    String name();

    boolean isConfigured();

    /** Runs one program and waits (bounded) for a terminal result. */
    ExecutionResult execute(ExecutionRequest request);

    /** Runs several programs (e.g. hidden test cases) and returns results in the same order. */
    List<ExecutionResult> executeBatch(List<ExecutionRequest> requests);

    /**
     * True when every call is metered (e.g. JDoodle credits): submissions then run all hidden tests
     * inside ONE combined program (HiddenTestHarness) instead of one execution per test.
     */
    default boolean prefersCombinedSubmission() {
        return false;
    }
}
