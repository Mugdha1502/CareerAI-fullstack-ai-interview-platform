package com.careerai.backend.security;

import com.careerai.backend.billing.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Failed-login throttling through the real /api/auth/login endpoint (in-memory test database). */
@SpringBootTest(properties = {
    "app.security.login.max-failures-per-account=5",
    "app.security.login.max-failures-per-ip=12",
    "app.security.login.window=PT15M",
    "app.security.trusted-proxies=10.0.0.0/8"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginRateLimitIntegrationTest.Config.class)
class LoginRateLimitIntegrationTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger IPS = new AtomicInteger(1);
    private static final String PASSWORD = "secret123";

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock loginTestClock() {
            return new MutableClock(Instant.parse("2026-11-23T10:00:00Z"));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MutableClock clock;

    private String ip;

    private record Response(int status, JsonNode body, String retryAfter) {
    }

    @BeforeEach
    void freshClient() {
        ip = "198.51.100." + IPS.getAndIncrement(); // each test starts from its own client IP
    }

    private String register() throws Exception {
        String email = "limit-" + System.nanoTime() + "@example.test";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("name", "Limit Tester", "email", email, "password", PASSWORD, "confirmPassword", PASSWORD))));
        return email;
    }

    private Response login(String email, String password, String fromIp) throws Exception {
        return login(email, password, fromIp, null);
    }

    private Response login(String email, String password, String fromIp, String forwardedFor) throws Exception {
        var request = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("email", email, "password", password)))
            .with(r -> { r.setRemoteAddr(fromIp); return r; });
        if (forwardedFor != null) request.header("X-Forwarded-For", forwardedFor);
        MvcResult result = mvc.perform(request).andReturn();
        String raw = result.getResponse().getContentAsString();
        return new Response(result.getResponse().getStatus(), raw.isBlank() ? JSON.nullNode() : JSON.readTree(raw), result.getResponse().getHeader("Retry-After"));
    }

    @Test
    void repeatedFailuresAreThrottledEvenForTheCorrectPassword() throws Exception {
        String email = register();
        for (int i = 0; i < 5; i++) assertThat(login(email, "wrong-password", ip).status()).isEqualTo(401);

        Response blocked = login(email, "wrong-password", ip);
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.body().path("code").asString()).isEqualTo("LOGIN_RATE_LIMITED");
        assertThat(Long.parseLong(blocked.retryAfter())).isBetween(1L, 900L);
        assertThat(blocked.body().toString()).doesNotContain(email).doesNotContain("exist");
        // While throttled, even the right password gets 429 (no oracle for guessing).
        assertThat(login(email, PASSWORD, ip).status()).isEqualTo(429);
    }

    @Test
    void unknownEmailsAreThrottledExactlyLikeRealOnes() throws Exception {
        String real = register();
        String unknown = "nobody-" + System.nanoTime() + "@example.test";
        Response realFailure = login(real, "wrong-password", ip);
        Response unknownFailure = login(unknown, "wrong-password", ip);
        assertThat(unknownFailure.status()).isEqualTo(realFailure.status()).isEqualTo(401);
        assertThat(unknownFailure.body().path("message").asString()).isEqualTo(realFailure.body().path("message").asString());

        for (int i = 0; i < 4; i++) {
            login(real, "wrong-password", ip);
            login(unknown, "wrong-password", ip);
        }
        Response realBlocked = login(real, "wrong-password", ip);
        Response unknownBlocked = login(unknown, "wrong-password", ip);
        assertThat(realBlocked.status()).isEqualTo(429);
        assertThat(unknownBlocked.status()).isEqualTo(429);
        assertThat(unknownBlocked.body()).isEqualTo(realBlocked.body());
    }

    @Test
    void oneAttackerDoesNotLockOutTheRealUserOrOtherAccounts() throws Exception {
        String victim = register();
        String other = register();
        for (int i = 0; i < 6; i++) login(victim, "wrong-password", ip);
        assertThat(login(victim, PASSWORD, ip).status()).isEqualTo(429);

        // The real user on another network still signs in.
        assertThat(login(victim, PASSWORD, "203.0.113.77").status()).isEqualTo(200);
        // Another account from the attacker's IP is not blocked by the per-account limit.
        assertThat(login(other, PASSWORD, ip).status()).isEqualTo(200);
    }

    @Test
    void throttlingEndsWhenTheWindowPasses() throws Exception {
        String email = register();
        for (int i = 0; i < 6; i++) login(email, "wrong-password", ip);
        assertThat(login(email, PASSWORD, ip).status()).isEqualTo(429);

        clock.advance(Duration.ofMinutes(14));
        assertThat(login(email, PASSWORD, ip).status()).isEqualTo(429);
        clock.advance(Duration.ofMinutes(2));
        assertThat(login(email, PASSWORD, ip).status()).isEqualTo(200);
    }

    @Test
    void aSuccessfulLoginResetsTheAccountCounter() throws Exception {
        String email = register();
        for (int i = 0; i < 4; i++) assertThat(login(email, "wrong-password", ip).status()).isEqualTo(401);
        assertThat(login(email, PASSWORD, ip).status()).isEqualTo(200);
        for (int i = 0; i < 4; i++) assertThat(login(email, "wrong-password", ip).status()).isEqualTo(401);
        assertThat(login(email, PASSWORD, ip).status()).isEqualTo(200);
    }

    @Test
    void passwordSprayingFromOneIpIsThrottledAcrossAccounts() throws Exception {
        for (int i = 0; i < 12; i++) {
            assertThat(login("spray-" + i + "-" + System.nanoTime() + "@example.test", "Password1", ip).status()).isEqualTo(401);
        }
        assertThat(login("spray-next@example.test", "Password1", ip).status()).isEqualTo(429);
    }

    @Test
    void spoofedForwardedForFromAnUntrustedClientIsIgnored() throws Exception {
        String email = register();
        // A direct (untrusted) client rotates X-Forwarded-For on every attempt: still one client.
        for (int i = 0; i < 5; i++) login(email, "wrong-password", ip, "192.0.2." + i);
        assertThat(login(email, PASSWORD, ip, "192.0.2.99").status()).isEqualTo(429);
    }

    @Test
    void aTrustedProxyForwardsTheRealClientAddress() throws Exception {
        String email = register();
        String proxy = "10.1.2.3";
        String attacker = "198.51.200." + IPS.getAndIncrement();
        // The left-most value is attacker-controlled and ignored; the hop added by the proxy counts.
        for (int i = 0; i < 6; i++) login(email, "wrong-password", proxy, "6.6.6." + i + ", " + attacker);
        assertThat(login(email, PASSWORD, proxy, attacker).status()).isEqualTo(429);
        // A different real client behind the same proxy is unaffected.
        assertThat(login(email, PASSWORD, proxy, "198.51.201.9").status()).isEqualTo(200);
    }
}
