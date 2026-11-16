package com.careerai.backend.billing;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory Razorpay stand-in for automated tests: no network, no real money. Signatures use the
 * real HMAC-SHA256 algorithm with test-only secrets, so verification logic is exercised for real.
 */
public class FakePaymentGateway implements PaymentGateway {

    public static final String KEY_ID = "rzp_test_fakeKeyId";
    public static final String KEY_SECRET = "fake_key_secret_for_tests_only";
    public static final String WEBHOOK_SECRET = "fake_webhook_secret_for_tests_only";

    public volatile boolean configured = true;
    public volatile boolean webhookConfigured = true;
    public volatile boolean failOrders = false;
    public volatile boolean failFetch = false;
    /** When set, created orders report this amount instead of the requested one. */
    public volatile Long tamperOrderAmount = null;

    public final List<Map<String, Object>> createdOrders = new CopyOnWriteArrayList<>();
    public final Map<String, GatewayPayment> payments = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    public void reset() {
        configured = true;
        webhookConfigured = true;
        failOrders = false;
        failFetch = false;
        tamperOrderAmount = null;
        createdOrders.clear();
        payments.clear();
    }

    /** Registers a payment as Razorpay would report it from GET /v1/payments/{id}. */
    public void payment(String paymentId, String orderId, long amount, String currency, String status) {
        payments.put(paymentId, new GatewayPayment(paymentId, orderId, amount, currency, status));
    }

    public static String checkoutSignature(String orderId, String paymentId) {
        return RazorpayPaymentGateway.hmacHex(KEY_SECRET, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
    }

    public static String webhookSignature(byte[] rawBody) {
        return RazorpayPaymentGateway.hmacHex(WEBHOOK_SECRET, rawBody);
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public boolean isWebhookConfigured() {
        return webhookConfigured;
    }

    @Override
    public String publicKeyId() {
        return KEY_ID;
    }

    @Override
    public GatewayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        if (!configured || failOrders) throw BillingException.gatewayUnavailable();
        createdOrders.add(Map.of("amount", amountPaise, "currency", currency, "receipt", receipt, "notes", notes));
        String id = "order_TEST" + counter.incrementAndGet() + System.nanoTime() % 100000;
        return new GatewayOrder(id, tamperOrderAmount != null ? tamperOrderAmount : amountPaise, currency, "created");
    }

    @Override
    public GatewayPayment fetchPayment(String paymentId) {
        if (!configured || failFetch) throw BillingException.gatewayUnavailable();
        GatewayPayment payment = payments.get(paymentId);
        if (payment == null) throw BillingException.gatewayUnavailable();
        return payment;
    }

    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        return signature != null && RazorpayPaymentGateway.constantTimeEquals(checkoutSignature(orderId, paymentId), signature);
    }

    @Override
    public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
        return webhookConfigured && signature != null && RazorpayPaymentGateway.constantTimeEquals(webhookSignature(rawBody), signature);
    }
}
