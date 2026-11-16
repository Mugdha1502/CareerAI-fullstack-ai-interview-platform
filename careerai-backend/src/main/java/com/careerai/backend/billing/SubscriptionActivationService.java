package com.careerai.backend.billing;

import com.careerai.backend.entity.PaymentWebhookEvent;
import com.careerai.backend.entity.Plan;
import com.careerai.backend.entity.User;
import com.careerai.backend.entity.UserSubscription;
import com.careerai.backend.repository.PaymentWebhookEventRepository;
import com.careerai.backend.repository.UserRepository;
import com.careerai.backend.repository.UserSubscriptionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Every subscription state change, each in one transaction under row locks so the checkout
 * callback and (possibly duplicated, out-of-order) webhooks for the same order are serialised:
 *
 *  - activate: PENDING/FAILED → ACTIVE exactly once per order. A repeat with the same payment id is
 *    a no-op (ALREADY_ACTIVE); ACTIVE/EXPIRED never move backwards.
 *  - access period: starts at max(now, end of the user's current PRO access) — remaining paid time
 *    is never lost — and lasts one calendar month or year (ZonedDateTime arithmetic in the billing zone).
 *  - markFailed: only PENDING → FAILED (a late "failed" event never downgrades an ACTIVE purchase).
 */
@Service
public class SubscriptionActivationService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionActivationService.class);

    public enum Outcome { ACTIVATED, ALREADY_ACTIVE, CONFLICT, AMOUNT_MISMATCH, UNKNOWN_ORDER, IGNORED, MARKED_FAILED }

    private final UserSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final EntityManager entityManager;
    private final Clock clock;
    private final ZoneId zone;

    public SubscriptionActivationService(UserSubscriptionRepository subscriptionRepository, UserRepository userRepository,
                                         PaymentWebhookEventRepository webhookEventRepository, EntityManager entityManager,
                                         Clock billingClock, ZoneId billingZone) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.entityManager = entityManager;
        this.clock = billingClock;
        this.zone = billingZone;
    }

    @Transactional
    public Outcome activate(String orderId, String paymentId, long amountPaise, String currency, String via) {
        UserSubscription subscription = subscriptionRepository.lockByRazorpayOrderId(orderId).orElse(null);
        if (subscription == null) {
            log.warn("PAYMENT_UNKNOWN_ORDER: {} for order {} (no CareerAI purchase).", via, orderId);
            return Outcome.UNKNOWN_ORDER;
        }
        SubscriptionStatus status = subscription.getStatus();
        if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.EXPIRED) {
            if (paymentId.equals(subscription.getRazorpayPaymentId())) return Outcome.ALREADY_ACTIVE;
            log.warn("PAYMENT_CONFLICT: order {} is already paid by another payment ({}).", orderId, via);
            return Outcome.CONFLICT;
        }
        if (status == SubscriptionStatus.CANCELLED) {
            log.warn("PAYMENT_FOR_CANCELLED_ORDER: order {} ({}).", orderId, via);
            return Outcome.IGNORED;
        }
        if (amountPaise != subscription.getAmountPaise() || currency == null || !currency.equalsIgnoreCase(subscription.getCurrency())) {
            log.warn("PAYMENT_AMOUNT_MISMATCH: order {} expected {} {} but payment has {} {} ({}).",
                orderId, subscription.getAmountPaise(), subscription.getCurrency(), amountPaise, currency, via);
            return Outcome.AMOUNT_MISMATCH;
        }
        if (subscriptionRepository.findByRazorpayPaymentId(paymentId).filter(other -> !other.getId().equals(subscription.getId())).isPresent()) {
            log.warn("PAYMENT_CONFLICT: payment {} already belongs to another purchase ({}).", mask(paymentId), via);
            return Outcome.CONFLICT;
        }

        // Serialise this user's activations so stacked renewals never overlap.
        User user = subscription.getUser();
        entityManager.lock(user, LockModeType.PESSIMISTIC_WRITE);

        Instant now = Instant.now(clock);
        Instant start = now;
        for (UserSubscription active : subscriptionRepository.findActiveUntilAfter(user.getId(), now)) {
            if (!active.getId().equals(subscription.getId()) && active.getExpiresAt().isAfter(start)) start = active.getExpiresAt();
        }
        ZonedDateTime from = start.atZone(zone);
        Instant expires = (subscription.getBillingCycle() == BillingCycle.ANNUAL ? from.plusYears(1) : from.plusMonths(1)).toInstant();

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setRazorpayPaymentId(paymentId);
        subscription.setPaidAt(now);
        subscription.setStartedAt(start);
        subscription.setExpiresAt(expires);
        subscription.setActivatedVia(via);
        subscription.setFailureReason(null);
        subscription.setUpdatedAt(now);
        subscriptionRepository.saveAndFlush(subscription);

        user.setPlan(Plan.PRO); // convenience cache; entitlement always re-checks the period
        log.info("SUBSCRIPTION_ACTIVATED: user {}, purchase {}, {} via {}, access {} → {}", user.getId(), subscription.getId(),
            subscription.getBillingCycle(), via, start, expires);
        return Outcome.ACTIVATED;
    }

    @Transactional
    public Outcome markFailed(String orderId, String reason) {
        UserSubscription subscription = subscriptionRepository.lockByRazorpayOrderId(orderId).orElse(null);
        if (subscription == null) return Outcome.UNKNOWN_ORDER;
        if (subscription.getStatus() != SubscriptionStatus.PENDING) return Outcome.IGNORED;
        subscription.setStatus(SubscriptionStatus.FAILED);
        subscription.setFailureReason(truncate(reason));
        subscription.setUpdatedAt(Instant.now(clock));
        log.info("PAYMENT_FAILED: purchase {} (order {}).", subscription.getId(), orderId);
        return Outcome.MARKED_FAILED;
    }

    /**
     * Records the webhook event and applies it in ONE transaction: the unique event_id makes a
     * duplicate delivery fail before any business action, and a failure rolls the record back so
     * Razorpay's retry is processed normally.
     */
    @Transactional
    public Outcome processWebhook(String eventId, String eventType, String orderId, String paymentId, long amountPaise, String currency) {
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setRazorpayOrderId(orderId);
        event.setRazorpayPaymentId(paymentId);
        event.setReceivedAt(Instant.now(clock));
        webhookEventRepository.saveAndFlush(event); // duplicate → DataIntegrityViolationException

        Outcome outcome = switch (eventType) {
            case "payment.captured", "order.paid" -> orderId == null || paymentId == null
                ? Outcome.IGNORED
                : activate(orderId, paymentId, amountPaise, currency, "WEBHOOK");
            case "payment.failed" -> orderId == null ? Outcome.IGNORED : markFailed(orderId, "Payment failed (Razorpay webhook)");
            default -> Outcome.IGNORED;
        };
        event.setOutcome(outcome.name());
        return outcome;
    }

    /** Keeps users.plan (a display cache) in line with the verified entitlement. */
    @Transactional
    public void syncPlanCache(Long userId, Plan effective) {
        userRepository.findById(userId).ifPresent(user -> {
            Plan cached = user.getPlan() == Plan.PRO || user.getPlan() == Plan.PREMIUM ? Plan.PRO : Plan.FREE;
            if (cached != effective) user.setPlan(effective);
        });
    }

    @Transactional
    public int expireEndedPeriods() {
        return subscriptionRepository.expireEndedPeriods(Instant.now(clock));
    }

    /** All non-expired ACTIVE periods of a user (current first by expiry). */
    @Transactional(readOnly = true)
    public List<UserSubscription> activePeriods(Long userId) {
        return subscriptionRepository.findActiveUntilAfter(userId, Instant.now(clock));
    }

    static String mask(String id) {
        if (id == null || id.length() < 8) return "****";
        return id.substring(0, 4) + "…" + id.substring(id.length() - 4);
    }

    private static String truncate(String text) {
        if (text == null) return null;
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
