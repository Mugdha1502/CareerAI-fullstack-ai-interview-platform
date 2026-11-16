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

/** The candidate's typed answer to one interview question, with its evaluation. */
@Entity
@Table(name = "interview_answers")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = { "question", "answerText", "feedbackJson" })
public class InterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_interview_answers_question"))
    private InterviewQuestion question;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @Column(nullable = false)
    private int score;

    /** {strengths, missingPoints, feedback, idealAnswer, followUpRequired} as JSON. */
    @Column(name = "feedback_json", nullable = false, columnDefinition = "TEXT")
    private String feedbackJson;

    /** AI or FALLBACK. */
    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private LocalDateTime answeredAt;

    @PrePersist
    void onCreate() {
        if (answeredAt == null) {
            answeredAt = LocalDateTime.now();
        }
    }
}
