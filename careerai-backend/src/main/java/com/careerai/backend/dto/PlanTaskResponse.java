package com.careerai.backend.dto;

/**
 * One generated plan task. id is a stable key (e.g. "practice-spring-security")
 * used for completion. completionSource: MANUAL | PRACTICE | null.
 */
public record PlanTaskResponse(
    String id,
    String title,
    String description,
    String type,
    String topic,
    int durationMinutes,
    String priority,
    String route,
    boolean completed,
    String completionSource
) {
}
