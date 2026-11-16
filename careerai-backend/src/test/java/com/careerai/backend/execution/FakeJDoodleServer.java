package com.careerai.backend.execution;

import com.careerai.backend.practice.CodingQuestion;
import com.careerai.backend.practice.CodingQuestionCatalog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local stand-in for the JDoodle Compiler API (POST /v1/execute, POST /v1/credit-spent).
 * No code is executed and no JDoodle credits are used. Responses follow JDoodle's documented
 * shape: HTTP 200 { output, statusCode, memory, cpuTime } even for compile/runtime errors.
 *
 * Behaviour comes from markers in the submitted program:
 *   COMPILE_ERROR | RUNTIME_ERROR | INFINITE (JDoodle timeout) | otherwise "echo:<stdin>"
 * and for combined hidden-test programs (HiddenTestHarness):
 *   "SOLVED <questionId>" | "PASS_FIRST_3 <questionId>" | COMPILE_ERROR | INFINITE
 */
public class FakeJDoodleServer implements AutoCloseable {

    public static final String CLIENT_ID = "test-client-id";
    public static final String CLIENT_SECRET = "test-client-secret";
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final HexFormat HEX = HexFormat.of();
    private static final Pattern NONCE = Pattern.compile("__CAREERAI_([0-9a-f]{16})__|CAREERAI_NONCE = \"([0-9a-f]{16})\"");
    private static final Pattern HEX_TOKEN = Pattern.compile("\"([0-9a-f]*)\"");

    private final HttpServer server;
    private final CodingQuestionCatalog catalog;
    public final List<JsonNode> executeBodies = new CopyOnWriteArrayList<>();
    public final List<Map<String, String>> headers = new CopyOnWriteArrayList<>();
    public final Map<String, Integer> creditCalls = new ConcurrentHashMap<>();
    public volatile int forcedHttpStatus = 0;
    public volatile int errorInBodyStatus = 0;
    public volatile long delayMs = 0;
    public volatile boolean withFlags = false;

    public FakeJDoodleServer(CodingQuestionCatalog catalog) throws IOException {
        this.catalog = catalog;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset() {
        executeBodies.clear();
        headers.clear();
        creditCalls.clear();
        forcedHttpStatus = 0;
        errorInBodyStatus = 0;
        delayMs = 0;
        withFlags = false;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> h = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), v.getFirst()));
        headers.add(h);
        JsonNode body = JSON.readTree(exchange.getRequestBody().readAllBytes());
        if (delayMs > 0) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        if (!CLIENT_ID.equals(body.path("clientId").asString("")) || !CLIENT_SECRET.equals(body.path("clientSecret").asString(""))) {
            send(exchange, 401, "{\"error\":\"Unauthorized Request\",\"statusCode\":401}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/v1/credit-spent")) {
            send(exchange, 200, "{\"used\":" + executeBodies.size() + "}");
            return;
        }
        if (!path.equals("/v1/execute")) {
            send(exchange, 404, "{\"error\":\"Not found\",\"statusCode\":404}");
            return;
        }
        executeBodies.add(body);
        if (forcedHttpStatus != 0) {
            send(exchange, forcedHttpStatus, forcedHttpStatus == 429
                ? "{\"error\":\"Daily limit reached\",\"statusCode\":429}"
                : "{\"error\":\"Server error\",\"statusCode\":" + forcedHttpStatus + "}");
            return;
        }
        if (errorInBodyStatus != 0) {
            send(exchange, 200, "{\"error\":\"Request failed\",\"statusCode\":" + errorInBodyStatus + "}");
            return;
        }
        String script = body.path("script").asString("");
        String language = body.path("language").asString("");
        String output = script.contains("CAREERAI_INPUTS") ? harnessOutput(script, language) : runOutput(script, language, body.path("stdin").asString(""));
        String flags = "";
        if (withFlags) {
            boolean compiled = !script.contains("COMPILE_ERROR");
            boolean success = compiled && !script.contains("RUNTIME_ERROR") && !script.contains("INFINITE");
            flags = ",\"isCompiled\":" + compiled + ",\"isExecutionSuccess\":" + success;
        }
        send(exchange, 200, "{\"output\":" + JSON.writeValueAsString(output) + ",\"statusCode\":200,\"memory\":\"10240\",\"cpuTime\":\"0.12\"" + flags + "}");
    }

    private static String runOutput(String script, String language, String stdin) {
        if (script.contains("COMPILE_ERROR")) return compileError(language);
        if (script.contains("RUNTIME_ERROR")) return runtimeError(language);
        if (script.contains("INFINITE")) return "partial\n\n\n JDoodle - Timeout \nIf your program reads input, please enter the inputs in the STDIN box above.";
        return "echo:" + stdin + "\n";
    }

    private static String compileError(String language) {
        return switch (language) {
            case "java" -> "Main.java:5: error: ';' expected\n        return 0\n                ^\n1 error\n";
            case "cpp17" -> "jdoodle.cpp: In function 'int main()':\njdoodle.cpp:5:17: error: expected ';' before '}' token\n";
            case "python3" -> "  File \"/home/jdoodle.py\", line 2\n    return (\n           ^\nSyntaxError: '(' was never closed\n";
            default -> "/home/jdoodle.js:2\n  return (\n         ^\n\nSyntaxError: Unexpected token '}'\n    at internalCompileFunction (node:internal/vm:73:18)\n";
        };
    }

    private static String runtimeError(String language) {
        return switch (language) {
            case "java" -> "before crash\nException in thread \"main\" java.lang.ArithmeticException: / by zero\n\tat Main.main(Main.java:7)\n";
            case "cpp17" -> "before crash\nSegmentation fault (core dumped)\n";
            case "python3" -> "before crash\nTraceback (most recent call last):\n  File \"/home/jdoodle.py\", line 3, in <module>\nZeroDivisionError: division by zero\n";
            default -> "before crash\n/home/jdoodle.js:3\n  undefinedFunction();\n  ^\n\nReferenceError: undefinedFunction is not defined\n    at Object.<anonymous> (/home/jdoodle.js:3:3)\n";
        };
    }

    /** Answers a combined hidden-test program with one marker line per test (no code is run). */
    private String harnessOutput(String script, String language) {
        Matcher nonceMatcher = NONCE.matcher(script);
        String nonce = nonceMatcher.find() ? (nonceMatcher.group(1) != null ? nonceMatcher.group(1) : nonceMatcher.group(2)) : "";
        String inputsLine = script.lines().filter(line -> line.contains("CAREERAI_INPUTS") && line.contains("\"")).findFirst().orElse("");
        List<String> inputs = new ArrayList<>();
        Matcher token = HEX_TOKEN.matcher(inputsLine.substring(Math.max(0, inputsLine.indexOf("CAREERAI_INPUTS"))));
        while (token.find()) inputs.add(new String(HEX.parseHex(token.group(1)), StandardCharsets.UTF_8));

        // Python/JavaScript harnesses carry the candidate source hex-encoded: decode it for markers.
        StringBuilder searchable = new StringBuilder(script);
        Matcher longHex = Pattern.compile("\"([0-9a-f]{40,})\"").matcher(script);
        while (longHex.find()) searchable.append('\n').append(new String(HEX.parseHex(longHex.group(1)), StandardCharsets.UTF_8));
        String text = searchable.toString();

        if (text.contains("COMPILE_ERROR")) return compileError(language);
        CodingQuestion question = catalog.all().stream().filter(q -> text.contains(q.id())).findFirst().orElse(null);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < inputs.size(); i++) {
            if (text.contains("INFINITE") && i == 1) {
                out.append("\n JDoodle - Timeout \n");
                return out.toString();
            }
            String stdout;
            if (text.contains("PASS_FIRST_3") && i >= 3) stdout = "wrong\n";
            else if (question != null && (text.contains("SOLVED") || text.contains("PASS_FIRST_3"))) stdout = expected(question, inputs.get(i)) + "\n";
            else stdout = "echo:" + inputs.get(i) + "\n";
            out.append("__CAREERAI_").append(nonce).append("__|").append(i + 1).append("|OK|")
                .append(HEX.formatHex(stdout.getBytes(StandardCharsets.UTF_8))).append('\n');
        }
        return out.toString();
    }

    private static String expected(CodingQuestion question, String stdin) {
        return question.hiddenTests().stream().filter(t -> t.stdin().equals(stdin)).map(CodingQuestion.TestCase::expectedOutput).findFirst().orElse("unknown");
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
