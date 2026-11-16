package com.careerai.backend.entity;

import com.careerai.backend.billing.FeatureKey;
import com.careerai.backend.billing.UsagePeriod;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

/**
 * How often a user used one metered feature in one period (a calendar day or month).
 * One row per (user, feature, period type, period start): a new period simply starts a new row,
 * so limits reset without any job. Counts change only through atomic UPDATEs (UsageService).
 */
@Entity
@Table(
    name = "user_usage",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_usage_period", columnNames = { "user_id", "feature_key", "period_type", "period_start" }),
    indexes = @Index(name = "idx_user_usage_user_period", columnList = "user_id, period_start")
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "user")
public class UserUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_usage_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, length = 40)
    private FeatureKey featureKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private UsagePeriod periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "usage_count", nullable = false)
    private int usageCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
