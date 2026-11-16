package com.careerai.backend.billing;

import com.careerai.backend.entity.UserUsage;
import com.careerai.backend.repository.UserRepository;
import com.careerai.backend.repository.UserUsageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent, concurrency-safe usage counters (table user_usage).
 *
 * Reservation = one atomic conditional UPDATE (count = count + 1 WHERE count < limit) in its own
 * short transaction, so two simultaneous requests can never both take the last remaining use and
 * the row lock is never held while the actual AI/execution work runs. A missing row (first use in a
 * period) is inserted with count 1; a concurrent insert of the same row hits the unique key and
 * simply retries the UPDATE. Periods start at the calendar day/month in the billing zone, so a new
 * day or month starts with fresh rows (no reset job).
 */
@Service
public class UsageService {

    private final UserUsageRepository usageRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate newTransaction;
    private final Clock clock;
    private final ZoneId zone;

    public UsageService(UserUsageRepository usageRepository, UserRepository userRepository,
                        PlatformTransactionManager transactionManager, Clock billingClock, ZoneId billingZone) {
        this.usageRepository = usageRepository;
        this.userRepository = userRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = billingClock;
        this.zone = billingZone;
    }

    /** First day of the current period in the billing zone. */
    public LocalDate periodStart(UsagePeriod period) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        return period == UsagePeriod.DAILY ? today : today.withDayOfMonth(1);
    }

    /** Uses in the current period. */
    public int used(Long userId, FeatureKey feature, UsagePeriod period) {
        return usageRepository.findByUserIdAndFeatureKeyAndPeriodTypeAndPeriodStart(userId, feature, period, periodStart(period))
            .map(UserUsage::getUsageCount).orElse(0);
    }

    /** Current-period counts for every metered feature (one query). */
    public Map<FeatureKey, Integer> usedByFeature(Long userId) {
        LocalDate day = periodStart(UsagePeriod.DAILY);
        LocalDate month = periodStart(UsagePeriod.MONTHLY);
        Map<FeatureKey, Integer> used = new EnumMap<>(FeatureKey.class);
        for (UserUsage row : usageRepository.findByUserIdAndPeriodStartIn(userId, List.of(day, month))) {
            LocalDate expected = row.getPeriodType() == UsagePeriod.DAILY ? day : month;
            if (row.getPeriodStart().equals(expected)) used.put(row.getFeatureKey(), row.getUsageCount());
        }
        return used;
    }

    /**
     * Atomically reserves one use if fewer than {@code limit} were used in the current period.
     * Returns the period start the use was counted in, or null when the limit is reached.
     */
    public LocalDate tryReserve(Long userId, FeatureKey feature, UsagePeriod period, int limit) {
        if (limit <= 0) return null;
        LocalDate start = periodStart(period);
        for (int attempt = 0; attempt < 3; attempt++) {
            Integer updated = newTransaction.execute(status ->
                usageRepository.incrementIfBelow(userId, feature, period, start, limit, Instant.now(clock)));
            if (updated != null && updated == 1) return start;

            Boolean exists = newTransaction.execute(status ->
                usageRepository.findByUserIdAndFeatureKeyAndPeriodTypeAndPeriodStart(userId, feature, period, start).isPresent());
            if (Boolean.TRUE.equals(exists)) return null; // the row exists and is at the limit

            try {
                newTransaction.executeWithoutResult(status -> {
                    UserUsage row = new UserUsage();
                    row.setUser(userRepository.getReferenceById(userId));
                    row.setFeatureKey(feature);
                    row.setPeriodType(period);
                    row.setPeriodStart(start);
                    row.setUsageCount(1);
                    Instant now = Instant.now(clock);
                    row.setCreatedAt(now);
                    row.setUpdatedAt(now);
                    usageRepository.saveAndFlush(row);
                });
                return start;
            } catch (DataIntegrityViolationException concurrentInsert) {
                // Another request created the row first: retry the atomic UPDATE.
            }
        }
        return null;
    }

    /** Gives back one reserved use (the operation failed). Safe if the count is already 0. */
    public void release(Long userId, FeatureKey feature, UsagePeriod period, LocalDate periodStart) {
        newTransaction.execute(status -> usageRepository.decrement(userId, feature, period, periodStart, Instant.now(clock)));
    }
}
