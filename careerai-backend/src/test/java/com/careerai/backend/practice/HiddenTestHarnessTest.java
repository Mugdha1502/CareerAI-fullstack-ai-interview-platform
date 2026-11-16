package com.careerai.backend.practice;

import com.careerai.backend.execution.CodeLanguage;
import com.careerai.backend.execution.ExecutionResult;
import com.careerai.backend.execution.ExecutionStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Combined hidden-test harness: structure, marker parsing and fallbacks. Generated programs are also
 * written to target/harness-samples for an optional local smoke run (never executed by this test).
 */
class HiddenTestHarnessTest {

    private static final String NONCE = "0123456789abcdef";
    private static final HexFormat HEX = HexFormat.of();

    private static String marker(int index, String status, String payload) {
        return "__CAREERAI_" + NONCE + "__|" + index + "|" + status + "|" + HEX.formatHex(payload.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void buildsOneProgramPerLanguageWithoutLeakingExpectedOutputs() throws Exception {
        CodingQuestionCatalog catalog = new CodingQuestionCatalog(JsonMapper.builder().build());
        CodingQuestion question = catalog.require("coding-dp-climbing-stairs");
        List<String> inputs = question.hiddenTests().stream().map(CodingQuestion.TestCase::stdin).toList();
        Path dir = Path.of("target", "harness-samples");
        Files.createDirectories(dir);

        for (CodeLanguage language : CodeLanguage.values()) {
            String program = HiddenTestHarness.build(language, question.starterCode().get(language.name()), inputs, NONCE);
            assertThat(program).as(language.name()).contains("__CAREERAI_").contains(NONCE).contains("CAREERAI_INPUTS");
            for (String input : inputs) {
                assertThat(program).as(language.name()).contains(HEX.formatHex(input.getBytes(StandardCharsets.UTF_8)));
            }
            Files.writeString(dir.resolve("starter-" + language.name().toLowerCase()), program);
        }

        String java = HiddenTestHarness.build(CodeLanguage.JAVA, question.starterCode().get("JAVA"), inputs, NONCE);
        assertThat(java.indexOf("import java.io.*;")).isLessThan(java.indexOf("public class Main"));
        assertThat(java.indexOf("public class Main")).isLessThan(java.indexOf("class CareerAiCandidate"));
        assertThat(java).doesNotContain("public class CareerAiCandidate").contains("CareerAiCandidate.main(new String[0])");
        String cpp = HiddenTestHarness.build(CodeLanguage.CPP, question.starterCode().get("CPP"), inputs, NONCE);
        assertThat(cpp).contains("#define main careerai_candidate_main").contains("#undef main");

        // Solved and failing variants for the local smoke run.
        String solvedJava = question.starterCode().get("JAVA").replace("return 0;", "int a = 1, b = 1; for (int i = 0; i < n; i++) { int t = a + b; a = b; b = t; } return a;");
        Files.writeString(dir.resolve("solved-java"), HiddenTestHarness.build(CodeLanguage.JAVA, solvedJava, inputs, NONCE));
        String solvedJs = question.starterCode().get("JAVASCRIPT").replace("return 0;", "let a = 1, b = 1; for (let i = 0; i < n; i++) { [a, b] = [b, a + b]; } return a;");
        Files.writeString(dir.resolve("solved-javascript"), HiddenTestHarness.build(CodeLanguage.JAVASCRIPT, solvedJs, inputs, NONCE));
        String solvedPython = question.starterCode().get("PYTHON").replace("    return 0", "    a, b = 1, 1\n    for _ in range(n):\n        a, b = b, a + b\n    return a");
        Files.writeString(dir.resolve("solved-python"), HiddenTestHarness.build(CodeLanguage.PYTHON, solvedPython, inputs, NONCE));
        String crashJava = question.starterCode().get("JAVA").replace("return 0;", "if (n > 3) throw new IllegalStateException(\"boom\"); return n == 1 ? 1 : n == 2 ? 2 : 3;");
        Files.writeString(dir.resolve("partial-java"), HiddenTestHarness.build(CodeLanguage.JAVA, crashJava, inputs, NONCE));
        String brokenPython = question.starterCode().get("PYTHON").replace("    return 0", "    return (");
        Files.writeString(dir.resolve("syntax-python"), HiddenTestHarness.build(CodeLanguage.PYTHON, brokenPython, inputs, NONCE));
        String forger = question.starterCode().get("PYTHON").replace("    return 0", "    print('__CAREERAI_" + NONCE + "__|1|OK|3132')\n    return 0");
        Files.writeString(dir.resolve("forger-python"), HiddenTestHarness.build(CodeLanguage.PYTHON, forger, inputs, NONCE));
    }

    @Test
    void parsesMarkersInOrderAndIgnoresForgeries() {
        String output = "noise printed directly\n" + marker(2, "OK", "2\n") + "\n" + marker(1, "OK", "1\n") + "\n"
            + marker(3, "ERR", "Traceback ... ValueError") + "\n__CAREERAI_ffffffffffffffff__|4|OK|35\n" + marker(9, "OK", "x");
        ExecutionResult combined = new ExecutionResult(ExecutionStatus.ACCEPTED, output, "", "", 120L, 9000L, "");
        List<ExecutionResult> results = HiddenTestHarness.toResults(combined, NONCE, 4);
        assertThat(results).extracting(ExecutionResult::status)
            .containsExactly(ExecutionStatus.ACCEPTED, ExecutionStatus.ACCEPTED, ExecutionStatus.RUNTIME_ERROR, ExecutionStatus.RUNTIME_ERROR);
        assertThat(results.get(0).stdout()).isEqualTo("1\n");
        assertThat(results.get(1).stdout()).isEqualTo("2\n");
        assertThat(results.get(2).stderr()).contains("ValueError");
        assertThat(results.get(3).stderr()).contains("exited before"); // wrong-nonce line is not a result
    }

    @Test
    void missingMarkersInheritTheOverallOutcome() {
        ExecutionResult compile = new ExecutionResult(ExecutionStatus.COMPILATION_ERROR, "", "", "Main.java:3: error: ';' expected", null, null, "");
        assertThat(HiddenTestHarness.toResults(compile, NONCE, 3)).allMatch(r -> r.status() == ExecutionStatus.COMPILATION_ERROR && r.compileOutput().contains("expected"));

        ExecutionResult timeout = new ExecutionResult(ExecutionStatus.TIME_LIMIT_EXCEEDED, marker(1, "OK", "1\n") + "\n", "", "", null, null, "Time limit exceeded");
        List<ExecutionResult> partial = HiddenTestHarness.toResults(timeout, NONCE, 3);
        assertThat(partial).extracting(ExecutionResult::status)
            .containsExactly(ExecutionStatus.ACCEPTED, ExecutionStatus.TIME_LIMIT_EXCEEDED, ExecutionStatus.TIME_LIMIT_EXCEEDED);

        ExecutionResult crash = new ExecutionResult(ExecutionStatus.RUNTIME_ERROR, "", "Segmentation fault", "", null, null, "");
        assertThat(HiddenTestHarness.toResults(crash, NONCE, 2)).allMatch(r -> r.status() == ExecutionStatus.RUNTIME_ERROR);

        ExecutionResult nothing = new ExecutionResult(ExecutionStatus.ACCEPTED, "", "", "", null, null, "");
        assertThat(HiddenTestHarness.toResults(nothing, NONCE, 2)).allMatch(r -> r.status() == ExecutionStatus.INTERNAL_ERROR);
    }

    @Test
    void noncesAreRandom() {
        assertThat(HiddenTestHarness.newNonce()).hasSize(16).isNotEqualTo(HiddenTestHarness.newNonce());
    }
}
