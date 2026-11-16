package com.careerai.backend.entity;

import com.careerai.backend.billing.BillingCycle;
import com.careerai.backend.billing.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * One PRO purchase (a paid access period, not an auto-renewing subscription) with its Razorpay
 * order and payment. Entitlement comes from ACTIVE rows whose period covers "now"; users.plan is
 * only a convenience cache. Rows are never deleted, so history and future admin reporting
 * (active PRO users, revenue, failed payments) can be queried from this table.
 */
@Entity
@Table(
    name = "user_subscriptions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_subscriptions_receipt", columnNames = "receipt"),
        @UniqueConstraint(name = "uk_user_subscriptions_order", columnNames = "razorpay_order_id"),
        @UniqueConstraint(name = "uk_user_subscriptions_payment", columnNames = "razorpay_payment_id")
    },
    indexes = {
        @Index(name = "idx_user_subscriptions_user_status", columnList = "user_id, status"),
        @Index(name = "idx_user_subscriptions_status_expires", columnList = "status, expires_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "user")
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_subscriptions_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle;

    /** Server-decided amount in the smallest currency unit (paise for INR). */
    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 40)
    private String receipt;

    @Column(name = "razorpay_order_id", length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** VERIFY (checkout callback) or WEBHOOK: how the payment was confirmed. */
    @Column(name = "activated_via", length = 20)
    private String activatedVia;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
