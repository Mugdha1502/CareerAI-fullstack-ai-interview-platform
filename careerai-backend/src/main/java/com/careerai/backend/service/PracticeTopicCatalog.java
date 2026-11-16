package com.careerai.backend.service;

import com.careerai.backend.exception.InvalidAiRequestException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Allow-list of practice topics that AI features accept, matching the frontend practice
 * modules. Technical topics come from the CareerRequirementService skill catalogue;
 * aptitude and system design topics are listed here. Only listed topics reach a prompt.
 */
@Component
public class PracticeTopicCatalog {

    private static final List<String> APTITUDE_TOPICS = List.of(
        "Percentages", "Profit & Loss", "Ratio", "Time & Work", "Probability",
        "Logical Reasoning", "Verbal Ability", "Data Interpretation"
    );

    private static final List<String> SYSTEM_DESIGN_TOPICS = List.of(
        "URL Shortener", "Chat Application", "E-commerce Platform", "Video Streaming",
        "Rate Limiter", "Notification System"
    );

    private final List<String> technicalTopics;

    public PracticeTopicCatalog(CareerRequirementService careerRequirementService) {
        this.technicalTopics = careerRequirementService.allSkills().stream()
            .filter(skill -> "technical".equals(skill.module()))
            .map(CareerRequirementService.SkillDefinition::topic)
            .toList();
    }

    public List<String> topicsFor(String module) {
        return switch (module) {
            case "technical" -> technicalTopics;
            case "aptitude" -> APTITUDE_TOPICS;
            case "system-design" -> SYSTEM_DESIGN_TOPICS;
            default -> List.of();
        };
    }

    /** The canonical topic name (case-insensitive match), or 400 for an unknown topic. */
    public String requireTopic(String module, String topic) {
        return find(module, topic).orElseThrow(() ->
            new InvalidAiRequestException("Unknown " + module + " topic. Choose one of: " + String.join(", ", topicsFor(module)) + "."));
    }

    private Optional<String> find(String module, String topic) {
        String wanted = topic == null ? "" : topic.strip();
        return topicsFor(module).stream().filter(name -> name.equalsIgnoreCase(wanted)).findFirst();
    }
}
