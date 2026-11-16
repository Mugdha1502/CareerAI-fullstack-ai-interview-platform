package com.careerai.backend.billing;

import java.util.Map;

/**
 * Payment provider boundary (Razorpay in production, a fake in tests). Business rules — prices,
 * ownership, activation, idempotency — live in BillingService, never here.
 */
public interface PaymentGateway {

    /** A created provider order. amount is in the smallest currency unit (paise). */
    record GatewayOrder(String id, long amount, String currency, String status) {
    }

    /** A provider payment as fetched server-side. status: created/authorized/captured/refunded/failed. */
    record GatewayPayment(String id, String orderId, long amount, String currency, String status) {
    }

    /** Key id + key secret present (checkout possible). */
    boolean isConfigured();

    boolean isWebhookConfigured();

    /** The PUBLIC key id for Razorpay Checkout (never the secret). */
    String publicKeyId();

    GatewayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes);

    GatewayPayment fetchPayment(String paymentId);

    /** Checkout signature: HMAC-SHA256(orderId + "|" + paymentId, key secret), constant-time compare. */
    boolean verifyPaymentSignature(String orderId, String paymentId, String signature);

    /** Webhook signature: HMAC-SHA256(raw request body bytes, webhook secret), constant-time compare. */
    boolean verifyWebhookSignature(byte[] rawBody, String signature);
}
