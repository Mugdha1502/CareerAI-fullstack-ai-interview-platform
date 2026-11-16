package com.careerai.backend.billing;

import com.careerai.backend.repository.PaymentWebhookEventRepository;
import com.careerai.backend.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Razorpay webhooks: raw-body HMAC, de-duplication, out-of-order events — fake gateway only. */
class WebhookIntegrationTest extends BillingTestSupport {

    @Autowired
    private UserSubscriptionRepository subscriptionRepository;

    @Autowired
    private PaymentWebhookEventRepository eventRepository;

    private record Hook(int status, String body) {
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

    /** Payment event body in Razorpay's documented shape (deliberately with irregular whitespace). */
    private static String paymentEvent(String event, String paymentId, String orderId, long amount, String status) {
        return "{\n  \"entity\":\"event\",  \"account_id\":\"acc_test\",\"event\":\"" + event + "\",\"contains\":[\"payment\"],\n"
            + "  \"payload\":{\"payment\":{\"entity\":{\"id\":\"" + paymentId + "\",\"order_id\":\"" + orderId + "\",\"amount\":" + amount
            + ",\"currency\":\"INR\",\"status\":\"" + status + "\"}}},\"created_at\":1793000000}";
    }

    private static String orderPaidEvent(String paymentId, String orderId, long amount) {
        return "{\"entity\":\"event\",\"event\":\"order.paid\",\"contains\":[\"payment\",\"order\"],\"payload\":{"
            + "\"payment\":{\"entity\":{\"id\":\"" + paymentId + "\",\"order_id\":\"" + orderId + "\",\"amount\":" + amount + ",\"currency\":\"INR\",\"status\":\"captured\"}},"
            + "\"order\":{\"entity\":{\"id\":\"" + orderId + "\",\"amount\":" + amount + ",\"amount_paid\":" + amount + ",\"currency\":\"INR\",\"status\":\"paid\"}}}}";
    }

    private String newOrder(TestUser user) throws Exception {
        return call("POST", "/api/billing/checkout/order", user.token(), Map.of("billingCycle", "MONTHLY")).body().path("razorpayOrderId").asString();
    }

    @Test
    void capturedPaymentActivatesWhenTheBrowserNeverCalledBack() throws Exception {
        TestUser user = register();
        String orderId = newOrder(user);
        Hook hook = signed(paymentEvent("payment.captured", "pay_wh1", orderId, 29900, "captured"), "evt_1_" + orderId);
        assertThat(hook.status()).isEqualTo(200);
        assertThat(hook.body()).contains("ACTIVATED");
        assertThat(me(user).path("plan").asString()).isEqualTo("PRO");
        assertThat(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.id()).getFirst().getActivatedVia()).isEqualTo("WEBHOOK");
    }

    @Test
    void duplicateEventsDoNotExtendTwice() throws Exception {
        TestUser user = register();
        String orderId = newOrder(user);
        String body = paymentEvent("payment.captured", "pay_wh2", orderId, 29900, "captured");
        signed(body, "evt_dup_" + orderId);
        String expiry = me(user).path("expiresAt").asString();
        Hook again = signed(body, "evt_dup_" + orderId);
        assertThat(again.status()).isEqualTo(200);
        assertThat(again.body()).contains("DUPLICATE");
        // A different event for the same payment (e.g. order.paid after payment.captured) is a no-op too.
        assertThat(signed(orderPaidEvent("pay_wh2", orderId, 29900), "evt_orderpaid_" + orderId).body()).contains("ALREADY_ACTIVE");
        assertThat(me(user).path("expiresAt").asString()).isEqualTo(expiry);
        assertThat(eventRepository.findByEventId("evt_dup_" + orderId)).isPresent();
    }

    @Test
    void signatureIsCheckedAgainstTheRawBody() throws Exception {
        TestUser user = register();
        String orderId = newOrder(user);
        String body = paymentEvent("payment.captured", "pay_wh3", orderId, 29900, "captured");
        String signature = FakePaymentGateway.webhookSignature(body.getBytes(StandardCharsets.UTF_8));
        // Same JSON, different whitespace (what re-serialising would produce): signature no longer matches.
        String reformatted = body.replace("\n  ", "").replace(",  ", ",");
        Hook tampered = send(reformatted, signature, "evt_raw_" + orderId);
        assertThat(tampered.status()).isEqualTo(400);
        assertThat(tampered.body()).contains("WEBHOOK_SIGNATURE_INVALID");
        assertThat(send(body, "deadbeef", "evt_raw2_" + orderId).status()).isEqualTo(400);
        assertThat(send(body, null, "evt_raw3_" + orderId).status()).isEqualTo(400);
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");
        // The exact bytes Razorpay signed are accepted.
        assertThat(send(body, signature, "evt_raw4_" + orderId).status()).isEqualTo(200);
        assertThat(me(user).path("plan").asString()).isEqualTo("PRO");
    }

    @Test
    void orderPaidActivatesAndFailedMarksFailed() throws Exception {
        TestUser user = register();
        String paidOrder = newOrder(user);
        assertThat(signed(orderPaidEvent("pay_wh4", paidOrder, 29900), "evt_op_" + paidOrder).body()).contains("ACTIVATED");

        TestUser other = register();
        String failedOrder = newOrder(other);
        assertThat(signed(paymentEvent("payment.failed", "pay_wh5", failedOrder, 29900, "failed"), "evt_f_" + failedOrder).body()).contains("MARKED_FAILED");
        assertThat(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(other.id()).getFirst().getStatus()).isEqualTo(SubscriptionStatus.FAILED);
        assertThat(me(other).path("plan").asString()).isEqualTo("FREE");
    }

    @Test
    void outOfOrderEventsOnlyMoveForward() throws Exception {
        // captured, then a late "failed" event: stays ACTIVE.
        TestUser user = register();
        String orderId = newOrder(user);
        signed(paymentEvent("payment.captured", "pay_wh6", orderId, 29900, "captured"), "evt_c_" + orderId);
        assertThat(signed(paymentEvent("payment.failed", "pay_wh6x", orderId, 29900, "failed"), "evt_lf_" + orderId).body()).contains("IGNORED");
        assertThat(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.id()).getFirst().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(me(user).path("plan").asString()).isEqualTo("PRO");

        // failed first, then a later successful capture for the same order: FAILED → ACTIVE.
        TestUser second = register();
        String retried = newOrder(second);
        signed(paymentEvent("payment.failed", "pay_wh7a", retried, 29900, "failed"), "evt_f2_" + retried);
        assertThat(signed(paymentEvent("payment.captured", "pay_wh7b", retried, 29900, "captured"), "evt_c2_" + retried).body()).contains("ACTIVATED");
        assertThat(me(second).path("plan").asString()).isEqualTo("PRO");
    }

    @Test
    void webhookAfterVerifyDoesNotExtendAgain() throws Exception {
        TestUser user = register();
        String orderId = newOrder(user);
        gateway.payment("pay_wh8", orderId, 29900, "INR", "captured");
        call("POST", "/api/billing/checkout/verify", user.token(), Map.of("razorpayOrderId", orderId, "razorpayPaymentId", "pay_wh8",
            "razorpaySignature", FakePaymentGateway.checkoutSignature(orderId, "pay_wh8")));
        String expiry = me(user).path("expiresAt").asString();
        assertThat(signed(paymentEvent("payment.captured", "pay_wh8", orderId, 29900, "captured"), "evt_after_" + orderId).body()).contains("ALREADY_ACTIVE");
        assertThat(me(user).path("expiresAt").asString()).isEqualTo(expiry);
    }

    @Test
    void wrongAmountUnknownOrderAndUnknownEventsAreIgnoredSafely() throws Exception {
        TestUser user = register();
        String orderId = newOrder(user);
        assertThat(signed(paymentEvent("payment.captured", "pay_wh9", orderId, 100, "captured"), "evt_amt_" + orderId).body()).contains("AMOUNT_MISMATCH");
        assertThat(me(user).path("plan").asString()).isEqualTo("FREE");
        assertThat(signed(paymentEvent("payment.captured", "pay_wh10", "order_notOurs", 29900, "captured"), "evt_unknown_order").body()).contains("UNKNOWN_ORDER");
        Hook unknownType = signed("{\"entity\":\"event\",\"event\":\"refund.processed\",\"payload\":{}}", "evt_refund_1");
        assertThat(unknownType.status()).isEqualTo(200);
        assertThat(unknownType.body()).contains("IGNORED");
    }

    @Test
    void missingWebhookSecretRejectsEverything() throws Exception {
        gateway.webhookConfigured = false;
        Hook hook = signed(paymentEvent("payment.captured", "pay_x", "order_x", 29900, "captured"), "evt_noconf");
        assertThat(hook.status()).isEqualTo(503);
    }

    @Test
    void webhookNeedsNoJwtButJwtUsersCannotFakeIt() throws Exception {
        TestUser user = register();
        String orderId = newOrder(user);
        var request = post("/api/billing/razorpay/webhook").contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + user.token())
            .content(paymentEvent("payment.captured", "pay_self", orderId, 29900, "captured"));
        assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(400); // a JWT is no substitute for the signature
        JsonNode me = me(user);
        assertThat(me.path("plan").asString()).isEqualTo("FREE");
    }
}
