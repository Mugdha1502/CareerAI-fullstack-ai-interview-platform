package com.careerai.backend.repository;

import com.careerai.backend.billing.FeatureKey;
import com.careerai.backend.billing.UsagePeriod;
import com.careerai.backend.entity.UserUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserUsageRepository extends JpaRepository<UserUsage, Long> {

    Optional<UserUsage> findByUserIdAndFeatureKeyAndPeriodTypeAndPeriodStart(Long userId, FeatureKey featureKey, UsagePeriod periodType, LocalDate periodStart);

    List<UserUsage> findByUserIdAndPeriodStartIn(Long userId, List<LocalDate> periodStarts);

    /**
     * Atomic check-and-increment: the row lock taken by UPDATE serialises concurrent requests, so
     * the count can never pass the limit. Returns 1 when a use was reserved, 0 at the limit.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update UserUsage u set u.usageCount = u.usageCount + 1, u.updatedAt = :now
        where u.user.id = :userId and u.featureKey = :feature and u.periodType = :period
          and u.periodStart = :start and u.usageCount < :limit
        """)
    int incrementIfBelow(@Param("userId") Long userId, @Param("feature") FeatureKey feature, @Param("period") UsagePeriod period,
                         @Param("start") LocalDate start, @Param("limit") int limit, @Param("now") Instant now);

    /** Atomic decrement used to give back a reservation whose operation failed. Never goes below 0. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update UserUsage u set u.usageCount = u.usageCount - 1, u.updatedAt = :now
        where u.user.id = :userId and u.featureKey = :feature and u.periodType = :period
          and u.periodStart = :start and u.usageCount > 0
        """)
    int decrement(@Param("userId") Long userId, @Param("feature") FeatureKey feature, @Param("period") UsagePeriod period,
                  @Param("start") LocalDate start, @Param("now") Instant now);
}
