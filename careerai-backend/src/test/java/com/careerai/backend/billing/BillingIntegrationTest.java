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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** PRO checkout with the fake Razorpay gateway — no network, no real money, test database only. */
class BillingIntegrationTest extends BillingTestSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    private UserSubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    private JsonNode order(TestUser user, Object body) throws Exception {
        Response response = call("POST", "/api/billing/checkout/order", user.token(), body);
        assertThat(response.status()).as(response.raw()).isEqualTo(200);
        return response.body();
    }

    private Response verify(TestUser user, String orderId, String paymentId, String signature) throws Exception {
        return call("POST", "/api/billing/checkout/verify", user.token(),
            Map.of("razorpayOrderId", orderId, "razorpayPaymentId", paymentId, "razorpaySignature", signature));
    }

    /** Creates an order and a captured payment for it; returns the order id. */
    private String paidOrder(TestUser user, String cycle, String paymentId) throws Exception {
        JsonNode order = order(user, Map.of("billingCycle", cycle));
        gateway.payment(paymentId, order.path("razorpayOrderId").asString(), order.path("amount").asLong(), "INR", "captured");
        return order.path("razorpayOrderId").asString();
    }

    @Test
    void plansArePublicAndServerPriced() throws Exception {
        Response plans = call("GET", "/api/billing/plans", null, null);
        assertThat(plans.status()).isEqualTo(200);
        assertThat(plans.body().path("currency").asString()).isEqualTo("INR");
        JsonNode pro = plans.body().path("plans").get(1);
        assertThat(pro.path("plan").asString()).isEqualTo("PRO");
        assertThat(pro.path("pricesPaise").path("MONTHLY").asLong()).isEqualTo(29900);
        assertThat(pro.path("pricesPaise").path("ANNUAL").asLong()).isEqualTo(249900);
        assertThat(pro.path("features").path("AI_VOICE_INTERVIEW").path("limit").asInt()).isEqualTo(20);
        assertThat(plans.raw()).doesNotContain(FakePaymentGateway.KEY_SECRET).doesNotContain(FakePaymentGateway.WEBHOOK_SECRET);
    }

    @Test
    void createMonthlyAndAnnualOrdersWithServerSidePrice() throws Exception {
        TestUser user = register();
        JsonNode monthly = order(user, Map.of("billingCycle", "MONTHLY", "amount", 1, "currency", "USD", "plan", "PRO", "userId", 999, "expiresAt", "2099-01-01T00:00:00Z"));
        assertThat(monthly.path("amount").asLong()).isEqualTo(29900);
        assertThat(monthly.path("currency").asString()).isEqualTo("INR");
        assertThat(monthly.path("keyId").asString()).isEqualTo(FakePaymentGateway.KEY_ID);
        assertThat(monthly.path("razorpayOrderId").asString()).startsWith("order_");
        assertThat(monthly.path("user").path("email").asString()).isEqualTo(user.email());
        assertThat(monthly.path("description").asString()).isEqualTo("CareerAI PRO Monthly");
        assertThat(gateway.createdOrders.getFirst().get("amount")).isEqualTo(29900L); // what Razorpay was asked for
        assertThat(monthly.toString()).doesNotContain(FakePaymentGateway.KEY_SECRET).doesNotContain(FakePaymentGateway.WEBHOOK_SECRET);

        JsonNode annual = order(user, Map.of("billingCycle", "ANNUAL"));
        assertThat(annual.path("amount").asLong()).isEqualTo(249900);
        assertThat(call("POST", "/api/billing/checkout/order", user.token(), Map.of("billingCycle", "WEEKLY")).status()).isEqualTo(400);
        assertThat(call("POST", "/api/billing/checkout/order", null, Map.of("billingCycle", "MONTHLY")).status()).isEqualTo(401);
        // Creating orders never grants anything.
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");
    }

    @Test
    void validPaymentActivatesProForOneCalendarMonth() throws Exception {
        TestUser user = register();
        String orderId = paidOrder(user, "MONTHLY", "pay_valid1");
        Response result = verify(user, orderId, "pay_valid1", FakePaymentGateway.checkoutSignature(orderId, "pay_valid1"));
        assertThat(result.status()).as(result.raw()).isEqualTo(200);
        assertThat(result.body().path("result").asString()).isEqualTo("ACTIVATED");
        assertThat(Instant.parse(result.body().path("accessFrom").asString())).isEqualTo(START);
        assertThat(Instant.parse(result.body().path("accessUntil").asString())).isEqualTo(START.atZone(ZONE).plusMonths(1).toInstant());
        assertThat(result.body().path("subscription").path("plan").asString()).isEqualTo("PRO");
        JsonNode me = me(user);
        assertThat(me.path("plan").asString()).isEqualTo("PRO");
        assertThat(me.path("autoRenew").asBoolean(true)).isFalse();
        assertThat(userRepository.findById(user.id()).orElseThrow().getPlan()).isEqualTo(Plan.PRO);
    }

    @Test
    void duplicateVerificationIsIdempotent() throws Exception {
        TestUser user = register();
        String orderId = paidOrder(user, "MONTHLY", "pay_dup1");
        String signature = FakePaymentGateway.checkoutSignature(orderId, "pay_dup1");
        Response first = verify(user, orderId, "pay_dup1", signature);
        Response second = verify(user, orderId, "pay_dup1", signature);
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body().path("result").asString()).isEqualTo("ALREADY_ACTIVE");
        assertThat(second.body().path("accessUntil").asString()).isEqualTo(first.body().path("accessUntil").asString());
        List<UserSubscription> rows = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.id());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void invalidSignatureWrongOrderAndOtherUsersOrderNeverActivate() throws Exception {
        TestUser user = register();
        String orderId = paidOrder(user, "MONTHLY", "pay_sig1");
        Response forged = verify(user, orderId, "pay_sig1", "0".repeat(64));
        assertThat(forged.status()).isEqualTo(400);
        assertThat(forged.body().path("code").asString()).isEqualTo("PAYMENT_VERIFICATION_FAILED");
        Response signedForOtherOrder = verify(user, orderId, "pay_sig1", FakePaymentGateway.checkoutSignature("order_someoneElse", "pay_sig1"));
        assertThat(signedForOtherOrder.status()).isEqualTo(400);
        Response unknownOrder = verify(user, "order_doesNotExist", "pay_sig1", FakePaymentGateway.checkoutSignature("order_doesNotExist", "pay_sig1"));
        assertThat(unknownOrder.status()).isEqualTo(404);

        TestUser attacker = register();
        Response stolen = verify(attacker, orderId, "pay_sig1", FakePaymentGateway.checkoutSignature(orderId, "pay_sig1"));
        assertThat(stolen.status()).isEqualTo(404); // someone else's order is indistinguishable from a missing one
        assertThat(me(attacker).path("plan").asString()).isEqualTo("FREE");
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");
        assertThat(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.id()).getFirst().getStatus()).isEqualTo(SubscriptionStatus.PENDING);
    }

    @Test
    void amountCurrencyAndOrderMismatchesAreRejected() throws Exception {
        TestUser user = register();
        JsonNode order = order(user, Map.of("billingCycle", "MONTHLY"));
        String orderId = order.path("razorpayOrderId").asString();
        gateway.payment("pay_cheap", orderId, 100, "INR", "captured");
        assertThat(verify(user, orderId, "pay_cheap", FakePaymentGateway.checkoutSignature(orderId, "pay_cheap")).status()).isEqualTo(400);
        gateway.payment("pay_usd", orderId, 29900, "USD", "captured");
        assertThat(verify(user, orderId, "pay_usd", FakePaymentGateway.checkoutSignature(orderId, "pay_usd")).status()).isEqualTo(400);
        gateway.payment("pay_other_order", "order_unrelated", 29900, "INR", "captured");
        assertThat(verify(user, orderId, "pay_other_order", FakePaymentGateway.checkoutSignature(orderId, "pay_other_order")).status()).isEqualTo(400);
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");
    }

    @Test
    void paymentIdCannotActivateTwoPurchases() throws Exception {
        TestUser user = register();
        String first = paidOrder(user, "MONTHLY", "pay_once");
        verify(user, first, "pay_once", FakePaymentGateway.checkoutSignature(first, "pay_once"));
        JsonNode second = order(user, Map.of("billingCycle", "MONTHLY"));
        String secondId = second.path("razorpayOrderId").asString();
        gateway.payment("pay_once", secondId, 29900, "INR", "captured"); // a gateway claiming the same payment paid order 2
        Response reuse = verify(user, secondId, "pay_once", FakePaymentGateway.checkoutSignature(secondId, "pay_once"));
        assertThat(reuse.status()).isEqualTo(409);
        assertThat(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.id()).stream().filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void failedAndPendingPaymentsDoNotActivate() throws Exception {
        TestUser user = register();
        JsonNode order = order(user, Map.of("billingCycle", "MONTHLY"));
        String orderId = order.path("razorpayOrderId").asString();
        gateway.payment("pay_auth", orderId, 29900, "INR", "authorized");
        Response pending = verify(user, orderId, "pay_auth", FakePaymentGateway.checkoutSignature(orderId, "pay_auth"));
        assertThat(pending.status()).isEqualTo(200);
        assertThat(pending.body().path("result").asString()).isEqualTo("PENDING");
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");

        gateway.payment("pay_failed", orderId, 29900, "INR", "failed");
        Response failed = verify(user, orderId, "pay_failed", FakePaymentGateway.checkoutSignature(orderId, "pay_failed"));
        assertThat(failed.status()).isEqualTo(402);
        assertThat(failed.body().path("code").asString()).isEqualTo("PAYMENT_FAILED");
        assertThat(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.id()).getFirst().getStatus()).isEqualTo(SubscriptionStatus.FAILED);
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");
    }

    @Test
    void gatewayFailuresAndMissingConfiguration() throws Exception {
        TestUser user = register();
        gateway.failOrders = true;
        Response down = call("POST", "/api/billing/checkout/order", user.token(), Map.of("billingCycle", "MONTHLY"));
        assertThat(down.status()).isEqualTo(503);
        assertThat(down.body().path("code").asString()).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");
        assertThat(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.id()).getFirst().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);

        gateway.failOrders = false;
        gateway.tamperOrderAmount = 100L; // gateway order not matching the server price
        assertThat(call("POST", "/api/billing/checkout/order", user.token(), Map.of("billingCycle", "MONTHLY")).status()).isEqualTo(503);

        gateway.tamperOrderAmount = null;
        String orderId = paidOrder(user, "MONTHLY", "pay_fetchfail");
        gateway.failFetch = true; // Razorpay unreachable while verifying
        assertThat(verify(user, orderId, "pay_fetchfail", FakePaymentGateway.checkoutSignature(orderId, "pay_fetchfail")).status()).isEqualTo(503);
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");

        gateway.configured = false;
        Response missing = call("POST", "/api/billing/checkout/order", user.token(), Map.of("billingCycle", "MONTHLY"));
        assertThat(missing.status()).isEqualTo(503);
        assertThat(missing.body().path("code").asString()).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");
        assertThat(call("GET", "/api/billing/plans", null, null).body().path("paymentsAvailable").asBoolean(true)).isFalse();
        assertThat(me(user).path("paymentsAvailable").asBoolean(true)).isFalse();
    }

    @Test
    void renewalExtendsFromTheCurrentExpiryAndKeepsPaidTime() throws Exception {
        TestUser user = register();
        String first = paidOrder(user, "MONTHLY", "pay_r1");
        Instant firstEnd = Instant.parse(verify(user, first, "pay_r1", FakePaymentGateway.checkoutSignature(first, "pay_r1")).body().path("accessUntil").asString());
        clock.advance(Duration.ofDays(5)); // renew early, with 25+ days left
        String second = paidOrder(user, "ANNUAL", "pay_r2");
        JsonNode renewed = verify(user, second, "pay_r2", FakePaymentGateway.checkoutSignature(second, "pay_r2")).body();
        assertThat(Instant.parse(renewed.path("accessFrom").asString())).isEqualTo(firstEnd);
        assertThat(Instant.parse(renewed.path("accessUntil").asString())).isEqualTo(firstEnd.atZone(ZONE).plusYears(1).toInstant());
        assertThat(Instant.parse(me(user).path("expiresAt").asString())).isEqualTo(firstEnd.atZone(ZONE).plusYears(1).toInstant());

        clock.advance(Duration.ofDays(30)); // past the first period: still PRO through the queued annual period
        assertThat(me(user).path("plan").asString()).isEqualTo("PRO");
        clock.set(firstEnd.atZone(ZONE).plusYears(1).toInstant()); // annual period over
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");
    }

    @Test
    void historyIsOwnerOnlyAndMasksPaymentIds() throws Exception {
        TestUser user = register();
        String orderId = paidOrder(user, "MONTHLY", "pay_history123456");
        verify(user, orderId, "pay_history123456", FakePaymentGateway.checkoutSignature(orderId, "pay_history123456"));
        JsonNode history = call("GET", "/api/billing/history", user.token(), null).body();
        assertThat(history.size()).isEqualTo(1);
        JsonNode item = history.get(0);
        assertThat(item.path("status").asString()).isEqualTo("ACTIVE");
        assertThat(item.path("amountPaise").asLong()).isEqualTo(29900);
        assertThat(item.path("billingCycle").asString()).isEqualTo("MONTHLY");
        assertThat(item.path("paymentId").asString()).isEqualTo("pay_…3456").doesNotContain("history12");
        assertThat(item.path("accessUntil").asString()).isNotBlank();

        TestUser other = register();
        assertThat(call("GET", "/api/billing/history", other.token(), null).body().size()).isZero();
        assertThat(call("GET", "/api/billing/history", null, null).status()).isEqualTo(401);
    }
}
