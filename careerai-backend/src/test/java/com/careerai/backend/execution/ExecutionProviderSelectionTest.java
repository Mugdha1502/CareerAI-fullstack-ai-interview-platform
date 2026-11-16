package com.careerai.backend.execution;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** CODE_EXECUTION_PROVIDER selects exactly one provider bean; a bad value never breaks startup. */
class ExecutionProviderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
        .withUserConfiguration(JDoodleCodeExecutionProvider.class, Judge0CodeExecutionProvider.class, DisabledCodeExecutionProvider.class);

    @Test
    void defaultIsJDoodle() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(CodeExecutionProvider.class);
            assertThat(context.getBean(CodeExecutionProvider.class)).isInstanceOf(JDoodleCodeExecutionProvider.class);
            assertThat(context.getBean(CodeExecutionProvider.class).isConfigured()).isFalse(); // no credentials → still starts
        });
    }

    @Test
    void jdoodleSelected() {
        runner.withPropertyValues("app.execution.provider=jdoodle", "app.execution.jdoodle.client-id=a", "app.execution.jdoodle.client-secret=b")
            .run(context -> {
                assertThat(context).hasSingleBean(CodeExecutionProvider.class);
                assertThat(context.getBean(CodeExecutionProvider.class)).isInstanceOf(JDoodleCodeExecutionProvider.class);
                assertThat(context.getBean(CodeExecutionProvider.class).isConfigured()).isTrue();
            });
    }

    @Test
    void judge0Selected() {
        runner.withPropertyValues("app.execution.provider=judge0").run(context -> {
            assertThat(context).hasSingleBean(CodeExecutionProvider.class);
            assertThat(context.getBean(CodeExecutionProvider.class)).isInstanceOf(Judge0CodeExecutionProvider.class);
            assertThat(context.getBean(CodeExecutionProvider.class).prefersCombinedSubmission()).isFalse();
        });
    }

    @Test
    void noneOrUnknownFallsBackToDisabled() {
        for (String value : new String[] { "none", "something-else" }) {
            runner.withPropertyValues("app.execution.provider=" + value).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(CodeExecutionProvider.class);
                assertThat(context.getBean(CodeExecutionProvider.class)).isInstanceOf(DisabledCodeExecutionProvider.class);
            });
        }
    }
}
