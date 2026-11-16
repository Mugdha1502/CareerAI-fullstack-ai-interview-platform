package com.careerai.backend.practice;

import com.careerai.backend.execution.CodeLanguage;
import com.careerai.backend.execution.ExecutionResult;
import com.careerai.backend.execution.ExecutionStatus;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs ALL hidden tests of a submission inside ONE program execution (one JDoodle credit instead
 * of one per test).
 *
 * The candidate's complete stdin/stdout program is wrapped by a small per-language harness that,
 * for each hidden input: redirects stdin to that input and captures stdout, runs the candidate's
 * program, restores the real streams and prints exactly one line
 *
 *   __CAREERAI_<nonce>__|<test number>|OK|<hex of captured stdout>
 *   __CAREERAI_<nonce>__|<test number>|ERR|<hex of the error/stack trace>
 *
 * The nonce is random per submission and never sent to the browser, and the candidate's own output
 * is captured (hex-encoded inside the marker line), so candidate output cannot pose as a result.
 * Expected outputs never leave the server: comparison happens in CodingPracticeService.
 */
public final class HiddenTestHarness {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final String CANDIDATE_CLASS = "CareerAiCandidate";

    private HiddenTestHarness() {
    }

    public static String newNonce() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    public static String build(CodeLanguage language, String source, List<String> inputs, String nonce) {
        List<String> hexInputs = inputs.stream().map(input -> "\"" + hex(input) + "\"").toList();
        String inputList = String.join(", ", hexInputs);
        return switch (language) {
            case PYTHON -> python(source, inputList, nonce);
            case JAVASCRIPT -> javascript(source, inputList, nonce);
            case JAVA -> java(source, inputList, nonce);
            case CPP -> cpp(source, inputList, nonce);
        };
    }

    /**
     * Per-test results from the combined run. Tests without a marker line inherit the overall
     * outcome (compile error, time limit, crash). Without any marker and without a recognisable
     * program failure the run is INTERNAL_ERROR (treated as a provider failure, never scored).
     */
    public static List<ExecutionResult> toResults(ExecutionResult combined, String nonce, int testCount) {
        Pattern marker = Pattern.compile("(?m)^__CAREERAI_" + Pattern.quote(nonce) + "__\\|(\\d+)\\|(OK|ERR)\\|([0-9a-f]*)\\s*$");
        String text = (combined.stdout() == null ? "" : combined.stdout()) + "\n" + (combined.stderr() == null ? "" : combined.stderr());
        Map<Integer, ExecutionResult> found = new TreeMap<>();
        Matcher matcher = marker.matcher(text);
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index < 1 || index > testCount || found.containsKey(index)) continue;
            String payload = unhex(matcher.group(3));
            found.put(index, "OK".equals(matcher.group(2))
                ? new ExecutionResult(ExecutionStatus.ACCEPTED, payload, "", "", null, combined.memoryKb(), "")
                : new ExecutionResult(ExecutionStatus.RUNTIME_ERROR, "", payload, "", null, combined.memoryKb(), ""));
        }

        List<ExecutionResult> results = new ArrayList<>();
        for (int i = 1; i <= testCount; i++) {
            ExecutionResult result = found.get(i);
            if (result == null) result = missing(combined, found.isEmpty());
            results.add(result);
        }
        return results;
    }

    private static ExecutionResult missing(ExecutionResult combined, boolean noMarkers) {
        return switch (combined.status()) {
            case COMPILATION_ERROR -> new ExecutionResult(ExecutionStatus.COMPILATION_ERROR, "", "", combined.compileOutput(), null, null, "");
            case TIME_LIMIT_EXCEEDED -> new ExecutionResult(ExecutionStatus.TIME_LIMIT_EXCEEDED, "", "", "", null, null, "Time limit exceeded");
            case RUNTIME_ERROR, MEMORY_LIMIT_EXCEEDED -> new ExecutionResult(combined.status(), "", combined.stderr(), "", null, null, "");
            // The program ended normally but printed no result for this test: it stopped the whole
            // process early (e.g. System.exit) — unless nothing at all came back (provider problem).
            case ACCEPTED -> noMarkers
                ? new ExecutionResult(ExecutionStatus.INTERNAL_ERROR, "", "", "", null, null, "No test results")
                : new ExecutionResult(ExecutionStatus.RUNTIME_ERROR, "", "The program exited before this test ran.", "", null, null, "");
            default -> new ExecutionResult(ExecutionStatus.INTERNAL_ERROR, "", "", "", null, null, "No test results");
        };
    }

    /* ---------- per-language harnesses ---------- */

    private static String python(String source, String inputs, String nonce) {
        return """
            import base64, io, sys, traceback
            CAREERAI_NONCE = "%s"
            CAREERAI_INPUTS = [%s]
            CAREERAI_SOURCE = bytes.fromhex("%s").decode("utf-8")
            careerai_code = compile(CAREERAI_SOURCE, "solution.py", "exec")
            for careerai_index, careerai_input in enumerate(CAREERAI_INPUTS, 1):
                real_in, real_out, real_err = sys.stdin, sys.stdout, sys.stderr
                captured_out, captured_err = io.StringIO(), io.StringIO()
                sys.stdin = io.StringIO(bytes.fromhex(careerai_input).decode("utf-8"))
                sys.stdout, sys.stderr = captured_out, captured_err
                careerai_status = "OK"
                try:
                    exec(careerai_code, {"__name__": "__main__"})
                except SystemExit as careerai_exit:
                    if careerai_exit.code not in (None, 0):
                        careerai_status = "ERR"
                        captured_err.write("SystemExit: %%s\\n" %% careerai_exit.code)
                except BaseException:
                    careerai_status = "ERR"
                    traceback.print_exc(file=captured_err)
                finally:
                    sys.stdin, sys.stdout, sys.stderr = real_in, real_out, real_err
                careerai_payload = captured_out.getvalue() if careerai_status == "OK" else captured_err.getvalue()
                print("__CAREERAI_%%s__|%%d|%%s|%%s" %% (CAREERAI_NONCE, careerai_index, careerai_status, careerai_payload.encode("utf-8").hex()), flush=True)
            """.formatted(nonce, inputs, hex(source));
    }

    private static String javascript(String source, String inputs, String nonce) {
        return """
            const careeraiVm = require("vm");
            const careeraiFs = require("fs");
            const careeraiUtil = require("util");
            const CAREERAI_NONCE = "%s";
            const CAREERAI_INPUTS = [%s];
            const careeraiSource = Buffer.from("%s", "hex").toString("utf8");
            let careeraiScript;
            try {
              careeraiScript = new careeraiVm.Script(careeraiSource, { filename: "solution.js" });
            } catch (error) {
              console.error(error && error.stack ? error.stack : String(error));
              process.exit(1);
            }
            CAREERAI_INPUTS.forEach((encoded, index) => {
              const input = Buffer.from(encoded, "hex").toString("utf8");
              let out = "";
              let err = "";
              const fakeFs = Object.assign({}, careeraiFs, {
                readFileSync: (file, ...rest) => (file === 0 || file === "/dev/stdin")
                  ? (rest.length ? input : Buffer.from(input, "utf8"))
                  : careeraiFs.readFileSync(file, ...rest)
              });
              const capture = (...args) => { out += careeraiUtil.format(...args) + "\\n"; };
              const captureErr = (...args) => { err += careeraiUtil.format(...args) + "\\n"; };
              const context = careeraiVm.createContext({
                require: (name) => (name === "fs" ? fakeFs : require(name)),
                console: { log: capture, info: capture, warn: captureErr, error: captureErr },
                process: { stdout: { write: (chunk) => { out += String(chunk); return true; } }, stderr: { write: (chunk) => { err += String(chunk); return true; } }, argv: [], env: {}, exit: (code) => { throw new Error("process.exit(" + code + ")"); } },
                Buffer, module: { exports: {} }, exports: {}, setTimeout, clearTimeout
              });
              let status = "OK";
              try {
                careeraiScript.runInContext(context, { timeout: 4000 });
              } catch (error) {
                status = "ERR";
                err += (error && error.stack) ? error.stack : String(error);
              }
              const payload = status === "OK" ? out : err;
              process.stdout.write("__CAREERAI_" + CAREERAI_NONCE + "__|" + (index + 1) + "|" + status + "|" + Buffer.from(payload, "utf8").toString("hex") + "\\n");
            });
            """.formatted(nonce, inputs, hex(source));
    }

    /**
     * The harness is the public Main class (first type in the file); the candidate's Main class is
     * renamed and made package-private, and its imports are hoisted to the top of the file.
     */
    private static String java(String source, String inputs, String nonce) {
        StringBuilder imports = new StringBuilder();
        StringBuilder body = new StringBuilder();
        for (String line : source.split("\\R", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ") && trimmed.endsWith(";")) continue;
            if (trimmed.startsWith("import ") && trimmed.endsWith(";")) imports.append(trimmed).append('\n');
            else body.append(line).append('\n');
        }
        String candidate = body.toString()
            .replaceAll("\\bMain\\b", CANDIDATE_CLASS)
            .replaceAll("(?m)^public\\s+((?:final\\s+|abstract\\s+)*(?:class|interface|enum|record)\\s)", "$1");
        String harness = """
            public class Main {
                private static final String CAREERAI_NONCE = "%s";
                private static final String[] CAREERAI_INPUTS = { %s };

                public static void main(String[] args) {
                    java.io.PrintStream realOut = System.out;
                    java.io.PrintStream realErr = System.err;
                    java.io.InputStream realIn = System.in;
                    java.util.HexFormat hex = java.util.HexFormat.of();
                    for (int i = 0; i < CAREERAI_INPUTS.length; i++) {
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
                        String status = "OK";
                        try {
                            System.setIn(new java.io.ByteArrayInputStream(hex.parseHex(CAREERAI_INPUTS[i])));
                            System.setOut(new java.io.PrintStream(out, true, "UTF-8"));
                            System.setErr(new java.io.PrintStream(err, true, "UTF-8"));
                            %s.main(new String[0]);
                        } catch (Throwable t) {
                            status = "ERR";
                            t.printStackTrace(new java.io.PrintStream(err, true));
                        } finally {
                            System.out.flush();
                            System.err.flush();
                            System.setOut(realOut);
                            System.setErr(realErr);
                            System.setIn(realIn);
                        }
                        byte[] payload = "OK".equals(status) ? out.toByteArray() : err.toByteArray();
                        realOut.println("__CAREERAI_" + CAREERAI_NONCE + "__|" + (i + 1) + "|" + status + "|" + hex.formatHex(payload));
                        realOut.flush();
                    }
                }
            }
            """.formatted(nonce, inputs, CANDIDATE_CLASS);
        return imports + "\n" + harness + "\n" + candidate;
    }

    /** The candidate's main() is renamed by the preprocessor; the harness provides the real main. */
    private static String cpp(String source, String inputs, String nonce) {
        String head = """
            #include <exception>
            #include <iostream>
            #include <sstream>
            #include <string>

            static std::string careerai_unhex(const std::string& hex) {
                std::string out;
                for (size_t i = 0; i + 1 < hex.size(); i += 2) out.push_back((char) std::stoi(hex.substr(i, 2), nullptr, 16));
                return out;
            }

            static std::string careerai_hex(const std::string& text) {
                static const char* digits = "0123456789abcdef";
                std::string out;
                for (unsigned char c : text) { out.push_back(digits[c >> 4]); out.push_back(digits[c & 15]); }
                return out;
            }

            #define main careerai_candidate_main
            """;
        String tail = """

            #undef main

            static const char* CAREERAI_INPUTS[] = { %s };

            int main() {
                std::streambuf* realIn = std::cin.rdbuf();
                std::streambuf* realOut = std::cout.rdbuf();
                const int count = (int) (sizeof(CAREERAI_INPUTS) / sizeof(CAREERAI_INPUTS[0]));
                for (int i = 0; i < count; i++) {
                    std::istringstream input(careerai_unhex(CAREERAI_INPUTS[i]));
                    std::ostringstream output;
                    std::string status = "OK";
                    std::string payload;
                    std::cin.rdbuf(input.rdbuf());
                    std::cin.clear();
                    std::cout.rdbuf(output.rdbuf());
                    try {
                        careerai_candidate_main();
                        std::cout.flush();
                        payload = output.str();
                    } catch (const std::exception& e) {
                        status = "ERR";
                        payload = std::string("terminate called after throwing an exception: ") + e.what();
                    } catch (...) {
                        status = "ERR";
                        payload = "terminate called after throwing an unknown exception";
                    }
                    std::cout.rdbuf(realOut);
                    std::cin.rdbuf(realIn);
                    std::cin.clear();
                    std::cout << "__CAREERAI_%s__|" << (i + 1) << "|" << status << "|" << careerai_hex(payload) << std::endl;
                }
                return 0;
            }
            """.formatted(inputs, nonce);
        return head + source + tail;
    }

    private static String hex(String text) {
        return HEX.formatHex((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    private static String unhex(String hex) {
        try {
            return new String(HEX.parseHex(hex), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }
}
