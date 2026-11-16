package com.careerai.backend.service;

import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single backend source of career-track skill requirements, used by SkillGapService
 * and PlanService. Role titles and track ids match the frontend Career Tracks data.
 *
 * TODO: move roles and skills into MySQL career-track tables; callers only use the
 * public methods below, so the storage can change without touching them.
 */
@Service
public class CareerRequirementService {

    public static final String DEFAULT_TRACK_ID = "java-full-stack";

    /** How a skill is measured: a practice module and, for technical practice, a topic. */
    public record SkillDefinition(String name, String module, String topic, Set<String> aliases) {

        /** Measured by a practice module (otherwise only study material exists for now). */
        public boolean isPracticable() {
            return module != null;
        }

        /** True when the score comes from a whole module rather than one topic. */
        public boolean isModuleLevel() {
            return module != null && topic == null;
        }

        public boolean matches(String label) {
            if (label == null) return false;
            String normalized = normalize(label);
            return normalize(name).equals(normalized) || aliases.contains(normalized);
        }
    }

    public record CareerRole(String trackId, String title, List<SkillDefinition> requiredSkills) {

        /** Whether the role lists Data Structures & Algorithms (drives coding tasks in the plan). */
        public boolean requiresCoding() {
            return requiredSkills.stream().anyMatch(skill -> "coding".equals(skill.module()));
        }
    }

    private final Map<String, SkillDefinition> skills = new LinkedHashMap<>();
    private final Map<String, CareerRole> rolesById = new LinkedHashMap<>();

    public CareerRequirementService() {
        // Technical practice topics (frontend technicalTopics)
        technical("Java");
        technical("Python");
        technical("JavaScript", "js");
        technical("React", "react.js", "reactjs");
        technical("Spring Boot", "spring");
        technical("Spring Security");
        technical("REST APIs", "rest api", "rest", "apis", "api design");
        technical("Git", "github", "git & github");
        technical("Docker", "containers");
        technical("DBMS", "database design", "databases");
        technical("Operating Systems", "os");
        technical("Computer Networks", "networking", "networks");

        // Whole practice modules
        module("SQL", "sql", "mysql", "postgresql", "advanced sql");
        module("Data Structures & Algorithms", "coding", "dsa", "data structures", "algorithms", "problem solving");
        module("System Design", "system-design", "distributed systems");
        module("Aptitude", "aptitude", "quantitative aptitude", "logical reasoning");
        module("Communication", "voice", "communication skills", "soft skills");

        // Not measurable by a practice module yet (study via the career track)
        study("HTML & CSS", "html", "css");
        study("Node.js", "node", "nodejs", "express");
        study("C# / .NET", "c#", ".net", "asp.net core", "dotnet");
        study("Mobile Development", "android", "flutter", "react native", "kotlin");
        study("Testing", "unit testing", "manual testing", "test case design");
        study("Selenium", "selenium webdriver", "test automation");
        study("Excel", "ms excel");
        study("Statistics", "probability");
        study("Data Visualization", "power bi", "tableau");
        study("Pandas", "numpy");
        study("Machine Learning", "ml", "deep learning", "scikit-learn");
        study("ETL Pipelines", "etl", "elt", "airflow");
        study("Apache Spark", "spark");
        study("LLMs & Prompting", "llm", "prompt engineering", "rag");
        study("Linux", "bash", "shell scripting");
        study("Kubernetes", "k8s");
        study("CI/CD", "ci-cd", "jenkins", "github actions");
        study("Cloud", "aws", "azure", "gcp", "cloud computing");
        study("Infrastructure as Code", "terraform");
        study("Monitoring", "observability", "prometheus");
        study("Security Fundamentals", "cybersecurity", "information security");
        study("Web Security", "owasp", "owasp top 10");
        study("SIEM", "splunk", "log analysis");
        study("Incident Response");
        study("Agile", "scrum");
        study("Requirements Gathering", "requirements", "user stories");

        role("java-full-stack", "Java Full Stack Developer",
            "Java", "SQL", "Spring Boot", "REST APIs", "Spring Security", "React", "Git", "Docker", "Data Structures & Algorithms");
        role("frontend-developer", "Frontend Developer",
            "JavaScript", "React", "HTML & CSS", "REST APIs", "Git", "Testing", "Data Structures & Algorithms");
        role("backend-developer", "Backend Developer",
            "Java", "SQL", "Spring Boot", "REST APIs", "DBMS", "Docker", "System Design", "Data Structures & Algorithms");
        role("full-stack-developer", "Full Stack Developer",
            "JavaScript", "React", "Node.js", "REST APIs", "SQL", "Git", "Docker", "Data Structures & Algorithms");
        role("python-developer", "Python Developer",
            "Python", "SQL", "REST APIs", "DBMS", "Git", "Testing", "Data Structures & Algorithms");
        role("dotnet-developer", ".NET Developer",
            "C# / .NET", "SQL", "REST APIs", "DBMS", "Git", "Data Structures & Algorithms");
        role("nodejs-developer", "Node.js Developer",
            "JavaScript", "Node.js", "REST APIs", "SQL", "Git", "Docker", "Data Structures & Algorithms");
        role("mobile-developer", "Mobile App Developer",
            "Mobile Development", "JavaScript", "REST APIs", "Git", "Data Structures & Algorithms");
        role("software-engineer", "Software Engineer",
            "Data Structures & Algorithms", "Java", "DBMS", "Operating Systems", "Computer Networks", "System Design", "SQL", "Git");
        role("data-analyst", "Data Analyst",
            "SQL", "Excel", "Python", "Statistics", "Data Visualization", "Aptitude");
        role("data-scientist", "Data Scientist",
            "Python", "SQL", "Statistics", "Machine Learning", "Pandas", "Data Structures & Algorithms");
        role("data-engineer", "Data Engineer",
            "SQL", "Python", "ETL Pipelines", "Apache Spark", "DBMS", "Docker");
        role("machine-learning-engineer", "Machine Learning Engineer",
            "Python", "Machine Learning", "Data Structures & Algorithms", "SQL", "Docker", "System Design");
        role("ai-engineer", "AI Engineer",
            "Python", "LLMs & Prompting", "REST APIs", "Machine Learning", "Docker", "System Design");
        role("devops-engineer", "DevOps Engineer",
            "Linux", "Docker", "Kubernetes", "CI/CD", "Git", "Computer Networks", "Cloud");
        role("cloud-engineer", "Cloud Engineer",
            "Cloud", "Linux", "Computer Networks", "Docker", "Kubernetes", "Infrastructure as Code");
        role("site-reliability-engineer", "Site Reliability Engineer",
            "Linux", "Operating Systems", "Computer Networks", "Monitoring", "Python", "System Design", "Kubernetes");
        role("cybersecurity-analyst", "Cybersecurity Analyst",
            "Computer Networks", "Operating Systems", "Security Fundamentals", "Linux", "Incident Response");
        role("soc-analyst", "SOC Analyst",
            "Computer Networks", "Security Fundamentals", "SIEM", "Incident Response", "Operating Systems");
        role("security-engineer", "Security Engineer",
            "Security Fundamentals", "Web Security", "Spring Security", "Computer Networks", "Python", "Cloud");
        role("penetration-tester", "Penetration Tester",
            "Computer Networks", "Web Security", "Linux", "Python", "Operating Systems");
        role("qa-engineer", "QA Engineer",
            "Testing", "SQL", "REST APIs", "Agile", "Aptitude");
        role("automation-test-engineer", "Automation Test Engineer",
            "Java", "Selenium", "Testing", "REST APIs", "Git", "CI/CD");
        role("business-analyst", "Business Analyst",
            "Requirements Gathering", "SQL", "Excel", "Communication", "Agile", "Aptitude");
    }


    /* ---------- Lookups ---------- */

    public Optional<CareerRole> findByTitle(String title) {
        if (title == null || title.isBlank()) return Optional.empty();
        String normalized = normalize(title);
        return rolesById.values().stream()
            .filter(role -> normalize(role.title()).equals(normalized))
            .findFirst();
    }

    public Optional<CareerRole> findByTrackId(String trackId) {
        return Optional.ofNullable(trackId).map(rolesById::get);
    }

    public CareerRole defaultRole() {
        return rolesById.get(DEFAULT_TRACK_ID);
    }

    public List<CareerRole> allRoles() {
        return List.copyOf(rolesById.values());
    }

    /** Whole skill catalogue (used by resume text analysis to recognise skills). */
    public List<SkillDefinition> allSkills() {
        return List.copyOf(skills.values());
    }

    /** Catalogue skill whose name or alias equals the label. */
    public Optional<SkillDefinition> findSkill(String label) {
        return skills.values().stream().filter(skill -> skill.matches(label)).findFirst();
    }

    /** True when a free-text profile skill refers to the given required skill. */
    public boolean profileHasSkill(List<String> profileSkills, SkillDefinition skill) {
        return profileSkills != null && profileSkills.stream().anyMatch(skill::matches);
    }

    /**
     * Frontend route for practising/assessing a skill. Skills without a practice
     * module link to the career track page, where study material lives.
     */
    public String practiceRoute(SkillDefinition skill, String trackId) {
        if (skill.module() == null) {
            return "/career-tracks/" + trackId;
        }

        if ("technical".equals(skill.module())) {
            return "/practice/technical?topic=" + encode(skill.topic());
        }

        return "/practice/" + skill.module();
    }


    /* ---------- Catalogue setup ---------- */

    private void technical(String name, String... aliases) {
        add(new SkillDefinition(name, "technical", name, aliasSet(aliases)));
    }

    private void module(String name, String module, String... aliases) {
        add(new SkillDefinition(name, module, null, aliasSet(aliases)));
    }

    private void study(String name, String... aliases) {
        add(new SkillDefinition(name, null, null, aliasSet(aliases)));
    }

    private void add(SkillDefinition skill) {
        skills.put(normalize(skill.name()), skill);
    }

    private void role(String trackId, String title, String... skillNames) {
        List<SkillDefinition> required = Arrays.stream(skillNames)
            .map(name -> {
                SkillDefinition skill = skills.get(normalize(name));
                if (skill == null) {
                    throw new IllegalStateException("Unknown skill '" + name + "' in role " + title);
                }
                return skill;
            })
            .toList();

        rolesById.put(trackId, new CareerRole(trackId, title, required));
    }

    private static Set<String> aliasSet(String... aliases) {
        return Arrays.stream(aliases).map(CareerRequirementService::normalize).collect(Collectors.toUnmodifiableSet());
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** URL-encodes a query value the way the frontend does (spaces as %20). */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
