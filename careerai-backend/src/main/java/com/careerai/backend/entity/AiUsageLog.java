package com.careerai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * One AI request made on behalf of a user: which feature, which provider/model, whether
 * it succeeded and how long it took. Prompts, answers and AI output are not stored.
 */
@Entity
@Table(
    name = "ai_usage_logs",
    indexes = @Index(name = "idx_ai_usage_user_created", columnList = "user_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "user")
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ai_usage_user"))
    private User user;

    /** e.g. PRACTICE_QUESTIONS_MCQ, PRACTICE_EVALUATION, SYSTEM_DESIGN_EVALUATION. */
    @Column(nullable = false, length = 50)
    private String feature;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(length = 60)
    private String model;

    @Column(nullable = false)
    private boolean success;

    /** Failure category only (e.g. TIMEOUT, INVALID_RESPONSE); never provider messages. */
    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
