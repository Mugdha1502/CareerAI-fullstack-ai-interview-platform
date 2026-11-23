package com.careerai.backend.billing;

import com.careerai.backend.entity.Plan;
import com.careerai.backend.entity.User;
import com.careerai.backend.entity.UserSubscription;
import com.careerai.backend.repository.PaymentWebhookEventRepository;
import com.careerai.backend.repository.UserRepository;
import com.careerai.backend.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Subscription expiry and webhook-security regression (in-memory test database, fake gateway only):
 * expired PRO falls back to FREE on every check, data survives expiry, a stale users.plan or JWT
 * plan claim never grants PRO, repurchase after expiry starts a new period, and webhooks stay
 * idempotent, signature-checked and order-independent.
 */
class SubscriptionExpiryRegressionTest extends BillingTestSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final long MONTHLY = 29900;

    @Autowired
    private UserSubscriptionRepository subscriptionRepository;

    @Autowired
    private PaymentWebhookEventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionActivationService activation;

    private record Hook(int status, String body) {
    }

    /* ---------- helpers ---------- */

    private String newOrder(TestUser user, String cycle) throws Exception {
        return call("POST", "/api/billing/checkout/order", user.token(), Map.of("billingCycle", cycle)).body().path("razorpayOrderId").asString();
    }

    /** Checkout callback path: order → captured payment at the fake gateway → signed verify. */
    private JsonNode buy(TestUser user, String cycle, String paymentId) throws Exception {
        String orderId = newOrder(user, cycle);
        gateway.payment(paymentId, orderId, cycle.equals("ANNUAL") ? 249900 : MONTHLY, "INR", "captured");
        Response verify = call("POST", "/api/billing/checkout/verify", user.token(), Map.of("razorpayOrderId", orderId,
            "razorpayPaymentId", paymentId, "razorpaySignature", FakePaymentGateway.checkoutSignature(orderId, paymentId)));
        assertThat(verify.status()).isEqualTo(200);
        return verify.body();
    }

    private Hook send(String rawBody, String signature, String eventId) throws Exception {
        var request = post("/api/billing/razorpay/webhook").contentType(MediaType.APPLICATION_JSON).content(rawBody.getBytes(StandardCharsets.UTF_8));
        if (signature != null) request.header("X-Razorpay-Signature", signature);
        if (eventId != null) request.header("X-Razorpay-Event-Id", eventId);
        MvcResult result = mvc.perform(request).andReturn();
        return new Hook(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private Hook signed(String rawBody, String eventId) throws Exception {
        return send(rawBody, FakePaymentGateway.webhookSignature(rawBody.getBytes(StandardCharsets.UTF_8)), eventId);
    }

    private static String captured(String paymentId, String orderId, long amount) {
        return "{\"entity\":\"event\",\"event\":\"payment.captured\",\"contains\":[\"payment\"],\"payload\":{\"payment\":{\"entity\":{\"id\":\""
            + paymentId + "\",\"order_id\":\"" + orderId + "\",\"amount\":" + amount + ",\"currency\":\"INR\",\"status\":\"captured\"}}}}";
    }

    private static String failed(String paymentId, String orderId) {
        return "{\"entity\":\"event\",\"event\":\"payment.failed\",\"contains\":[\"payment\"],\"payload\":{\"payment\":{\"entity\":{\"id\":\""
            + paymentId + "\",\"order_id\":\"" + orderId + "\",\"amount\":" + MONTHLY + ",\"currency\":\"INR\",\"status\":\"failed\"}}}}";
    }

    private static String orderPaid(String paymentId, String orderId, long amount) {
        return "{\"entity\":\"event\",\"event\":\"order.paid\",\"contains\":[\"payment\",\"order\"],\"payload\":{"
            + "\"payment\":{\"entity\":{\"id\":\"" + paymentId + "\",\"order_id\":\"" + orderId + "\",\"amount\":" + amount + ",\"currency\":\"INR\",\"status\":\"captured\"}},"
            + "\"order\":{\"entity\":{\"id\":\"" + orderId + "\",\"amount\":" + amount + ",\"amount_paid\":" + amount + ",\"currency\":\"INR\",\"status\":\"paid\"}}}}";
    }

    private List<UserSubscription> purchases(TestUser user) {
        return subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.id());
    }

    private UserSubscription purchase(String orderId) {
        return subscriptionRepository.findAll().stream().filter(s -> orderId.equals(s.getRazorpayOrderId())).findFirst().orElseThrow();
    }

    private static Instant plusMonths(Instant from, int months) {
        return from.atZone(ZONE).plusMonths(months).toInstant();
    }

    private static Instant istStartOfDay(Instant instant) {
        LocalDate day = instant.atZone(ZONE).toLocalDate();
        return day.atStartOfDay(ZONE).toInstant();
    }

    private void setStoredPlan(TestUser user, Plan plan) {
        User entity = userRepository.findById(user.id()).orElseThrow();
        entity.setPlan(plan);
        userRepository.saveAndFlush(entity);
    }

    private String login(TestUser user) throws Exception {
        return call("POST", "/api/auth/login", null, Map.of("email", user.email(), "password", "secret123")).body().path("token").asString();
    }

    /* ---------- 2 + 3: expiry on every check, FREE limits restored ---------- */

    @Test
    void expiredProFallsBackToFreeOnTheNextEnforcementCheckWithoutTheJob() throws Exception {
        TestUser user = register();
        Instant end = Instant.parse(buy(user, "MONTHLY", "pay_exp1").path("accessUntil").asString());
        assertThat(end).isEqualTo(plusMonths(START, 1));

        // Last minutes of the paid period: PRO limits apply (3 voice interviews > FREE's 2).
        clock.set(end.minus(Duration.ofMinutes(10)));
        for (int i = 0; i < 3; i++) assertThat(startInterview(user, "VOICE").status()).isEqualTo(201);
        clock.set(end.minusSeconds(1));
        assertThat(me(user).path("plan").asString()).isEqualTo("PRO");

        // expiresAt is exclusive: at that instant the very next check is FREE. The housekeeping job
        // has NOT run — the row is still ACTIVE — and enforcement already uses FREE limits.
        clock.set(end);
        Response blocked = startInterview(user, "VOICE");
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.body().path("code").asString()).isEqualTo("PLAN_LIMIT_REACHED");
        assertThat(blocked.body().path("plan").asString()).isEqualTo("FREE");
        assertThat(blocked.body().path("limit").asInt()).isEqualTo(2);
        assertThat(blocked.body().path("proLimit").asInt()).isEqualTo(20);
        assertThat(purchases(user).getFirst().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        JsonNode me = me(user);
        assertThat(me.path("plan").asString()).isEqualTo("FREE");
        assertThat(me.path("status").asString()).isEqualTo("NONE");
        assertThat(me.path("expiresAt").isNull()).isTrue();
        assertThat(me.path("features").path("AI_VOICE_INTERVIEW").path("remaining").asInt()).isZero();
        assertThat(me.path("access").path("PERFORMANCE_ANALYTICS").asString()).isEqualTo("BASIC");
        assertThat(history(user)).isEqualTo(3); // the rejected start created no session
    }

    @Test
    void freeLimitsAreRestoredAfterExpiry() throws Exception {
        TestUser user = register();
        Instant end = Instant.parse(buy(user, "MONTHLY", "pay_exp2").path("accessUntil").asString());
        clock.set(end.plus(Duration.ofDays(1)));
        activation.expireEndedPeriods(); // with the job as well
        assertThat(purchases(user).getFirst().getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);

        JsonNode features = me(user).path("features");
        assertThat(features.path("AI_TEXT_INTERVIEW").path("limit").asInt()).isEqualTo(5);
        assertThat(features.path("AI_VOICE_INTERVIEW").path("limit").asInt()).isEqualTo(2);
        assertThat(features.path("AI_COACH_MESSAGE").path("limit").asInt()).isEqualTo(20);
        assertThat(features.path("RESUME_ATS_ANALYSIS").path("limit").asInt()).isEqualTo(3);
        assertThat(features.path("CODING_RUN").path("limit").asInt()).isEqualTo(20);
        assertThat(features.path("SQL_SUBMIT").path("limit").asInt()).isEqualTo(15);
        assertThat(features.path("AI_VOICE_INTERVIEW").path("proLimit").asInt()).isEqualTo(20);

        // FREE quota is enforced exactly: 2 allowed, the 3rd blocked, and it resets the next IST day.
        assertThat(startInterview(user, "VOICE").status()).isEqualTo(201);
        assertThat(startInterview(user, "VOICE").status()).isEqualTo(201);
        assertThat(startInterview(user, "VOICE").status()).isEqualTo(429);
        clock.set(istStartOfDay(Instant.now(clock)).plus(Duration.ofDays(1)).plusSeconds(60));
        assertThat(used(user, "AI_VOICE_INTERVIEW")).isZero();
        assertThat(startInterview(user, "VOICE").status()).isEqualTo(201);
    }

    private int history(TestUser user) throws Exception {
        return call("GET", "/api/interviews/history", user.token(), null).body().size();
    }

    /* ---------- 4: user data and billing records survive expiry ---------- */

    @Test
    void interviewResumePracticeAndBillingRecordsSurviveExpiry() throws Exception {
        TestUser user = register();
        JsonNode bought = buy(user, "MONTHLY", "pay_keep1");
        Instant end = Instant.parse(bought.path("accessUntil").asString());

        long interviewId = startInterview(user, "TEXT").body().path("id").asLong();
        assertThat(interviewId).isPositive();
        Response resume = call("POST", "/api/resumes", user.token(), Map.of("title", "Expiry Resume",
            "content", Map.of("summary", "Java developer", "skills", List.of("Java", "Spring Boot"))));
        assertThat(resume.status()).isEqualTo(201);
        long resumeId = resume.body().path("id").asLong();
        assertThat(call("POST", "/api/practice/sql/submit", user.token(),
            Map.of("questionId", "sql-hr-select", "query", "SELECT name, hire_date FROM employees")).status()).isEqualTo(200);

        JsonNode billingBefore = call("GET", "/api/billing/history", user.token(), null).body();
        int practiceBefore = call("GET", "/api/practice/history", user.token(), null).body().path("totalItems").asInt();
        assertThat(practiceBefore).isEqualTo(1);

        clock.set(end.plus(Duration.ofDays(3)));
        activation.expireEndedPeriods();
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");

        assertThat(history(user)).isEqualTo(1);
        assertThat(call("GET", "/api/interviews/" + interviewId, user.token(), null).status()).isEqualTo(200);
        assertThat(call("GET", "/api/resumes", user.token(), null).body().size()).isEqualTo(1);
        assertThat(call("GET", "/api/resumes/" + resumeId, user.token(), null).body().path("title").asString()).isEqualTo("Expiry Resume");
        assertThat(call("GET", "/api/practice/history", user.token(), null).body().path("totalItems").asInt()).isEqualTo(practiceBefore);

        // The purchase is kept, only its status moves to EXPIRED: amount, payment and period unchanged.
        JsonNode billingAfter = call("GET", "/api/billing/history", user.token(), null).body();
        assertThat(billingAfter.size()).isEqualTo(1);
        JsonNode before = billingBefore.get(0);
        JsonNode after = billingAfter.get(0);
        assertThat(before.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(after.path("status").asString()).isEqualTo("EXPIRED");
        for (String field : List.of("id", "amountPaise", "currency", "paymentId", "billingCycle", "accessFrom", "accessUntil", "date")) {
            assertThat(after.path(field)).as(field).isEqualTo(before.path(field));
        }
    }

    /* ---------- 5: users.plan / JWT plan claim cannot grant PRO ---------- */

    @Test
    void storedPlanProWithoutACoveringSubscriptionIsFree() throws Exception {
        TestUser never = register();
        setStoredPlan(never, Plan.PRO);
        String proClaimToken = login(never); // a JWT issued while users.plan = PRO carries plan=PRO
        TestUser withProClaim = new TestUser(never.id(), proClaimToken, never.email());
        assertThat(startInterview(withProClaim, "VOICE").status()).isEqualTo(201);
        assertThat(startInterview(withProClaim, "VOICE").status()).isEqualTo(201);
        Response third = startInterview(withProClaim, "VOICE");
        assertThat(third.status()).isEqualTo(429);
        assertThat(third.body().path("plan").asString()).isEqualTo("FREE");
        assertThat(me(withProClaim).path("plan").asString()).isEqualTo("FREE");
        assertThat(userRepository.findById(never.id()).orElseThrow().getPlan()).isEqualTo(Plan.FREE); // cache corrected

        // Legacy PREMIUM value is not an entitlement either.
        TestUser legacy = register();
        setStoredPlan(legacy, Plan.PREMIUM);
        assertThat(me(legacy).path("plan").asString()).isEqualTo("FREE");
        assertThat(me(legacy).path("features").path("CODING_RUN").path("limit").asInt()).isEqualTo(20);
    }

    @Test
    void expiredSubscriptionStillMarkedActiveWithStoredPlanProGrantsNothing() throws Exception {
        TestUser user = register();
        Instant end = Instant.parse(buy(user, "MONTHLY", "pay_stale1").path("accessUntil").asString());
        String tokenIssuedWhilePro = login(user);
        assertThat(userRepository.findById(user.id()).orElseThrow().getPlan()).isEqualTo(Plan.PRO);

        clock.set(end.plus(Duration.ofHours(2)));
        // Job not run, cache not synced: row ACTIVE, users.plan PRO, JWT says PRO.
        setStoredPlan(user, Plan.PRO);
        assertThat(purchases(user).getFirst().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        TestUser stale = new TestUser(user.id(), tokenIssuedWhilePro, user.email());
        assertThat(startInterview(stale, "VOICE").status()).isEqualTo(201);
        assertThat(startInterview(stale, "VOICE").status()).isEqualTo(201);
        Response blocked = startInterview(stale, "VOICE");
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.body().path("plan").asString()).isEqualTo("FREE");
        assertThat(me(stale).path("plan").asString()).isEqualTo("FREE");
    }

    /* ---------- 6: repurchase after expiry starts now ---------- */

    @Test
    void repurchaseAfterExpiryStartsANewPeriodFromNow() throws Exception {
        TestUser user = register();
        Instant firstEnd = Instant.parse(buy(user, "MONTHLY", "pay_rb1").path("accessUntil").asString());

        // Expired 5 days ago, housekeeping job not run (row still ACTIVE): must not stack on the old date.
        Instant now = firstEnd.plus(Duration.ofDays(5));
        clock.set(now);
        JsonNode second = buy(user, "MONTHLY", "pay_rb2");
        assertThat(Instant.parse(second.path("accessFrom").asString())).isEqualTo(now);
        assertThat(Instant.parse(second.path("accessUntil").asString())).isEqualTo(plusMonths(now, 1));
        assertThat(Instant.parse(me(user).path("expiresAt").asString())).isEqualTo(plusMonths(now, 1));

        // Expired again, job run (row EXPIRED), repurchase activated by webhook only: also starts now.
        Instant later = plusMonths(now, 1).plus(Duration.ofDays(40));
        clock.set(later);
        activation.expireEndedPeriods();
        String orderId = newOrder(user, "MONTHLY");
        assertThat(signed(captured("pay_rb3", orderId, MONTHLY), "evt_rb3").body()).contains("ACTIVATED");
        UserSubscription third = purchase(orderId);
        assertThat(third.getStartedAt()).isEqualTo(later);
        assertThat(third.getExpiresAt()).isEqualTo(plusMonths(later, 1));
        assertThat(me(user).path("plan").asString()).isEqualTo("PRO");
        assertThat(purchases(user)).hasSize(3);
    }

    /* ---------- 7: duplicate deliveries of one event id ---------- */

    @Test
    void sameEventIdIsAppliedExactlyOnceEvenWhenDeliveredConcurrently() throws Exception {
        TestUser user = register();
        String orderId = newOrder(user, "MONTHLY");
        String body = captured("pay_dupc", orderId, MONTHLY);
        String eventId = "evt_dupc_" + orderId;

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Hook>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return signed(body, eventId);
            }));
        }
        go.countDown();
        int activated = 0;
        int duplicates = 0;
        for (Future<Hook> result : results) {
            Hook hook = result.get(30, TimeUnit.SECONDS);
            assertThat(hook.status()).isEqualTo(200);
            if (hook.body().contains("ACTIVATED")) activated++;
            if (hook.body().contains("DUPLICATE")) duplicates++;
        }
        pool.shutdown();
        assertThat(activated).isEqualTo(1);
        assertThat(duplicates).isEqualTo(threads - 1);

        Instant expiry = Instant.parse(me(user).path("expiresAt").asString());
        assertThat(expiry).isEqualTo(plusMonths(START, 1));
        assertThat(eventRepository.findByEventId(eventId)).isPresent();
        assertThat(purchases(user)).hasSize(1);

        // Redelivered days later — even after the period ended — it is still a duplicate.
        assertThat(signed(body, eventId).body()).contains("DUPLICATE");
        clock.set(expiry.plus(Duration.ofDays(1)));
        assertThat(signed(body, eventId).body()).contains("DUPLICATE");
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");
    }

    /* ---------- 8: forged / invalid signatures ---------- */

    @Test
    void forgedSignaturesAreRejectedAndDoNotConsumeTheEventId() throws Exception {
        TestUser user = register();
        String orderId = newOrder(user, "MONTHLY");
        String body = captured("pay_forge", orderId, MONTHLY);
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        String eventId = "evt_forge_" + orderId;
        String valid = FakePaymentGateway.webhookSignature(raw);

        String wrongSecret = RazorpayPaymentGateway.hmacHex("attacker-guessed-secret", raw);
        String otherBodySignature = FakePaymentGateway.webhookSignature(captured("pay_other", "order_other", MONTHLY).getBytes(StandardCharsets.UTF_8));
        List<String> forged = List.of(wrongSecret, otherBodySignature, "", " ", valid.substring(0, 32), valid + "00",
            "0".repeat(64), valid.replace(valid.charAt(0), valid.charAt(0) == 'a' ? 'b' : 'a'));
        for (String signature : forged) {
            Hook hook = send(body, signature, eventId);
            assertThat(hook.status()).as("signature '%s'", signature.length() > 8 ? signature.substring(0, 8) + "…" : signature).isEqualTo(400);
        }
        assertThat(send(body, null, eventId).status()).isEqualTo(400);

        // Valid signature replayed on a modified body (cheaper amount): rejected.
        assertThat(send(captured("pay_forge", orderId, 100), valid, eventId).status()).isEqualTo(400);
        // A forged "failed" event cannot touch the purchase either.
        assertThat(send(failed("pay_forge", orderId), wrongSecret, "evt_forge_failed").status()).isEqualTo(400);

        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");
        assertThat(purchase(orderId).getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(eventRepository.findByEventId(eventId)).isEmpty(); // forged attempts are not recorded
        assertThat(eventRepository.findByEventId("evt_forge_failed")).isEmpty();

        // The genuine delivery with the same event id is still processed.
        Hook genuine = send(body, valid, eventId);
        assertThat(genuine.status()).isEqualTo(200);
        assertThat(genuine.body()).contains("ACTIVATED");
        assertThat(me(user).path("plan").asString()).isEqualTo("PRO");
    }

    /* ---------- 9: out-of-order events ---------- */

    @Test
    void orderPaidBeforePaymentCapturedActivatesOnce() throws Exception {
        TestUser user = register();
        String orderId = newOrder(user, "MONTHLY");
        assertThat(signed(orderPaid("pay_ooo1", orderId, MONTHLY), "evt_ooo_op").body()).contains("ACTIVATED");
        assertThat(signed(captured("pay_ooo1", orderId, MONTHLY), "evt_ooo_pc").body()).contains("ALREADY_ACTIVE");
        assertThat(Instant.parse(me(user).path("expiresAt").asString())).isEqualTo(plusMonths(START, 1));
        assertThat(eventRepository.findByEventId("evt_ooo_op").orElseThrow().getOutcome()).isEqualTo("ACTIVATED");
        assertThat(eventRepository.findByEventId("evt_ooo_pc").orElseThrow().getOutcome()).isEqualTo("ALREADY_ACTIVE");
    }

    @Test
    void laterPurchaseNotifiedFirstStillGivesTwoContiguousPeriods() throws Exception {
        TestUser user = register();
        String first = newOrder(user, "MONTHLY");
        String second = newOrder(user, "MONTHLY");
        // The second purchase's webhook arrives before the first one's.
        assertThat(signed(captured("pay_ooo_b", second, MONTHLY), "evt_ooo_b").body()).contains("ACTIVATED");
        clock.advance(Duration.ofMinutes(3));
        assertThat(signed(captured("pay_ooo_a", first, MONTHLY), "evt_ooo_a").body()).contains("ACTIVATED");

        UserSubscription b = purchase(second);
        UserSubscription a = purchase(first);
        assertThat(b.getStartedAt()).isEqualTo(START);
        assertThat(b.getExpiresAt()).isEqualTo(plusMonths(START, 1));
        assertThat(a.getStartedAt()).isEqualTo(b.getExpiresAt()); // queued, no overlap and no lost time
        assertThat(a.getExpiresAt()).isEqualTo(plusMonths(START, 2));
        assertThat(Instant.parse(me(user).path("expiresAt").asString())).isEqualTo(plusMonths(START, 2));
    }

    @Test
    void lateFailedEventAfterExpiryChangesNothing() throws Exception {
        TestUser user = register();
        String orderId = newOrder(user, "MONTHLY");
        assertThat(signed(captured("pay_late", orderId, MONTHLY), "evt_late_c").body()).contains("ACTIVATED");
        clock.set(plusMonths(START, 1).plus(Duration.ofDays(2)));
        activation.expireEndedPeriods();
        assertThat(signed(failed("pay_late_x", orderId), "evt_late_f").body()).contains("IGNORED");
        // A late duplicate success for the expired purchase does not re-activate it.
        assertThat(signed(captured("pay_late", orderId, MONTHLY), "evt_late_c2").body()).contains("ALREADY_ACTIVE");
        assertThat(purchase(orderId).getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");
    }

    /* ---------- 10: separate purchases extend, repeated notifications do not ---------- */

    @Test
    void twoPurchasesExtendSeparatelyWhileRepeatsForOnePurchaseNeverExtend() throws Exception {
        TestUser user = register();

        // Purchase A: browser verify + payment.captured + order.paid + redelivery of the same event.
        String a = newOrder(user, "MONTHLY");
        gateway.payment("pay_sep_a", a, MONTHLY, "INR", "captured");
        assertThat(call("POST", "/api/billing/checkout/verify", user.token(), Map.of("razorpayOrderId", a,
            "razorpayPaymentId", "pay_sep_a", "razorpaySignature", FakePaymentGateway.checkoutSignature(a, "pay_sep_a"))).body().path("result").asString())
            .isEqualTo("ACTIVATED");
        assertThat(signed(captured("pay_sep_a", a, MONTHLY), "evt_sep_a1").body()).contains("ALREADY_ACTIVE");
        assertThat(signed(orderPaid("pay_sep_a", a, MONTHLY), "evt_sep_a2").body()).contains("ALREADY_ACTIVE");
        assertThat(signed(captured("pay_sep_a", a, MONTHLY), "evt_sep_a1").body()).contains("DUPLICATE");
        assertThat(Instant.parse(me(user).path("expiresAt").asString())).isEqualTo(plusMonths(START, 1));

        // Purchase B ten days later: webhooks first, then the browser callback.
        clock.advance(Duration.ofDays(10));
        String b = newOrder(user, "MONTHLY");
        gateway.payment("pay_sep_b", b, MONTHLY, "INR", "captured");
        assertThat(signed(captured("pay_sep_b", b, MONTHLY), "evt_sep_b1").body()).contains("ACTIVATED");
        assertThat(signed(orderPaid("pay_sep_b", b, MONTHLY), "evt_sep_b2").body()).contains("ALREADY_ACTIVE");
        assertThat(call("POST", "/api/billing/checkout/verify", user.token(), Map.of("razorpayOrderId", b,
            "razorpayPaymentId", "pay_sep_b", "razorpaySignature", FakePaymentGateway.checkoutSignature(b, "pay_sep_b"))).body().path("result").asString())
            .isEqualTo("ALREADY_ACTIVE");

        // Exactly two months in total, B queued after A (like the live 23 Dec → 23 Jan extension).
        assertThat(purchase(b).getStartedAt()).isEqualTo(purchase(a).getExpiresAt());
        assertThat(Instant.parse(me(user).path("expiresAt").asString())).isEqualTo(plusMonths(START, 2));
        JsonNode history = call("GET", "/api/billing/history", user.token(), null).body();
        assertThat(history.size()).isEqualTo(2);
        assertThat(purchases(user)).allMatch(p -> p.getStatus() == SubscriptionStatus.ACTIVE);
    }
}
