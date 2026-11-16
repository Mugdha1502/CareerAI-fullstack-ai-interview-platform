package com.careerai.backend.service;

import com.careerai.backend.billing.FeatureKey;
import com.careerai.backend.billing.EntitlementService;
import com.careerai.backend.dto.CodeExecuteRequest;
import com.careerai.backend.dto.CodeExecutionResponse;
import com.careerai.backend.dto.CodeSubmitRequest;
import com.careerai.backend.dto.CodeSubmitResponse;
import com.careerai.backend.dto.CodingQuestionResponse;
import com.careerai.backend.dto.PracticeHistoryItem;
import com.careerai.backend.entity.User;
import com.careerai.backend.execution.CodeExecutionProvider;
import com.careerai.backend.execution.CodeLanguage;
import com.careerai.backend.execution.ExecutionLimits;
import com.careerai.backend.execution.ExecutionProviderException;
import com.careerai.backend.execution.ExecutionRequest;
import com.careerai.backend.execution.ExecutionResult;
import com.careerai.backend.execution.ExecutionStatus;
import com.careerai.backend.practice.CodingQuestion;
import com.careerai.backend.practice.CodingQuestionCatalog;
import com.careerai.backend.practice.HiddenTestHarness;
import com.careerai.backend.practice.PracticeExecutionException;
import com.careerai.backend.practice.PracticeRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Coding practice: Run Code (custom stdin, never saved) and Submit Solution (hidden tests,
 * scored and saved as a PracticeResult). Candidate code is only ever sent to the configured
 * external sandbox (CodeExecutionProvider) — never compiled or executed in this JVM.
 * Limits always come from server configuration; the client cannot change them.
 */
@Service
public class CodingPracticeService {

    private static final Logger log = LoggerFactory.getLogger(CodingPracticeService.class);
    private static final int MAX_SOURCE_BYTES = 51200;
    private static final int MAX_STDIN_BYTES = 20480;
    private static final int MAX_ERROR_CHARS = 2000;

    private final CodeExecutionProvider provider;
    private final CodingQuestionCatalog catalog;
    private final EntitlementService entitlementService;
    private final PracticeRateLimiter rateLimiter;
    private final PracticeService practiceService;
    private final CurrentUserService currentUserService;
    private final double cpuTimeLimitSeconds;
    private final double wallTimeLimitSeconds;
    private final int memoryLimitKb;
    private final int maxProcesses;
    private final int maxFileSizeKb;
    private final int runPerMinute;
    private final int submitPerMinute;

    public CodingPracticeService(
        CodeExecutionProvider provider,
        CodingQuestionCatalog catalog,
        EntitlementService entitlementService,
        PracticeRateLimiter rateLimiter,
        PracticeService practiceService,
        CurrentUserService currentUserService,
        @Value("${app.execution.cpu-time-limit-seconds:2.5}") double cpuTimeLimitSeconds,
        @Value("${app.execution.wall-time-limit-seconds:5}") double wallTimeLimitSeconds,
        @Value("${app.execution.memory-limit-kb:256000}") int memoryLimitKb,
        @Value("${app.execution.max-processes:60}") int maxProcesses,
        @Value("${app.execution.max-file-size-kb:1024}") int maxFileSizeKb,
        @Value("${app.practice.rate.code-run-per-minute:10}") int runPerMinute,
        @Value("${app.practice.rate.code-submit-per-minute:5}") int submitPerMinute
    ) {
        this.provider = provider;
        this.catalog = catalog;
        this.entitlementService = entitlementService;
        this.rateLimiter = rateLimiter;
        this.practiceService = practiceService;
        this.currentUserService = currentUserService;
        this.cpuTimeLimitSeconds = cpuTimeLimitSeconds;
        this.wallTimeLimitSeconds = wallTimeLimitSeconds;
        this.memoryLimitKb = memoryLimitKb;
        this.maxProcesses = maxProcesses;
        this.maxFileSizeKb = maxFileSizeKb;
        this.runPerMinute = runPerMinute;
        this.submitPerMinute = submitPerMinute;
    }

    public boolean isExecutionAvailable() {
        return provider.isConfigured();
    }

    public CodingQuestionResponse question(String id, String topic) {
        CodingQuestion question = id != null && !id.isBlank() ? catalog.require(id) : catalog.requireByTopic(topic);
        return toResponse(question);
    }

    public CodeExecutionResponse run(String email, CodeExecuteRequest request) {
        User user = currentUserService.require(email);
        CodeLanguage language = language(request.language());
        requireSize(request.sourceCode(), MAX_SOURCE_BYTES, "Source code must be at most 50 KB.");
        requireSize(request.stdin(), MAX_STDIN_BYTES, "Input must be at most 20 KB.");
        CodingQuestion question = request.questionId() == null || request.questionId().isBlank() ? null : catalog.require(request.questionId());
        rateLimiter.check(user.getId(), "code-run", runPerMinute);
        requireProvider();

        // CODING_RUN counts when the sandbox ran the program (any outcome); provider failures give it back.
        CodingQuestion runQuestion = question;
        ExecutionResult result = entitlementService.consume(user, FeatureKey.CODING_RUN,
            () -> provider.execute(new ExecutionRequest(language, request.sourceCode(), stdinOf(request.stdin()), limits(runQuestion))));

        String expected = null;
        Boolean matches = null;
        if (question != null) {
            String stdin = normalize(stdinOf(request.stdin()));
            for (CodingQuestion.TestCase sample : question.sampleTests()) {
                if (normalize(sample.stdin()).equals(stdin)) {
                    expected = sample.expectedOutput();
                    matches = result.status() == ExecutionStatus.ACCEPTED && normalize(result.stdout()).equals(normalize(expected));
                    break;
                }
            }
        }
        return new CodeExecutionResponse(result.status().name(), result.stdout(), result.stderr(), result.compileOutput(),
            result.executionTimeMs(), result.memoryKb(), friendlyMessage(result), expected, matches);
    }

    public CodeSubmitResponse submit(String email, CodeSubmitRequest request) {
        User user = currentUserService.require(email);
        CodingQuestion question = catalog.require(request.questionId());
        CodeLanguage language = language(request.language());
        requireSize(request.sourceCode(), MAX_SOURCE_BYTES, "Source code must be at most 50 KB.");
        rateLimiter.check(user.getId(), "code-submit", submitPerMinute);
        requireProvider();

        // CODING_SUBMIT counts a scored submission; provider failures (unavailable, quota, timeout) give it back.
        return entitlementService.consume(user, FeatureKey.CODING_SUBMIT, () -> evaluateSubmission(email, request, question, language));
    }

    private CodeSubmitResponse evaluateSubmission(String email, CodeSubmitRequest request, CodingQuestion question, CodeLanguage language) {
        List<ExecutionResult> results = runHiddenTests(question, language, request.sourceCode());
        if (results.size() != question.hiddenTests().size()) {
            throw ExecutionProviderException.unavailable();
        }
        if (results.stream().allMatch(result -> result.status() == ExecutionStatus.INTERNAL_ERROR)) {
            // The sandbox itself failed: do not penalise the candidate with a 0 score.
            log.warn("EXECUTION_INTERNAL_ERROR: every hidden test failed inside the provider (question {}).", question.id());
            throw ExecutionProviderException.unavailable();
        }

        int passed = 0;
        List<CodeSubmitResponse.TestOutcome> outcomes = new ArrayList<>();
        ExecutionResult firstFailure = null;
        for (int i = 0; i < results.size(); i++) {
            ExecutionResult result = results.get(i);
            boolean ok = result.status() == ExecutionStatus.ACCEPTED
                && normalize(result.stdout()).equals(normalize(question.hiddenTests().get(i).expectedOutput()));
            String status = ok ? "PASSED" : result.status() == ExecutionStatus.ACCEPTED ? ExecutionStatus.WRONG_ANSWER.name() : result.status().name();
            if (ok) passed++;
            else if (firstFailure == null) firstFailure = result;
            outcomes.add(new CodeSubmitResponse.TestOutcome(i + 1, ok, status, result.executionTimeMs()));
        }

        int total = results.size();
        String overall = overallStatus(passed, total, results);
        PracticeHistoryItem saved = practiceService.saveScoredSubmission(email, new PracticeService.ScoredSubmission(
            "coding", question.topic(), question.difficulty(), question.id(), language.name(), overall, passed, total, request.sessionId()));

        String compileOutput = firstFailure != null && firstFailure.status() == ExecutionStatus.COMPILATION_ERROR ? firstFailure.compileOutput() : "";
        String errorMessage = firstFailure == null ? null : failureMessage(firstFailure);
        return new CodeSubmitResponse(overall, passed, total, (int) Math.round(passed * 100.0 / total), outcomes,
            truncate(compileOutput), errorMessage, saved);
    }

    /**
     * Metered providers (JDoodle: one credit per call) run every hidden test inside ONE combined
     * program; others (Judge0) run one sandboxed execution per test as a batch. A provider failure
     * (unavailable, timeout, quota) propagates and nothing is scored or saved.
     */
    private List<ExecutionResult> runHiddenTests(CodingQuestion question, CodeLanguage language, String sourceCode) {
        List<String> inputs = question.hiddenTests().stream().map(CodingQuestion.TestCase::stdin).toList();
        if (provider.prefersCombinedSubmission()) {
            String nonce = HiddenTestHarness.newNonce();
            String program = HiddenTestHarness.build(language, sourceCode, inputs, nonce);
            ExecutionResult combined = provider.execute(new ExecutionRequest(language, program, "", limits(question)));
            return HiddenTestHarness.toResults(combined, nonce, inputs.size());
        }
        List<ExecutionRequest> runs = new ArrayList<>();
        for (String input : inputs) {
            runs.add(new ExecutionRequest(language, sourceCode, input, limits(question)));
        }
        return provider.executeBatch(runs);
    }

    private static String overallStatus(int passed, int total, List<ExecutionResult> results) {
        if (passed == total) return "PASSED";
        if (results.stream().anyMatch(r -> r.status() == ExecutionStatus.COMPILATION_ERROR)) return ExecutionStatus.COMPILATION_ERROR.name();
        if (passed > 0) return "PARTIAL";
        if (results.stream().anyMatch(r -> r.status() == ExecutionStatus.TIME_LIMIT_EXCEEDED)) return ExecutionStatus.TIME_LIMIT_EXCEEDED.name();
        if (results.stream().anyMatch(r -> r.status() == ExecutionStatus.RUNTIME_ERROR || r.status() == ExecutionStatus.MEMORY_LIMIT_EXCEEDED)) {
            return ExecutionStatus.RUNTIME_ERROR.name();
        }
        return "FAILED";
    }

    /** First failure, without hidden data: compile output, or a short stderr/explanation. */
    private String failureMessage(ExecutionResult result) {
        return switch (result.status()) {
            case COMPILATION_ERROR -> "Your code did not compile.";
            case TIME_LIMIT_EXCEEDED -> "Time limit exceeded on a hidden test.";
            case MEMORY_LIMIT_EXCEEDED -> "Memory limit exceeded on a hidden test.";
            case RUNTIME_ERROR -> "Runtime error on a hidden test" + (result.stderr().isBlank() ? "." : ": " + truncate(result.stderr()));
            case ACCEPTED, WRONG_ANSWER -> "Wrong answer on a hidden test.";
            default -> "A hidden test could not be evaluated.";
        };
    }

    private static String friendlyMessage(ExecutionResult result) {
        return switch (result.status()) {
            case ACCEPTED -> "Program finished successfully.";
            case COMPILATION_ERROR -> "Compilation error.";
            case RUNTIME_ERROR -> "Runtime error.";
            case TIME_LIMIT_EXCEEDED -> "Time limit exceeded.";
            case MEMORY_LIMIT_EXCEEDED -> "Memory limit exceeded.";
            default -> "The program could not be run.";
        };
    }

    private ExecutionLimits limits(CodingQuestion question) {
        double cpu = question != null && question.timeLimitSeconds() > 0 ? Math.min(question.timeLimitSeconds(), cpuTimeLimitSeconds) : cpuTimeLimitSeconds;
        int memory = question != null && question.memoryLimitKb() > 0 ? Math.min(question.memoryLimitKb(), memoryLimitKb) : memoryLimitKb;
        return new ExecutionLimits(cpu, wallTimeLimitSeconds, memory, maxProcesses, maxFileSizeKb);
    }

    private void requireProvider() {
        if (!provider.isConfigured()) throw ExecutionProviderException.unavailable();
    }

    private static CodeLanguage language(String value) {
        return CodeLanguage.parse(value).orElseThrow(() -> new PracticeExecutionException(HttpStatus.BAD_REQUEST, "INVALID_LANGUAGE",
            "Unsupported language. Choose Java, Python, C++ or JavaScript."));
    }

    private static void requireSize(String text, int maxBytes, String message) {
        if (text != null && text.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new PracticeExecutionException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
        }
    }

    private static String stdinOf(String stdin) {
        return stdin == null ? "" : stdin;
    }

    /** Output comparison ignores trailing spaces on each line and trailing blank lines. */
    static String normalize(String text) {
        if (text == null) return "";
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> kept = new ArrayList<>(Arrays.stream(lines).map(String::stripTrailing).toList());
        while (!kept.isEmpty() && kept.getLast().isEmpty()) kept.removeLast();
        return String.join("\n", kept);
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_ERROR_CHARS ? text.substring(0, MAX_ERROR_CHARS) + "\n… (truncated)" : text;
    }

    private CodingQuestionResponse toResponse(CodingQuestion question) {
        return new CodingQuestionResponse(
            question.id(), question.topic(), question.title(), question.difficulty(), question.description(),
            question.inputFormat(), question.outputFormat(), question.constraints(),
            question.examples().stream().map(e -> new CodingQuestionResponse.Example(e.input(), e.output(), e.explanation())).toList(),
            question.starterCode(),
            Arrays.stream(CodeLanguage.values()).filter(l -> question.starterCode().containsKey(l.name())).map(Enum::name).toList(),
            question.hiddenTests().size(), question.timeLimitSeconds(), question.memoryLimitKb(), provider.isConfigured());
    }
}
