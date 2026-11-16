package com.careerai.backend.billing;

import com.careerai.backend.execution.FakeCodeExecutionProvider;
import com.careerai.backend.practice.CodingQuestionCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared full-stack setup for plan/billing tests (in-memory test database only): a movable clock,
 * the fake payment gateway (no Razorpay calls) and the fake code-execution provider (no JDoodle calls).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BillingTestSupport.Config.class)
abstract class BillingTestSupport {

    static final Instant START = Instant.parse("2026-11-10T06:00:00Z"); // 11:30 in Asia/Kolkata
    static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger USERS = new AtomicInteger();

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }

        @Bean
        @Primary
        FakePaymentGateway fakePaymentGateway() {
            return new FakePaymentGateway();
        }

        @Bean
        @Primary
        FakeCodeExecutionProvider fakeCodeExecutionProvider(CodingQuestionCatalog catalog) {
            return new FakeCodeExecutionProvider(catalog);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    MutableClock clock;

    @Autowired
    FakePaymentGateway gateway;

    @Autowired
    FakeCodeExecutionProvider codeProvider;

    record Response(int status, JsonNode body, String raw) {
    }

    record TestUser(long id, String token, String email) {
    }

    @BeforeEach
    void resetFakes() {
        clock.set(START);
        gateway.reset();
        codeProvider.configured = true;
        codeProvider.timeout = false;
    }

    TestUser register() throws Exception {
        String email = "billing-" + USERS.incrementAndGet() + "-" + System.nanoTime() % 100000 + "@example.test";
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("name", "Billing Tester", "email", email, "password", "secret123", "confirmPassword", "secret123")))).andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        return new TestUser(body.path("userId").asLong(), body.path("token").asString(), email);
    }

    Response call(String method, String path, String token, Object body) throws Exception {
        var request = method.equals("GET") ? get(path) : post(path).contentType(MediaType.APPLICATION_JSON)
            .content(body instanceof String text ? text : JSON.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        MvcResult result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString();
        return new Response(result.getResponse().getStatus(), content.isBlank() ? JSON.nullNode() : JSON.readTree(content), content);
    }

    JsonNode me(TestUser user) throws Exception {
        return call("GET", "/api/billing/me", user.token(), null).body();
    }

    int used(TestUser user, String feature) throws Exception {
        return me(user).path("features").path(feature).path("used").asInt();
    }

    Response startInterview(TestUser user, String mode) throws Exception {
        return call("POST", "/api/interviews", user.token(), Map.of("type", "TECHNICAL", "mode", mode, "targetRole", "Java Developer", "difficulty", "Intermediate", "questionCount", 3));
    }
}
