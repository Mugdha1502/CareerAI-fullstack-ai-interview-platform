package com.careerai.backend.billing;

import com.careerai.backend.dto.BillingDtos;
import com.careerai.backend.entity.Plan;
import com.careerai.backend.entity.User;
import com.careerai.backend.entity.UserSubscription;
import com.careerai.backend.practice.PracticeRateLimiter;
import com.careerai.backend.repository.PaymentWebhookEventRepository;
import com.careerai.backend.repository.UserRepository;
import com.careerai.backend.repository.UserSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PRO checkout with Razorpay (Orders API + Checkout) and the plan/usage summary.
 *
 *   order   → server picks the price for the cycle, saves a PENDING purchase, creates the Razorpay
 *             order and returns browser-safe Checkout details (public key id only).
 *   verify  → the caller's own PENDING purchase is looked up by order id; the Checkout signature is
 *             checked against the STORED order id; the payment is fetched server-side and must match
 *             order, amount and currency and be captured; then activation happens exactly once.
 *   webhook → raw-body HMAC check, de-duplication by event id, then the same idempotent activation —
 *             so a closed browser or lost callback is reconciled.
 *
 * Nothing from the browser decides an amount, plan, user, or expiry.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlanCatalog catalog;
    private final EntitlementService entitlementService;
    private final UsageService usageService;
    private final PaymentGateway gateway;
    private final SubscriptionActivationService activation;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final UserRepository userRepository;
    private final PracticeRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public BillingService(PlanCatalog catalog, EntitlementService entitlementService, UsageService usageService, PaymentGateway gateway,
                          SubscriptionActivationService activation, UserSubscriptionRepository subscriptionRepository,
                          PaymentWebhookEventRepository webhookEventRepository, UserRepository userRepository,
                          PracticeRateLimiter rateLimiter, ObjectMapper objectMapper, PlatformTransactionManager transactionManager, Clock billingClock) {
        this.catalog = catalog;
        this.entitlementService = entitlementService;
        this.usageService = usageService;
        this.gateway = gateway;
        this.activation = activation;
        this.subscriptionRepository = subscriptionRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = billingClock;
    }

    /* ---------- plans and summary ---------- */

    public BillingDtos.PlansResponse plans() {
        List<BillingDtos.PlanInfo> plans = new ArrayList<>();
        for (Plan plan : List.of(Plan.FREE, Plan.PRO)) {
            PlanDefinition definition = catalog.definition(plan);
            Map<String, BillingDtos.FeatureLimit> features = new LinkedHashMap<>();
            for (FeatureKey feature : FeatureKey.values()) {
                PlanDefinition.Limit limit = definition.limitFor(feature);
                if (limit != null) features.put(feature.name(), new BillingDtos.FeatureLimit(feature.label(), limit.limit(), limit.period().name()));
            }
            Map<String, Long> prices = new LinkedHashMap<>();
            if (plan == Plan.PRO) catalog.proPrices().forEach((cycle, paise) -> prices.put(cycle.name(), paise));
            plans.add(new BillingDtos.PlanInfo(plan.name(), prices, features, accessMap(definition)));
        }
        return new BillingDtos.PlansResponse(catalog.currency(), gateway.isConfigured(), plans);
    }

    public BillingDtos.SubscriptionSummary summary(User user) {
        Plan plan = entitlementService.currentPlan(user);
        activation.syncPlanCache(user.getId(), plan);
        PlanDefinition definition = catalog.definition(plan);
        PlanDefinition pro = catalog.definition(Plan.PRO);
        Map<FeatureKey, Integer> used = usageService.usedByFeature(user.getId());

        Map<String, BillingDtos.FeatureUsage> features = new LinkedHashMap<>();
        for (FeatureKey feature : FeatureKey.values()) {
            PlanDefinition.Limit limit = definition.limitFor(feature);
            if (limit == null) continue;
            int count = used.getOrDefault(feature, 0);
            features.put(feature.name(), new BillingDtos.FeatureUsage(feature.label(), limit.limit(), count,
                Math.max(0, limit.limit() - count), limit.period().name(), plan == Plan.PRO ? null : pro.limitFor(feature).limit()));
        }

        Instant startedAt = null;
        Instant expiresAt = null;
        String cycle = null;
        if (plan == Plan.PRO) {
            List<UserSubscription> periods = activation.activePeriods(user.getId());
            UserSubscription current = entitlementService.currentSubscription(user).orElse(null);
            startedAt = current == null ? null : current.getStartedAt();
            cycle = current == null ? null : current.getBillingCycle().name();
            expiresAt = periods.isEmpty() ? null : periods.getFirst().getExpiresAt(); // includes queued renewals
        }
        Map<String, Long> prices = new LinkedHashMap<>();
        catalog.proPrices().forEach((billingCycle, paise) -> prices.put(billingCycle.name(), paise));
        return new BillingDtos.SubscriptionSummary(plan.name(), plan == Plan.PRO ? "ACTIVE" : "NONE", startedAt, expiresAt, false, cycle,
            features, accessMap(definition), prices, catalog.currency(), gateway.isConfigured());
    }

    /* ---------- checkout ---------- */

    public BillingDtos.CheckoutOrderResponse createOrder(User user, BillingDtos.CheckoutOrderRequest request) {
        if (!gateway.isConfigured()) throw BillingException.gatewayUnavailable();
        BillingCycle cycle = BillingCycle.valueOf(request.billingCycle());
        rateLimiter.check(user.getId(), "billing-order", 10);

        long amount = catalog.proPricePaise(cycle);      // server-side price only
        String currency = catalog.currency();
        String receipt = "cai-" + user.getId() + "-" + randomHex(8); // ≤ 40 chars, unique, not a secret

        UserSubscription purchase = transaction.execute(status -> {
            UserSubscription row = new UserSubscription();
            row.setUser(userRepository.getReferenceById(user.getId()));
            row.setPlan(Plan.PRO);
            row.setStatus(SubscriptionStatus.PENDING);
            row.setBillingCycle(cycle);
            row.setAmountPaise(amount);
            row.setCurrency(currency);
            row.setReceipt(receipt);
            Instant now = Instant.now(clock);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            return subscriptionRepository.saveAndFlush(row);
        });

        PaymentGateway.GatewayOrder order;
        try {
            Map<String, String> notes = new LinkedHashMap<>();
            notes.put("careerai_purchase_id", String.valueOf(purchase.getId()));
            notes.put("plan", "PRO");
            notes.put("billing_cycle", cycle.name());
            order = gateway.createOrder(amount, currency, receipt, notes);
        } catch (BillingException ex) {
            cancel(purchase.getId(), "Order could not be created");
            throw ex;
        }
        if (order.amount() != amount || !currency.equalsIgnoreCase(order.currency())) {
            log.warn("RAZORPAY_ORDER_MISMATCH: purchase {} expected {} {} but order has {} {}.", purchase.getId(), amount, currency, order.amount(), order.currency());
            cancel(purchase.getId(), "Order amount mismatch");
            throw BillingException.gatewayUnavailable();
        }
        transaction.executeWithoutResult(status -> subscriptionRepository.findById(purchase.getId()).ifPresent(row -> {
            row.setRazorpayOrderId(order.id());
            row.setUpdatedAt(Instant.now(clock));
        }));
        log.info("CHECKOUT_ORDER_CREATED: user {}, purchase {}, {} {} {}", user.getId(), purchase.getId(), cycle, amount, currency);

        String description = "CareerAI PRO " + (cycle == BillingCycle.ANNUAL ? "Annual" : "Monthly");
        return new BillingDtos.CheckoutOrderResponse(purchase.getId(), order.id(), gateway.publicKeyId(), amount, currency, "PRO", cycle.name(),
            description, new BillingDtos.CheckoutUser(user.getName(), user.getEmail()));
    }

    public BillingDtos.PaymentVerifyResponse verify(User user, BillingDtos.PaymentVerifyRequest request) {
        if (!gateway.isConfigured()) throw BillingException.gatewayUnavailable();
        UserSubscription purchase = subscriptionRepository.findByRazorpayOrderIdAndUserId(request.razorpayOrderId(), user.getId())
            .orElseThrow(() -> new BillingException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "This payment order was not found."));

        if (purchase.getStatus() == SubscriptionStatus.ACTIVE && request.razorpayPaymentId().equals(purchase.getRazorpayPaymentId())) {
            return verified("ALREADY_ACTIVE", "CareerAI PRO is already active for this payment.", purchase, user);
        }
        // Signature over the order id STORED for this user's purchase, never one supplied by the browser alone.
        if (!gateway.verifyPaymentSignature(purchase.getRazorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature())) {
            log.warn("PAYMENT_SIGNATURE_INVALID: user {}, purchase {}.", user.getId(), purchase.getId());
            throw verificationFailed();
        }
        PaymentGateway.GatewayPayment payment = gateway.fetchPayment(request.razorpayPaymentId());
        if (!purchase.getRazorpayOrderId().equals(payment.orderId())) {
            log.warn("PAYMENT_ORDER_MISMATCH: user {}, purchase {}.", user.getId(), purchase.getId());
            throw verificationFailed();
        }
        if (payment.amount() != purchase.getAmountPaise() || !purchase.getCurrency().equalsIgnoreCase(payment.currency())) {
            log.warn("PAYMENT_AMOUNT_MISMATCH: user {}, purchase {}.", user.getId(), purchase.getId());
            throw verificationFailed();
        }
        switch (payment.status()) {
            case "captured" -> { }
            case "failed" -> {
                activation.markFailed(purchase.getRazorpayOrderId(), "Payment failed");
                throw new BillingException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED", "The payment failed. You have not been charged for PRO.");
            }
            default -> {
                // authorized / created: not captured yet — the payment.captured webhook completes activation.
                return new BillingDtos.PaymentVerifyResponse("PENDING", "Payment received. Confirming it with Razorpay — PRO activates in a moment.",
                    null, null, summary(user));
            }
        }

        SubscriptionActivationService.Outcome outcome = activation.activate(purchase.getRazorpayOrderId(), payment.id(), payment.amount(), payment.currency(), "VERIFY");
        return switch (outcome) {
            case ACTIVATED, ALREADY_ACTIVE -> verified(outcome.name(), "Payment successful. CareerAI PRO activated.",
                subscriptionRepository.findById(purchase.getId()).orElse(purchase), user);
            case AMOUNT_MISMATCH -> throw verificationFailed();
            default -> throw new BillingException(HttpStatus.CONFLICT, "PAYMENT_CONFLICT", "This order was already completed with another payment.");
        };
    }

    /* ---------- webhook ---------- */

    /** Verifies the RAW body signature before parsing anything; duplicates are acknowledged without effect. */
    public String handleWebhook(byte[] rawBody, String signature, String eventIdHeader) {
        if (!gateway.isWebhookConfigured()) throw BillingException.gatewayUnavailable();
        if (signature == null || !gateway.verifyWebhookSignature(rawBody, signature)) {
            log.warn("WEBHOOK_SIGNATURE_INVALID: rejected a Razorpay webhook.");
            throw new BillingException(HttpStatus.BAD_REQUEST, "WEBHOOK_SIGNATURE_INVALID", "Invalid webhook signature.");
        }
        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (RuntimeException ex) {
            throw new BillingException(HttpStatus.BAD_REQUEST, "WEBHOOK_INVALID", "Invalid webhook payload.");
        }
        String type = event.path("event").asString("");
        String eventId = eventIdHeader != null && !eventIdHeader.isBlank() ? eventIdHeader.trim() : "sha256:" + sha256(rawBody);
        if (webhookEventRepository.findByEventId(eventId).isPresent()) return "DUPLICATE";

        JsonNode payment = event.path("payload").path("payment").path("entity");
        JsonNode order = event.path("payload").path("order").path("entity");
        String paymentId = text(payment.path("id"));
        String orderId = text(payment.path("order_id"));
        if (orderId == null) orderId = text(order.path("id"));
        long amount = payment.path("amount").isNumber() ? payment.path("amount").asLong() : order.path("amount_paid").asLong(-1);
        String currency = text(payment.path("currency"));
        if (currency == null) currency = text(order.path("currency"));

        try {
            SubscriptionActivationService.Outcome outcome = activation.processWebhook(eventId, type, orderId, paymentId, amount, currency);
            log.info("WEBHOOK_PROCESSED: {} {} → {}", type, eventId.length() > 24 ? eventId.substring(0, 24) : eventId, outcome);
            return outcome.name();
        } catch (DataIntegrityViolationException duplicate) {
            return "DUPLICATE"; // a concurrent delivery of the same event won
        }
    }

    /* ---------- history ---------- */

    public List<BillingDtos.HistoryItem> history(User user) {
        return subscriptionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(s -> new BillingDtos.HistoryItem(s.getId(), s.getPaidAt() != null ? s.getPaidAt() : s.getCreatedAt(), s.getPlan().name(),
                s.getBillingCycle().name(), s.getAmountPaise(), s.getCurrency(), s.getStatus().name(),
                s.getRazorpayPaymentId() == null ? null : SubscriptionActivationService.mask(s.getRazorpayPaymentId()),
                s.getStartedAt(), s.getExpiresAt()))
            .toList();
    }

    /* ---------- helpers ---------- */

    private BillingDtos.PaymentVerifyResponse verified(String result, String message, UserSubscription purchase, User user) {
        return new BillingDtos.PaymentVerifyResponse(result, message, purchase.getStartedAt(), purchase.getExpiresAt(), summary(user));
    }

    private static BillingException verificationFailed() {
        return new BillingException(HttpStatus.BAD_REQUEST, "PAYMENT_VERIFICATION_FAILED", "The payment could not be verified. PRO was not activated.");
    }

    private void cancel(Long purchaseId, String reason) {
        transaction.executeWithoutResult(status -> subscriptionRepository.findById(purchaseId).ifPresent(row -> {
            if (row.getStatus() == SubscriptionStatus.PENDING) {
                row.setStatus(SubscriptionStatus.CANCELLED);
                row.setCancelledAt(Instant.now(clock));
                row.setFailureReason(reason);
                row.setUpdatedAt(Instant.now(clock));
            }
        }));
    }

    private static Map<String, String> accessMap(PlanDefinition definition) {
        Map<String, String> access = new LinkedHashMap<>();
        for (FeatureKey feature : FeatureKey.values()) {
            String level = definition.access().get(feature);
            if (level != null) access.put(feature.name(), level);
        }
        return access;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String value = node.asString("");
        return value.isBlank() ? null : value;
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer).toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
