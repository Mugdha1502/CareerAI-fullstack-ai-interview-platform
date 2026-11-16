package com.careerai.backend.controller;

import com.careerai.backend.billing.BillingService;
import com.careerai.backend.dto.BillingDtos;
import com.careerai.backend.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Plans, entitlements and PRO checkout.
 *   GET  /api/billing/plans                public plan catalogue (limits + prices, no secrets)
 *   GET  /api/billing/me                   JWT: effective plan, access period, usage per feature
 *   POST /api/billing/checkout/order       JWT: create a Razorpay order for MONTHLY / ANNUAL
 *   POST /api/billing/checkout/verify      JWT: verify the Checkout payment and activate PRO once
 *   GET  /api/billing/history              JWT: the caller's own purchases
 *   POST /api/billing/razorpay/webhook     no JWT: authenticated by X-Razorpay-Signature over the raw body
 * The user always comes from the JWT; nothing in a request body identifies or upgrades a user.
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final CurrentUserService currentUserService;

    @GetMapping("/plans")
    public ResponseEntity<BillingDtos.PlansResponse> plans() {
        return ResponseEntity.ok(billingService.plans());
    }

    @GetMapping("/me")
    public ResponseEntity<BillingDtos.SubscriptionSummary> me(Authentication authentication) {
        return ResponseEntity.ok(billingService.summary(currentUserService.require(authentication.getName())));
    }

    @PostMapping("/checkout/order")
    public ResponseEntity<BillingDtos.CheckoutOrderResponse> order(Authentication authentication, @Valid @RequestBody BillingDtos.CheckoutOrderRequest request) {
        return ResponseEntity.ok(billingService.createOrder(currentUserService.require(authentication.getName()), request));
    }

    @PostMapping("/checkout/verify")
    public ResponseEntity<BillingDtos.PaymentVerifyResponse> verify(Authentication authentication, @Valid @RequestBody BillingDtos.PaymentVerifyRequest request) {
        return ResponseEntity.ok(billingService.verify(currentUserService.require(authentication.getName()), request));
    }

    @GetMapping("/history")
    public ResponseEntity<List<BillingDtos.HistoryItem>> history(Authentication authentication) {
        return ResponseEntity.ok(billingService.history(currentUserService.require(authentication.getName())));
    }

    /** Raw bytes on purpose: the HMAC is computed over the exact body Razorpay sent. */
    @PostMapping("/razorpay/webhook")
    public ResponseEntity<Map<String, String>> webhook(
        @RequestBody byte[] rawBody,
        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
        @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId
    ) {
        return ResponseEntity.ok(Map.of("status", billingService.handleWebhook(rawBody, signature, eventId)));
    }
}
