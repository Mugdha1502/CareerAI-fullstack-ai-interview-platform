package com.careerai.backend.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/** One practice attempt started by a user (e.g. a Java quiz or a coding problem). */
@Entity
@Table(
    name = "practice_sessions",
    indexes = @Index(name = "idx_practice_sessions_user_started", columnList = "user_id, started_at")
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "user")
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_practice_sessions_user"))
    private User user;

    /** Practice module id, e.g. technical, aptitude, coding, sql, daily-challenge. */
    @Column(nullable = false, length = 40)
    private String module;

    @Column(length = 100)
    private String topic;

    @Column(length = 20)
    private String difficulty;

    /** Mode within the module, e.g. quiz, notes, descriptive, or an assessment type. */
    @Column(name = "question_type", length = 60)
    private String questionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PracticeStatus status = PracticeStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }

        if (status == null) {
            status = PracticeStatus.IN_PROGRESS;
        }
    }

    /** Marks the session completed once; later calls keep the original completion time. */
    public void complete() {
        if (status != PracticeStatus.COMPLETED) {
            status = PracticeStatus.COMPLETED;
            completedAt = LocalDateTime.now();
        }
    }
}
