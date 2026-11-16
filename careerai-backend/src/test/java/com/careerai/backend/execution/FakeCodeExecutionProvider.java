package com.careerai.backend.execution;

import com.careerai.backend.practice.CodingQuestion;
import com.careerai.backend.practice.CodingQuestionCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for the external sandbox: no Judge0, no quota, no code is executed anywhere.
 * Behaviour is selected by markers in the submitted source:
 *   COMPILE_ERROR, RUNTIME_ERROR, SLOW (time limit), "SOLVED <questionId>" (prints that question's
 *   expected output for its test inputs), "PASS_FIRST_3 <questionId>" (only the first three pass).
 */
public class FakeCodeExecutionProvider implements CodeExecutionProvider {

    private final CodingQuestionCatalog catalog;
    public final List<ExecutionRequest> requests = new CopyOnWriteArrayList<>();
    public volatile boolean configured = true;
    public volatile boolean timeout = false;

    public FakeCodeExecutionProvider(CodingQuestionCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        if (!configured) throw ExecutionProviderException.unavailable();
        if (timeout) throw ExecutionProviderException.timeout();
        requests.add(request);
        String source = request.sourceCode();
        if (source.contains("COMPILE_ERROR")) {
            return new ExecutionResult(ExecutionStatus.COMPILATION_ERROR, "", "", "Main.java:3: error: ';' expected", null, null, "");
        }
        if (source.contains("RUNTIME_ERROR")) {
            return new ExecutionResult(ExecutionStatus.RUNTIME_ERROR, "", "Exception in thread \"main\" java.lang.ArithmeticException: / by zero", "", 12L, 9000L, "");
        }
        if (source.contains("SLOW")) {
            return new ExecutionResult(ExecutionStatus.TIME_LIMIT_EXCEEDED, "", "", "", 2500L, 9000L, "Time limit exceeded");
        }
        if (source.contains("SOLVED")) {
            return new ExecutionResult(ExecutionStatus.ACCEPTED, expectedFor(request.sourceCode(), request.stdin()) + "\n", "", "", 40L, 10240L, "");
        }
        return new ExecutionResult(ExecutionStatus.ACCEPTED, "Hello from " + request.language().name() + "\n", "", "", 30L, 9000L, "");
    }

    @Override
    public List<ExecutionResult> executeBatch(List<ExecutionRequest> batch) {
        if (!configured) throw ExecutionProviderException.unavailable();
        if (timeout) throw ExecutionProviderException.timeout();
        List<ExecutionResult> results = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            ExecutionRequest request = batch.get(i);
            if (request.sourceCode().contains("PASS_FIRST_3")) {
                results.add(i < 3
                    ? new ExecutionResult(ExecutionStatus.ACCEPTED, expectedFor(request.sourceCode(), request.stdin()) + "\n", "", "", 40L, 10240L, "")
                    : new ExecutionResult(ExecutionStatus.ACCEPTED, "wrong\n", "", "", 40L, 10240L, ""));
            } else {
                results.add(execute(request));
            }
        }
        return results;
    }

    private String expectedFor(String source, String stdin) {
        for (CodingQuestion question : catalog.all()) {
            if (!source.contains(question.id())) continue;
            for (CodingQuestion.TestCase test : question.hiddenTests()) {
                if (test.stdin().equals(stdin)) return test.expectedOutput();
            }
        }
        return "unknown";
    }
}
