package com.careerai.backend.execution;

/**
 * Sandbox limits for one run. Always set by the server from configuration, never by the client.
 * memoryLimitKb and the process limit are enforced by the provider's sandbox (Judge0: isolate).
 */
public record ExecutionLimits(
    double cpuTimeLimitSeconds,
    double wallTimeLimitSeconds,
    int memoryLimitKb,
    int maxProcessesAndThreads,
    int maxFileSizeKb
) {
}
