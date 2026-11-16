package com.careerai.backend.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic interview content used when the AI is unavailable (e.g. Gemini free-tier 429/503):
 * a curated question bank with key points, question templates for resume/job topics, and
 * key-point coverage scoring. It never pretends to be AI; results are marked FALLBACK.
 */
@Component
public class InterviewFallbackEngine {

    public record KeyPoint(String label, List<String> keywords) {
    }

    public record BankQuestion(String topic, String questionType, String question, List<KeyPoint> keyPoints, String referenceAnswer) {
    }

    public record Evaluation(int score, List<String> strengths, List<String> missingPoints, String feedback,
                             String idealAnswer, boolean followUpRequired) {
    }

    public static final List<String> BEHAVIORAL_TOPICS = List.of("Problem solving", "Teamwork", "Handling pressure", "Learning quickly", "Conflict resolution");
    public static final List<String> HR_TOPICS = List.of("Introduction", "Motivation", "Strengths and weaknesses", "Career goals");

    private static final Set<String> STOPWORDS = Set.of(
        "with", "that", "this", "from", "your", "their", "what", "when", "which", "about", "into", "using", "used",
        "how", "and", "the", "for", "are", "its", "it's", "between", "explain", "mention", "clear"
    );

    private final Map<String, List<BankQuestion>> technical = new LinkedHashMap<>();
    private final Map<String, List<BankQuestion>> behavioral = new LinkedHashMap<>();

    public InterviewFallbackEngine() {
        tech("Java", "Explain the difference between an interface and an abstract class in Java, and when you would use each.",
            "An abstract class can hold state, constructors and concrete methods, and a class can extend only one. An interface defines a contract; since Java 8 it can have default and static methods, and a class can implement many. Use an abstract class for related classes sharing code and state, and an interface for a capability shared by unrelated classes.",
            kp("Abstract classes can have state and constructors", "state", "field", "constructor"),
            kp("Single inheritance vs many interfaces", "only one", "multiple", "many interfaces", "single"),
            kp("Default and static methods in interfaces", "default", "static", "java 8"),
            kp("When to choose each", "contract", "capability", "share", "when to use", "choose"));
        tech("Java", "How does a HashMap work internally in Java?",
            "HashMap stores entries in an array of buckets. hashCode() selects the bucket, equals() finds the exact key, collisions are kept in a linked list that becomes a tree when large, and the table resizes when size exceeds capacity times the load factor (0.75).",
            kp("hashCode selects a bucket", "hashcode", "hash code", "bucket", "index"),
            kp("Collisions handled with lists/trees", "collision", "linked list", "tree"),
            kp("equals() finds the exact key", "equals"),
            kp("Resizing and load factor", "resize", "load factor", "capacity", "rehash"));
        tech("Spring Boot", "Explain dependency injection in Spring Boot and why constructor injection is preferred.",
            "Spring creates and manages beans and injects their dependencies instead of classes creating them. Constructor injection makes dependencies explicit and final, supports immutability and makes classes easy to test without the container.",
            kp("Container manages beans", "bean", "container", "ioc", "inversion of control", "application context"),
            kp("Dependencies are injected, not created", "inject", "@autowired", "provided"),
            kp("Constructor injection benefits", "constructor", "final", "immutable"),
            kp("Testability", "test", "mock"));
        tech("Spring Boot", "What does Spring Boot auto-configuration do, and how can you override it?",
            "Auto-configuration creates beans based on the classpath and properties using conditional annotations. You override it by defining your own bean, setting properties, or excluding an auto-configuration class.",
            kp("Configures beans from the classpath", "classpath", "dependency", "starter"),
            kp("Conditional annotations", "conditional", "@conditionalon", "condition"),
            kp("Properties configure behaviour", "properties", "application.properties", "yml"),
            kp("Overriding or excluding", "override", "exclude", "own bean", "custom"));
        tech("Spring Security", "How would you secure a REST API with Spring Security and JWT?",
            "Configure a SecurityFilterChain that is stateless, permits the login endpoint and requires authentication elsewhere. On login, authenticate credentials and issue a signed JWT. A filter validates the token on each request and sets the authentication in the security context; roles drive authorization.",
            kp("Security filter chain configuration", "filter chain", "securityfilterchain", "filter"),
            kp("Login issues a signed token", "login", "token", "jwt", "sign"),
            kp("Token validated on every request", "validate", "every request", "header", "bearer"),
            kp("Stateless sessions and authorization", "stateless", "role", "authoriz"));
        tech("Spring Security", "What is the difference between authentication and authorization?",
            "Authentication verifies who the user is, for example with a password or token. Authorization decides what an authenticated user may do, for example via roles or permissions checked on endpoints or methods.",
            kp("Authentication verifies identity", "identity", "who", "verify", "credential", "password"),
            kp("Authorization checks permissions", "permission", "role", "access", "allowed"),
            kp("Order: authenticate first", "first", "then", "after"));
        tech("SQL", "What is the difference between INNER JOIN and LEFT JOIN? Give an example.",
            "INNER JOIN returns only rows with matches in both tables. LEFT JOIN returns all rows from the left table and matching rows from the right, with NULLs where there is no match, for example customers including those without orders.",
            kp("INNER JOIN returns matching rows only", "only matching", "both tables", "match"),
            kp("LEFT JOIN keeps all left rows", "all rows", "left table", "all records"),
            kp("NULL for missing matches", "null"),
            kp("Concrete example", "example", "customer", "order", "employee"));
        tech("SQL", "How would you find and fix a slow SQL query?",
            "Check the execution plan with EXPLAIN, look for full table scans, add or adjust indexes on filtered and joined columns, select only needed columns, and avoid functions on indexed columns in WHERE clauses.",
            kp("Use the execution plan", "explain", "execution plan", "query plan"),
            kp("Indexes on filter/join columns", "index"),
            kp("Avoid full table scans / select *", "full table scan", "select *", "only needed"),
            kp("Measure before and after", "measure", "time", "compare"));
        tech("REST APIs", "What makes an API RESTful, and how do you choose HTTP methods and status codes?",
            "REST models resources with URLs, uses HTTP methods for actions (GET read, POST create, PUT/PATCH update, DELETE remove), is stateless, and returns meaningful status codes such as 200, 201, 400, 404 and 500.",
            kp("Resources identified by URLs", "resource", "url", "endpoint"),
            kp("HTTP methods map to actions", "get", "post", "put", "delete", "patch"),
            kp("Statelessness", "stateless"),
            kp("Meaningful status codes", "status code", "200", "201", "404", "400"));
        tech("REST APIs", "How would you handle errors consistently in a REST API?",
            "Use a global exception handler (for example @RestControllerAdvice) that maps exceptions to proper status codes and a consistent JSON error body, without exposing stack traces.",
            kp("Global exception handler", "controlleradvice", "exception handler", "global"),
            kp("Correct status codes", "status", "400", "404", "500"),
            kp("Consistent error body", "consistent", "json", "message", "format"),
            kp("Do not leak internals", "stack trace", "internal", "sensitive"));
        tech("React", "Explain the difference between state and props in React.",
            "Props are read-only inputs passed from a parent; state is data owned by a component that changes over time with setState or useState and triggers a re-render. Lift state up when several components need it.",
            kp("Props come from the parent and are read-only", "parent", "read-only", "immutable", "passed"),
            kp("State is owned and changes", "usestate", "setstate", "changes", "owned"),
            kp("Changes trigger re-render", "re-render", "render"),
            kp("Lifting state up", "lift", "shared"));
        tech("React", "How does React communicate with a Spring Boot backend?",
            "React calls REST endpoints with fetch or Axios, sends and receives JSON, attaches a JWT in the Authorization header for protected APIs, and the backend allows the frontend origin with CORS. Loading and error states are handled in the UI.",
            kp("HTTP calls via fetch/Axios", "fetch", "axios", "http"),
            kp("JSON payloads", "json"),
            kp("Authentication header", "token", "jwt", "authorization", "header"),
            kp("CORS and error handling", "cors", "error", "loading"));
        tech("JavaScript", "Explain closures in JavaScript with an example.",
            "A closure is a function that remembers variables from the scope where it was created, even after that scope has finished. For example, a counter factory returns a function that keeps incrementing its private count.",
            kp("Function remembers outer scope", "scope", "remember", "outer", "lexical"),
            kp("Works after outer function returns", "after", "returned", "finished"),
            kp("Concrete example", "example", "counter", "function"));
        tech("Python", "What is the difference between a list and a tuple in Python?",
            "Lists are mutable and tuples are immutable. Tuples can be dictionary keys because they are hashable and are slightly lighter; use tuples for fixed records and lists for collections that change.",
            kp("Mutable vs immutable", "mutable", "immutable"),
            kp("Tuples are hashable", "hashable", "dictionary key", "key"),
            kp("When to use each", "fixed", "record", "collection", "use"));
        tech("Docker", "How would you containerise a Spring Boot application with Docker?",
            "Write a Dockerfile from a JDK/JRE base image, copy the built JAR, expose the port and set the entry point; build the image, run the container with environment variables for configuration, and use Docker Compose to add a database.",
            kp("Dockerfile with a Java base image", "dockerfile", "base image", "jdk", "jre", "from"),
            kp("Copy the JAR and set entry point", "jar", "entrypoint", "cmd", "copy"),
            kp("Build and run the image", "build", "run", "image", "container"),
            kp("Configuration via environment / compose", "environment", "env", "compose"));
        tech("Git", "Explain the difference between git merge and git rebase.",
            "Merge combines branches with a merge commit and keeps history as it happened. Rebase replays commits on top of another branch for a linear history but rewrites commits, so avoid rebasing shared branches.",
            kp("Merge creates a merge commit", "merge commit", "preserve", "history"),
            kp("Rebase rewrites/replays commits", "replay", "rewrite", "linear"),
            kp("Avoid rebasing shared branches", "shared", "public", "force"));
        tech("DBMS", "What is normalization, and why is it used?",
            "Normalization organises tables to reduce redundancy and update anomalies by splitting data into related tables (1NF, 2NF, 3NF). Sometimes tables are denormalised for read performance.",
            kp("Reduces redundancy", "redundan", "duplicate"),
            kp("Avoids anomalies", "anomal", "update", "consisten"),
            kp("Normal forms", "1nf", "2nf", "3nf", "normal form"),
            kp("Denormalisation trade-off", "denormal", "performance", "trade"));
        tech("Data Structures & Algorithms", "How do you choose between an array list and a linked list?",
            "Array lists give O(1) random access and cache-friendly iteration but O(n) inserts in the middle; linked lists give O(1) inserts/removals at known positions but O(n) access. Most workloads favour array lists.",
            kp("Access complexity", "o(1)", "random access", "index", "constant"),
            kp("Insertion/removal costs", "insert", "remove", "o(n)"),
            kp("Memory / cache behaviour", "memory", "cache", "contiguous"),
            kp("Choice depends on workload", "depends", "use case", "workload"));
        tech("System Design", "How would you design a URL shortener at a high level?",
            "Provide an API to create short codes and redirect; generate unique codes (base62 of an ID or hashing), store code to URL mappings in a key-value store, cache hot codes, and scale reads with replicas and a CDN.",
            kp("Clear API / requirements", "api", "endpoint", "redirect", "requirement"),
            kp("Unique code generation", "base62", "hash", "unique", "id"),
            kp("Storage choice", "database", "key-value", "store", "table"),
            kp("Caching and scaling", "cache", "scale", "replica", "cdn", "load balancer"));
        tech("Operating Systems", "What is the difference between a process and a thread?",
            "A process has its own memory space; threads are units of execution inside a process that share its memory. Threads are cheaper to create and switch but need synchronisation to avoid race conditions.",
            kp("Separate vs shared memory", "memory", "address space", "share"),
            kp("Threads run inside a process", "inside", "within", "part of"),
            kp("Cost of creation/switching", "lightweight", "cheaper", "context switch"),
            kp("Synchronisation needs", "synchroniz", "race", "lock"));
        tech("Computer Networks", "What is the difference between TCP and UDP?",
            "TCP is connection-oriented and reliable with ordering, retransmission and congestion control; UDP is connectionless and faster with no delivery guarantees. TCP suits web traffic; UDP suits streaming, gaming and DNS.",
            kp("Connection-oriented vs connectionless", "connection"),
            kp("Reliability and ordering", "reliab", "order", "retransmi", "guarantee"),
            kp("Speed / overhead", "fast", "overhead", "lightweight"),
            kp("Use cases", "stream", "video", "game", "dns", "web", "http"));

        behavior("Problem solving", "Tell me about a difficult technical problem you solved. How did you approach it?");
        behavior("Teamwork", "Describe a time you worked in a team to deliver something. What was your role?");
        behavior("Handling pressure", "Tell me about a time you had to meet a tight deadline. What did you do?");
        behavior("Learning quickly", "Describe a time you had to learn a new technology quickly. How did you go about it?");
        behavior("Conflict resolution", "Tell me about a disagreement with a teammate and how you resolved it.");
        hr("Introduction", "Tell me about yourself and why you are interested in this role.");
        hr("Motivation", "Why do you want to work as a software professional in this role?");
        hr("Strengths and weaknesses", "What is one strength and one area for improvement that you have, with examples?");
        hr("Career goals", "Where do you see your career in the next two to three years?");
    }


    /* ---------- Questions ---------- */

    /** Bank question for a topic, avoiding already-asked questions; template when the bank has none. */
    public BankQuestion questionFor(String topic, String interviewType, String targetRole, Set<String> askedFingerprints) {
        String key = topic.toLowerCase(Locale.ROOT);
        List<BankQuestion> candidates = new ArrayList<>();
        technical.forEach((name, list) -> { if (name.equals(key)) candidates.addAll(list); });
        behavioral.forEach((name, list) -> { if (name.equals(key)) candidates.addAll(list); });

        for (BankQuestion candidate : candidates) {
            if (!askedFingerprints.contains(fingerprint(candidate.question()))) {
                return candidate;
            }
        }
        return template(topic, interviewType, targetRole);
    }

    public boolean isBehavioralTopic(String topic) {
        return behavioral.containsKey(topic.toLowerCase(Locale.ROOT));
    }

    private BankQuestion template(String topic, String interviewType, String targetRole) {
        return switch (interviewType) {
            case "RESUME" -> new BankQuestion(topic, "RESUME",
                "Your resume mentions " + topic + ". Describe a specific piece of work where you used it: what you built, your role and the outcome.",
                experienceKeyPoints(), "A strong answer names the project, explains your own role, gives one specific technical detail about " + topic + " and states the outcome or what you learned.");
            case "JOB_SPECIFIC" -> new BankQuestion(topic, "JOB_SPECIFIC",
                "This role requires " + topic + ". Describe your experience with it, or how you would become productive with it quickly.",
                experienceKeyPoints(), "A strong answer is honest about your level, gives a concrete example of using " + topic + " (or a learning plan) and links it to the job's needs.");
            default -> new BankQuestion(topic, "TECHNICAL",
                "Explain the key concepts of " + topic + " and how you would use it in a " + targetRole + " project.",
                List.of(
                    kp("Clear definition of the concept", "is a", "means", "refers", "used for", "allows"),
                    kp("How it works", "works", "because", "process", "steps", "internally"),
                    kp("Practical use in a project", "project", "used", "built", "implemented", "application"),
                    kp("Trade-offs or pitfalls", "trade-off", "advantage", "disadvantage", "limitation", "instead", "pitfall")),
                "A strong answer defines " + topic + ", explains how it works, gives a practical example from a project and mentions a trade-off or common mistake.");
        };
    }

    /** Generic key points for a question type, used to score voice answers when the AI is unavailable. */
    public List<KeyPoint> genericKeyPoints(String questionType) {
        return switch (questionType) {
            case "BEHAVIORAL" -> starKeyPoints();
            case "HR" -> List.of(
                kp("Specific and honest", "because", "example", "for instance", "project"),
                kp("Linked to the role", "role", "position", "company", "team", "developer", "engineer"),
                kp("Shows motivation or growth", "learn", "grow", "goal", "improve", "interested", "passion"));
            case "RESUME", "JOB_SPECIFIC" -> experienceKeyPoints();
            default -> template("the topic", "TECHNICAL", "this role").keyPoints();
        };
    }

    private static List<KeyPoint> experienceKeyPoints() {
        return List.of(
            kp("Context: which project or situation", "project", "application", "team", "internship", "course"),
            kp("Your own role and actions", "i built", "i implemented", "i designed", "i wrote", "my role", "i used", "i was responsible"),
            kp("A specific technical detail", "because", "using", "configured", "designed", "api", "database", "class"),
            kp("Outcome or learning", "result", "outcome", "learned", "improved", "delivered", "reduced"));
    }

    private static List<KeyPoint> starKeyPoints() {
        return List.of(
            kp("Situation: the context", "situation", "project", "team", "when", "during", "internship"),
            kp("Task: your responsibility", "task", "goal", "responsible", "needed to", "had to"),
            kp("Action: what you did", "i decided", "i built", "i talked", "i organised", "i organized", "i created", "i fixed", "i worked", "i took"),
            kp("Result and reflection", "result", "outcome", "learned", "improved", "delivered", "next time"));
    }


    /* ---------- Evaluation ---------- */

    /** Key-point coverage scoring, adjusted for very short answers. */
    public Evaluation evaluate(String answer, List<KeyPoint> keyPoints, String reference) {
        String text = " " + answer.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + " ";
        int words = answer.isBlank() ? 0 : answer.strip().split("\\s+").length;

        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (KeyPoint point : keyPoints) {
            boolean hit = point.keywords().stream().anyMatch(keyword -> text.contains(keyword.toLowerCase(Locale.ROOT)));
            (hit ? covered : missing).add(point.label());
        }

        double ratio = keyPoints.isEmpty() ? 0 : covered.size() / (double) keyPoints.size();
        int score = (int) Math.round(15 + 70 * ratio) + (words >= 40 ? 10 : 0);
        if (words < 10) score = Math.min(score, 20);
        score = Math.max(0, Math.min(100, score));

        String feedback;
        if (words < 10) {
            feedback = "Your answer is very short. Aim for 80-200 words with a concrete example.";
        } else if (missing.isEmpty()) {
            feedback = "You covered all the key points. Make it stronger with a concrete example from your own work.";
        } else {
            feedback = "Good start. To strengthen the answer, also cover: " + String.join("; ", missing) + ".";
        }

        String ideal = reference != null ? reference
            : "A strong answer covers: " + String.join("; ", keyPoints.stream().map(KeyPoint::label).toList()) + ".";

        return new Evaluation(score, covered, missing, feedback + " (Scored by key-point matching because AI evaluation was unavailable.)",
            ideal, score < 60);
    }

    /** Key points from AI-written labels, with keywords taken from the label's significant words. */
    public static List<KeyPoint> keyPointsFromLabels(List<String> labels) {
        return labels.stream().map(label -> new KeyPoint(label, java.util.Arrays.stream(label.toLowerCase(Locale.ROOT).split("[^a-z0-9+#.@]+"))
            .filter(word -> word.length() >= 4 && !STOPWORDS.contains(word))
            .toList())).toList();
    }

    public static String fingerprint(String question) {
        return question.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }


    /* ---------- bank setup ---------- */

    private void tech(String topic, String question, String reference, KeyPoint... points) {
        technical.computeIfAbsent(topic.toLowerCase(Locale.ROOT), key -> new ArrayList<>())
            .add(new BankQuestion(topic, "TECHNICAL", question, List.of(points), reference));
    }

    private void behavior(String topic, String question) {
        behavioral.computeIfAbsent(topic.toLowerCase(Locale.ROOT), key -> new ArrayList<>())
            .add(new BankQuestion(topic, "BEHAVIORAL", question, starKeyPoints(),
                "Use the STAR structure: the Situation, your Task, the Actions you personally took, and the Result with what you learned. Use a real example from your own experience."));
    }

    private void hr(String topic, String question) {
        behavioral.computeIfAbsent(topic.toLowerCase(Locale.ROOT), key -> new ArrayList<>())
            .add(new BankQuestion(topic, "HR", question, List.of(
                kp("Specific and honest", "because", "example", "for instance", "project"),
                kp("Linked to the role", "role", "position", "company", "team", "developer", "engineer"),
                kp("Shows motivation or growth", "learn", "grow", "goal", "improve", "interested", "passion")),
                "A strong answer is concise (about a minute), specific to you, supported by an example, and connects your background and goals to this role."));
    }

    private static KeyPoint kp(String label, String... keywords) {
        return new KeyPoint(label, List.of(keywords));
    }
}
