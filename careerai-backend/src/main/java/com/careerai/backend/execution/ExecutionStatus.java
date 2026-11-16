package com.careerai.backend.execution;

/** Outcome of one program execution (provider-neutral). */
public enum ExecutionStatus {
    QUEUED,
    PROCESSING,
    ACCEPTED,
    WRONG_ANSWER,
    COMPILATION_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    INTERNAL_ERROR,
    PROVIDER_UNAVAILABLE;

    public boolean isTerminal() {
        return this != QUEUED && this != PROCESSING;
    }
}
