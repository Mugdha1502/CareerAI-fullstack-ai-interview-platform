package com.careerai.backend.execution;

/** Provider-neutral result of one run. Text fields are already size-limited. */
public record ExecutionResult(
    ExecutionStatus status,
    String stdout,
    String stderr,
    String compileOutput,
    Long executionTimeMs,
    Long memoryKb,
    String message
) {
}
