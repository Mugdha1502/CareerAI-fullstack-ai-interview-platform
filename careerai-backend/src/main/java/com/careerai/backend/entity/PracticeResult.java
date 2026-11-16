package com.careerai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/** Final result of a practice session. At most one per session. */
@Entity
@Table(name = "practice_results")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "session")
public class PracticeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "session_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_practice_results_session")
    )
    private PracticeSession session;

    @Column(nullable = false)
    private int score;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "correct_answers", nullable = false)
    private int correctAnswers;

    @Column(name = "incorrect_answers", nullable = false)
    private int incorrectAnswers;

    /** Whole-number percentage 0–100, computed by the server. */
    @Column(nullable = false)
    private int accuracy;

    /** Computed by the server from the session's start and completion times. */
    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    /** Reward for this result, decided by the server (not credited to a balance yet). */
    @Column(name = "xp_earned", nullable = false)
    private int xpEarned;

    /** Coding/SQL submissions: the curated question that was evaluated (null for other modules). */
    @Column(name = "question_id", length = 100)
    private String questionId;

    /** Coding submissions: JAVA, PYTHON, CPP or JAVASCRIPT. */
    @Column(name = "language", length = 20)
    private String language;

    /** Coding/SQL submissions: overall outcome, e.g. PASSED, PARTIAL, COMPILATION_ERROR, CORRECT. */
    @Column(name = "execution_status", length = 30)
    private String executionStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
