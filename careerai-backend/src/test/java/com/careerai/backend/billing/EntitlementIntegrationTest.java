package com.careerai.backend.billing;

import com.careerai.backend.entity.Plan;
import com.careerai.backend.entity.UserSubscription;
import com.careerai.backend.repository.UserRepository;
import com.careerai.backend.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Plans, usage counting, resets, failure semantics and concurrency — test database only. */
class EntitlementIntegrationTest extends BillingTestSupport {

    @Autowired
    private UsageService usageService;

    @Autowired
    private UserSubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlanCatalog catalog;

    /** Inserts a verified ACTIVE PRO period directly (payment flow is covered by BillingIntegrationTest). */
    private void grantPro(TestUser user, Instant start, Instant end) {
        UserSubscription s = new UserSubscription();
        s.setUser(userRepository.getReferenceById(user.id()));
        s.setPlan(Plan.PRO);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setBillingCycle(BillingCycle.MONTHLY);
        s.setAmountPaise(29900);
        s.setCurrency("INR");
        s.setReceipt("test-" + System.nanoTime());
        s.setRazorpayOrderId("order_grant" + System.nanoTime());
        s.setRazorpayPaymentId("pay_grant" + System.nanoTime());
        s.setStartedAt(start);
        s.setExpiresAt(end);
        s.setCreatedAt(start);
        s.setUpdatedAt(start);
        subscriptionRepository.saveAndFlush(s);
    }

    @Test
    void freeUserSeesBackendLimits() throws Exception {
        TestUser user = register();
        JsonNode me = me(user);
        assertThat(me.path("plan").asString()).isEqualTo("FREE");
        assertThat(me.path("status").asString()).isEqualTo("NONE");
        assertThat(me.path("autoRenew").asBoolean(true)).isFalse();
        JsonNode features = me.path("features");
        assertThat(features.path("AI_TEXT_INTERVIEW").path("limit").asInt()).isEqualTo(5);
        assertThat(features.path("AI_VOICE_INTERVIEW").path("limit").asInt()).isEqualTo(2);
        assertThat(features.path("AI_VOICE_INTERVIEW").path("proLimit").asInt()).isEqualTo(20);
        assertThat(features.path("AI_COACH_MESSAGE").path("limit").asInt()).isEqualTo(20);
        assertThat(features.path("RESUME_ATS_ANALYSIS").path("limit").asInt()).isEqualTo(3);
        assertThat(features.path("RESUME_ATS_ANALYSIS").path("period").asString()).isEqualTo("MONTHLY");
        assertThat(features.path("CODING_RUN").path("limit").asInt()).isEqualTo(20);
        assertThat(features.path("CODING_SUBMIT").path("limit").asInt()).isEqualTo(10);
        assertThat(features.path("SQL_RUN").path("limit").asInt()).isEqualTo(30);
        assertThat(features.path("SQL_SUBMIT").path("limit").asInt()).isEqualTo(15);
        assertThat(me.path("access").path("PERFORMANCE_ANALYTICS").asString()).isEqualTo("BASIC");
        assertThat(me.path("proPricesPaise").path("MONTHLY").asLong()).isEqualTo(29900);
        assertThat(me.path("proPricesPaise").path("ANNUAL").asLong()).isEqualTo(249900);
    }

    @Test
    void proAndExpiredProEntitlements() throws Exception {
        TestUser user = register();
        grantPro(user, START.minus(Duration.ofDays(1)), START.plus(Duration.ofDays(10)));
        JsonNode me = me(user);
        assertThat(me.path("plan").asString()).isEqualTo("PRO");
        assertThat(me.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(me.path("expiresAt").asString()).isNotBlank();
        assertThat(me.path("features").path("AI_VOICE_INTERVIEW").path("limit").asInt()).isEqualTo(20);
        assertThat(me.path("features").path("CODING_RUN").path("limit").asInt()).isEqualTo(100);
        assertThat(me.path("access").path("PERFORMANCE_ANALYTICS").asString()).isEqualTo("FULL");
        assertThat(userRepository.findById(user.id()).orElseThrow().getPlan()).isEqualTo(Plan.PRO); // cache synced

        clock.advance(Duration.ofDays(11)); // expiresAt <= now → FREE, no scheduled job needed
        JsonNode expired = me(user);
        assertThat(expired.path("plan").asString()).isEqualTo("FREE");
        assertThat(expired.path("features").path("AI_VOICE_INTERVIEW").path("limit").asInt()).isEqualTo(2);
        assertThat(userRepository.findById(user.id()).orElseThrow().getPlan()).isEqualTo(Plan.FREE);
    }

    @Test
    void voiceInterviewLimitIsEnforcedServerSideBeforeAnyProviderStarts() throws Exception {
        TestUser user = register();
        assertThat(startInterview(user, "VOICE").status()).isEqualTo(201);
        assertThat(startInterview(user, "VOICE").status()).isEqualTo(201);
        Response third = startInterview(user, "VOICE");
        assertThat(third.status()).isEqualTo(429);
        JsonNode body = third.body();
        assertThat(body.path("code").asString()).isEqualTo("PLAN_LIMIT_REACHED");
        assertThat(body.path("feature").asString()).isEqualTo("AI_VOICE_INTERVIEW");
        assertThat(body.path("plan").asString()).isEqualTo("FREE");
        assertThat(body.path("limit").asInt()).isEqualTo(2);
        assertThat(body.path("used").asInt()).isEqualTo(2);
        assertThat(body.path("period").asString()).isEqualTo("DAILY");
        assertThat(body.path("proLimit").asInt()).isEqualTo(20);
        assertThat(body.path("message").asString()).isEqualTo("You've used today's 2 voice interviews.");
        // No interview session was created for the rejected start (so no Simli/Vapi session can be opened).
        assertThat(call("GET", "/api/interviews/history", user.token(), null).body().size()).isEqualTo(2);
        // Text interviews have their own allowance.
        assertThat(startInterview(user, "TEXT").status()).isEqualTo(201);
        assertThat(used(user, "AI_TEXT_INTERVIEW")).isEqualTo(1);
    }

    @Test
    void proHasHigherLimits() throws Exception {
        TestUser user = register();
        grantPro(user, START.minus(Duration.ofHours(1)), START.plus(Duration.ofDays(30)));
        for (int i = 0; i < 3; i++) assertThat(startInterview(user, "VOICE").status()).isEqualTo(201);
        assertThat(used(user, "AI_VOICE_INTERVIEW")).isEqualTo(3);
    }

    @Test
    void voiceCallThatNeverConnectedIsGivenBack() throws Exception {
        TestUser user = register();
        long id = startInterview(user, "VOICE").body().path("id").asLong();
        assertThat(used(user, "AI_VOICE_INTERVIEW")).isEqualTo(1);
        Response empty = call("POST", "/api/interviews/" + id + "/voice-transcript", user.token(),
            Map.of("provider", "VAPI", "providerCallId", "c1", "transcript", List.of()));
        assertThat(empty.body().path("status").asString()).isEqualTo("CANCELLED");
        assertThat(used(user, "AI_VOICE_INTERVIEW")).isZero();
        // Resubmitting never refunds twice.
        call("POST", "/api/interviews/" + id + "/voice-transcript", user.token(), Map.of("provider", "VAPI", "providerCallId", "c1", "transcript", List.of()));
        assertThat(used(user, "AI_VOICE_INTERVIEW")).isZero();

        long connected = startInterview(user, "VOICE").body().path("id").asLong();
        call("POST", "/api/interviews/" + connected + "/voice-transcript", user.token(), Map.of("provider", "VAPI", "providerCallId", "c2",
            "transcript", List.of(Map.of("role", "assistant", "text", "Hello, welcome to your interview. Tell me about yourself?"))));
        assertThat(used(user, "AI_VOICE_INTERVIEW")).isEqualTo(1); // the call connected: it counts
    }

    @Test
    void sqlUsageCountsExecutedQueriesOnly() throws Exception {
        TestUser user = register();
        assertThat(call("POST", "/api/practice/sql/execute", user.token(), Map.of("questionId", "sql-hr-select", "query", "SELECT name FROM employees")).status()).isEqualTo(200);
        assertThat(used(user, "SQL_RUN")).isEqualTo(1);
        // Rejected before execution: not counted.
        assertThat(call("POST", "/api/practice/sql/execute", user.token(), Map.of("questionId", "sql-hr-select", "query", "DROP TABLE employees")).status()).isEqualTo(400);
        assertThat(used(user, "SQL_RUN")).isEqualTo(1);
        // Executed in the sandbox but returned a SQL error: counted.
        assertThat(call("POST", "/api/practice/sql/execute", user.token(), Map.of("questionId", "sql-hr-select", "query", "SELECT nope FROM employees")).status()).isEqualTo(422);
        assertThat(used(user, "SQL_RUN")).isEqualTo(2);
        assertThat(call("POST", "/api/practice/sql/submit", user.token(), Map.of("questionId", "sql-hr-select", "query", "SELECT name, hire_date FROM employees")).status()).isEqualTo(200);
        assertThat(used(user, "SQL_SUBMIT")).isEqualTo(1);
    }

    @Test
    void providerFailuresDoNotConsume() throws Exception {
        TestUser user = register();
        assertThat(call("POST", "/api/practice/coding/execute", user.token(), Map.of("language", "JAVA", "sourceCode", "x", "stdin", "")).status()).isEqualTo(200);
        assertThat(used(user, "CODING_RUN")).isEqualTo(1);
        codeProvider.timeout = true; // provider timeout (e.g. JDoodle not answering)
        assertThat(call("POST", "/api/practice/coding/execute", user.token(), Map.of("language", "JAVA", "sourceCode", "x", "stdin", "")).status()).isEqualTo(504);
        assertThat(call("POST", "/api/practice/coding/submit", user.token(), Map.of("questionId", "coding-dp-climbing-stairs", "language", "JAVA", "sourceCode", "x")).status()).isEqualTo(504);
        codeProvider.timeout = false;
        codeProvider.configured = false; // provider unavailable
        assertThat(call("POST", "/api/practice/coding/execute", user.token(), Map.of("language", "JAVA", "sourceCode", "x", "stdin", "")).status()).isEqualTo(503);
        assertThat(used(user, "CODING_RUN")).isEqualTo(1);
        assertThat(used(user, "CODING_SUBMIT")).isZero();
        // Invalid request: not counted.
        codeProvider.configured = true;
        assertThat(call("POST", "/api/practice/coding/execute", user.token(), Map.of("language", "RUBY", "sourceCode", "x", "stdin", "")).status()).isEqualTo(400);
        assertThat(used(user, "CODING_RUN")).isEqualTo(1);
    }

    @Test
    void aiCoachFallbackReplyCounts() throws Exception {
        TestUser user = register();
        long conversation = call("POST", "/api/coach/conversations", user.token(), Map.of("title", "Plan")).body().path("id").asLong();
        Response reply = call("POST", "/api/coach/conversations/" + conversation + "/messages", user.token(), Map.of("message", "What should I study today?"));
        assertThat(reply.status()).isEqualTo(200); // AI not configured in tests → fallback reply, still a completed answer
        assertThat(used(user, "AI_COACH_MESSAGE")).isEqualTo(1);
        // Another user's conversation: rejected, not counted.
        TestUser other = register();
        assertThat(call("POST", "/api/coach/conversations/" + conversation + "/messages", other.token(), Map.of("message", "hi")).status()).isEqualTo(404);
        assertThat(used(other, "AI_COACH_MESSAGE")).isZero();
    }

    @Test
    void dailyUsageResetsAtTheNextDay() throws Exception {
        TestUser user = register();
        startInterview(user, "VOICE");
        startInterview(user, "VOICE");
        assertThat(startInterview(user, "VOICE").status()).isEqualTo(429);
        clock.advance(Duration.ofHours(14)); // 01:30 the next day in Asia/Kolkata
        assertThat(used(user, "AI_VOICE_INTERVIEW")).isZero();
        assertThat(startInterview(user, "VOICE").status()).isEqualTo(201);
    }

    @Test
    void monthlyUsageResetsAtTheNextMonth() throws Exception {
        TestUser user = register();
        int limit = catalog.definition(Plan.FREE).limitFor(FeatureKey.RESUME_ATS_ANALYSIS).limit();
        for (int i = 0; i < limit; i++) assertThat(usageService.tryReserve(user.id(), FeatureKey.RESUME_ATS_ANALYSIS, UsagePeriod.MONTHLY, limit)).isNotNull();
        assertThat(usageService.tryReserve(user.id(), FeatureKey.RESUME_ATS_ANALYSIS, UsagePeriod.MONTHLY, limit)).isNull();
        assertThat(used(user, "RESUME_ATS_ANALYSIS")).isEqualTo(limit);
        clock.advance(Duration.ofDays(15)); // still November
        assertThat(used(user, "RESUME_ATS_ANALYSIS")).isEqualTo(limit);
        clock.advance(Duration.ofDays(10)); // December
        assertThat(used(user, "RESUME_ATS_ANALYSIS")).isZero();
        assertThat(usageService.tryReserve(user.id(), FeatureKey.RESUME_ATS_ANALYSIS, UsagePeriod.MONTHLY, limit)).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void concurrentRequestsCannotExceedTheQuota() throws Exception {
        TestUser user = register();
        int limit = 5;
        int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<LocalDate>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return usageService.tryReserve(user.id(), FeatureKey.CODING_RUN, UsagePeriod.DAILY, limit);
            }));
        }
        go.countDown();
        int granted = 0;
        for (Future<LocalDate> result : results) if (result.get(30, TimeUnit.SECONDS) != null) granted++;
        pool.shutdown();
        assertThat(granted).isEqualTo(limit);
        assertThat(usageService.used(user.id(), FeatureKey.CODING_RUN, UsagePeriod.DAILY)).isEqualTo(limit);
    }

    @Test
    void releaseGivesBackExactlyOneUseAndNeverGoesNegative() {
        TestUser user;
        try { user = register(); } catch (Exception ex) { throw new IllegalStateException(ex); }
        LocalDate start = usageService.tryReserve(user.id(), FeatureKey.SQL_RUN, UsagePeriod.DAILY, 30);
        usageService.release(user.id(), FeatureKey.SQL_RUN, UsagePeriod.DAILY, start);
        usageService.release(user.id(), FeatureKey.SQL_RUN, UsagePeriod.DAILY, start);
        assertThat(usageService.used(user.id(), FeatureKey.SQL_RUN, UsagePeriod.DAILY)).isZero();
    }
}
