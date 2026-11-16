package com.careerai.backend.repository;

import com.careerai.backend.billing.SubscriptionStatus;
import com.careerai.backend.entity.UserSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /** PRO periods that cover the given instant (renewals may already be queued for later). */
    @Query("""
        select s from UserSubscription s
        where s.user.id = :userId and s.status = com.careerai.backend.billing.SubscriptionStatus.ACTIVE
          and s.startedAt <= :now and s.expiresAt > :now
        order by s.expiresAt desc
        """)
    List<UserSubscription> findCurrent(@Param("userId") Long userId, @Param("now") Instant now);

    /** ACTIVE periods not yet over (current and queued renewals), latest expiry first. */
    @Query("""
        select s from UserSubscription s
        where s.user.id = :userId and s.status = com.careerai.backend.billing.SubscriptionStatus.ACTIVE
          and s.expiresAt > :now
        order by s.expiresAt desc
        """)
    List<UserSubscription> findActiveUntilAfter(@Param("userId") Long userId, @Param("now") Instant now);

    List<UserSubscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserSubscription> findByRazorpayOrderIdAndUserId(String razorpayOrderId, Long userId);

    Optional<UserSubscription> findByRazorpayPaymentId(String razorpayPaymentId);

    /** Row lock for activation: verify callback and webhooks for the same order are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSubscription s where s.razorpayOrderId = :orderId")
    Optional<UserSubscription> lockByRazorpayOrderId(@Param("orderId") String orderId);

    /** Housekeeping only: entitlement checks never depend on this having run. */
    @Modifying
    @Query("""
        update UserSubscription s set s.status = com.careerai.backend.billing.SubscriptionStatus.EXPIRED, s.updatedAt = :now
        where s.status = com.careerai.backend.billing.SubscriptionStatus.ACTIVE and s.expiresAt <= :now
        """)
    int expireEndedPeriods(@Param("now") Instant now);

    long countByStatus(SubscriptionStatus status);
}
