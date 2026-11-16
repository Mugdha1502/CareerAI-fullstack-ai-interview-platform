package com.careerai.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Billing / entitlement request and response bodies. Requests never carry an amount, plan,
 * user id, email or expiry: the server decides all of them (unknown JSON fields are ignored).
 */
public final class BillingDtos {

    private BillingDtos() {
    }

    /** A metered allowance as shown on the pricing page. */
    public record FeatureLimit(String label, int limit, String period) {
    }

    public record PlanInfo(String plan, Map<String, Long> pricesPaise, Map<String, FeatureLimit> features, Map<String, String> access) {
    }

    /** GET /api/billing/plans — public plan catalogue (no secrets). */
    public record PlansResponse(String currency, boolean paymentsAvailable, List<PlanInfo> plans) {
    }

    /** One metered feature for the current user and period. proLimit: the PRO allowance (for upgrade prompts). */
    public record FeatureUsage(String label, int limit, int used, int remaining, String period, Integer proLimit) {
    }

    /**
     * GET /api/billing/me — the authoritative plan and usage. status: ACTIVE (PRO period covers now)
     * or NONE (FREE). expiresAt is the end of the current PRO access including queued renewals.
     * autoRenew is always false: purchases are paid access periods, not recurring subscriptions.
     */
    public record SubscriptionSummary(
        String plan,
        String status,
        Instant startedAt,
        Instant expiresAt,
        boolean autoRenew,
        String billingCycle,
        Map<String, FeatureUsage> features,
        Map<String, String> access,
        Map<String, Long> proPricesPaise,
        String currency,
        boolean paymentsAvailable
    ) {
    }

    /** POST /api/billing/checkout/order — only the billing cycle; the price is decided server-side. */
    public record CheckoutOrderRequest(
        @NotBlank(message = "Billing cycle is required")
        @Pattern(regexp = "^(MONTHLY|ANNUAL)$", message = "Billing cycle must be MONTHLY or ANNUAL")
        String billingCycle
    ) {
    }

    public record CheckoutUser(String name, String email) {
    }

    /** Browser-safe Checkout details: the PUBLIC key id only, never the key secret. */
    public record CheckoutOrderResponse(
        Long internalSubscriptionId,
        String razorpayOrderId,
        String keyId,
        long amount,
        String currency,
        String plan,
        String billingCycle,
        String description,
        CheckoutUser user
    ) {
    }

    /** POST /api/billing/checkout/verify — the values Razorpay Checkout returns to the handler. */
    public record PaymentVerifyRequest(
        @NotBlank(message = "Order id is required")
        @Size(max = 64, message = "Order id is invalid")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Order id is invalid")
        String razorpayOrderId,

        @NotBlank(message = "Payment id is required")
        @Size(max = 64, message = "Payment id is invalid")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "Payment id is invalid")
        String razorpayPaymentId,

        @NotBlank(message = "Signature is required")
        @Size(max = 128, message = "Signature is invalid")
        String razorpaySignature
    ) {
    }

    /** result: ACTIVATED, ALREADY_ACTIVE or PENDING (captured payment not confirmed yet; the webhook will finish it). */
    public record PaymentVerifyResponse(String result, String message, Instant accessFrom, Instant accessUntil, SubscriptionSummary subscription) {
    }

    /** One purchase in GET /api/billing/history (payment id partially masked). */
    public record HistoryItem(
        Long id,
        Instant date,
        String plan,
        String billingCycle,
        long amountPaise,
        String currency,
        String status,
        String paymentId,
        Instant accessFrom,
        Instant accessUntil
    ) {
    }
}
