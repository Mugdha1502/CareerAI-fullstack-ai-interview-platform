package com.careerai.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/** One question asked in an interview session, in order. */
@Entity
@Table(
    name = "interview_questions",
    uniqueConstraints = @UniqueConstraint(name = "uk_interview_question_sequence", columnNames = { "session_id", "sequence_number" })
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "session")
public class InterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_interview_questions_session"))
    private InterviewSession session;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    /** TECHNICAL, BEHAVIORAL, HR, RESUME or JOB_SPECIFIC. */
    @Column(name = "question_type", nullable = false, length = 20)
    private String questionType;

    @Column(length = 80)
    private String topic;

    @Column(length = 20)
    private String difficulty;

    @Column(length = 300)
    private String intent;

    /** Key points a good answer covers (JSON); used for fallback scoring. */
    @Column(name = "key_points_json", columnDefinition = "TEXT")
    private String keyPointsJson;

    /** Model answer from the fallback bank (null for AI questions; AI evaluation writes its own). */
    @Column(name = "reference_answer", columnDefinition = "TEXT")
    private String referenceAnswer;

    /** AI or FALLBACK. */
    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    void onCreate() {
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }
    }
}
