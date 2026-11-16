package com.careerai.backend.execution;

/** One program run: language, full source, stdin and server-chosen limits. */
public record ExecutionRequest(
    CodeLanguage language,
    String sourceCode,
    String stdin,
    ExecutionLimits limits
) {
}
