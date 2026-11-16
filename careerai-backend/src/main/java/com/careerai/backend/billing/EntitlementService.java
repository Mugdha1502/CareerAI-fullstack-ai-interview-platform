package com.careerai.backend.billing;

import com.careerai.backend.entity.Plan;
import com.careerai.backend.entity.User;
import com.careerai.backend.entity.UserSubscription;
import com.careerai.backend.repository.UserSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Central plan enforcement. Modules call {@link #consume} at ONE point per protected action:
 *
 *   authenticated user → effective plan (verified ACTIVE subscription covering now, else FREE)
 *   → feature limit (PlanCatalog) → atomic reservation (UsageService) → operation
 *   → on failure the reservation is released, so failed operations never use up the allowance.
 *
 * Expiry is enforced on every check (expiresAt <= now means FREE) — no scheduled job required.
 */
@Service
public class EntitlementService {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);

    /** One reserved use (keep it to release it later, e.g. a voice call that never connected). */
    public record Reservation(Long userId, FeatureKey feature, UsagePeriod period, LocalDate periodStart) {
    }

    private final PlanCatalog catalog;
    private final UsageService usageService;
    private final UserSubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public EntitlementService(PlanCatalog catalog, UsageService usageService, UserSubscriptionRepository subscriptionRepository, Clock billingClock) {
        this.catalog = catalog;
        this.usageService = usageService;
        this.subscriptionRepository = subscriptionRepository;
        this.clock = billingClock;
    }

    /** Effective plan: PRO only while a verified ACTIVE purchase covers the current instant. */
    public Plan currentPlan(User user) {
        return currentSubscription(user).isPresent() ? Plan.PRO : Plan.FREE;
    }

    public Optional<UserSubscription> currentSubscription(User user) {
        List<UserSubscription> current = subscriptionRepository.findCurrent(user.getId(), Instant.now(clock));
        return current.isEmpty() ? Optional.empty() : Optional.of(current.getFirst());
    }

    /** Reserves one use or throws PlanLimitExceededException (HTTP 429 PLAN_LIMIT_REACHED). */
    public Reservation reserve(User user, FeatureKey feature) {
        Plan plan = currentPlan(user);
        PlanDefinition.Limit limit = catalog.definition(plan).limitFor(feature);
        if (limit == null) {
            throw new IllegalArgumentException(feature + " is not a metered feature");
        }
        LocalDate start = usageService.tryReserve(user.getId(), feature, limit.period(), limit.limit());
        if (start == null) {
            PlanDefinition.Limit pro = plan == Plan.PRO ? null : catalog.definition(Plan.PRO).limitFor(feature);
            int used = usageService.used(user.getId(), feature, limit.period());
            log.info("PLAN_LIMIT_REACHED: user {}, feature {}, plan {}, {}/{} {}", user.getId(), feature, plan, used, limit.limit(), limit.period());
            throw new PlanLimitExceededException(feature, plan, limit.limit(), Math.max(used, limit.limit()), limit.period(), pro == null ? null : pro.limit());
        }
        return new Reservation(user.getId(), feature, limit.period(), start);
    }

    public void release(Reservation reservation) {
        if (reservation == null) return;
        usageService.release(reservation.userId(), reservation.feature(), reservation.period(), reservation.periodStart());
    }

    /**
     * Gives back a use counted earlier (e.g. a voice call that never connected). A feature's period is
     * the same on every plan, so the catalog's period identifies the counter.
     */
    public void release(Long userId, FeatureKey feature, LocalDate periodStart) {
        if (periodStart == null) return;
        PlanDefinition.Limit limit = catalog.definition(Plan.FREE).limitFor(feature);
        usageService.release(userId, feature, limit.period(), periodStart);
    }

    /** Runs a protected operation, counting it only if it completes. */
    public <T> T consume(User user, FeatureKey feature, Supplier<T> operation) {
        return consume(user, feature, operation, failure -> false);
    }

    /**
     * Like {@link #consume(User, FeatureKey, Supplier)}, but failures matching {@code countFailure}
     * still count (e.g. a SQL query that executed in the sandbox and returned a syntax error).
     */
    public <T> T consume(User user, FeatureKey feature, Supplier<T> operation, Predicate<RuntimeException> countFailure) {
        Reservation reservation = reserve(user, feature);
        try {
            return operation.get();
        } catch (RuntimeException failure) {
            if (!countFailure.test(failure)) release(reservation);
            throw failure;
        }
    }
}
