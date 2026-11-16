package com.careerai.backend.execution;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * app.execution.provider=none (or any value other than jdoodle / judge0): the app runs normally and
 * execution endpoints answer 503. Exactly one CodeExecutionProvider bean exists for every value.
 */
@Component
@ConditionalOnExpression("!'${app.execution.provider:jdoodle}'.equals('jdoodle') && !'${app.execution.provider:jdoodle}'.equals('judge0')")
public class DisabledCodeExecutionProvider implements CodeExecutionProvider {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        throw ExecutionProviderException.unavailable();
    }

    @Override
    public List<ExecutionResult> executeBatch(List<ExecutionRequest> requests) {
        throw ExecutionProviderException.unavailable();
    }
}
