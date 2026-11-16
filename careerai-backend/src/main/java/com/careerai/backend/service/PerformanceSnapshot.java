package com.careerai.backend.service;

import java.util.Map;
import java.util.Optional;

/**
 * One user's measured scores, loaded once and shared by Skill Gap and Plan.
 * Keys are lower-case: module ids, and "module|topic" for topics.
 */
public record PerformanceSnapshot(Map<String, ScoreStat> moduleScores, Map<String, ScoreStat> topicScores) {

    public Optional<ScoreStat> module(String module) {
        return Optional.ofNullable(moduleScores.get(PerformanceService.key(module)));
    }

    public Optional<ScoreStat> topic(String module, String topic) {
        return Optional.ofNullable(topicScores.get(PerformanceService.key(module, topic)));
    }

    public boolean isEmpty() {
        return moduleScores.isEmpty();
    }
}
