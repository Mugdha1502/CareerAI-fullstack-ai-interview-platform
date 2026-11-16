package com.careerai.backend.entity;

import java.time.LocalDate;
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
 * A text-based mock interview. Questions are generated one at a time (adaptive); the final
 * report is stored as JSON on the session once it is completed.
 * resumeId is a plain id (not a foreign key) so deleting a resume never breaks interview history.
 */
@Entity
@Table(
    name = "interview_sessions",
    indexes = @Index(name = "idx_interview_sessions_user_started", columnList = "user_id, started_at")
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = { "user", "jobDescription", "reportJson", "voiceTranscriptJson" })
public class InterviewSession {

    /** PROCESSING: a voice transcript is being evaluated; CANCELLED: a voice call had no usable answers. */
    public enum Status { IN_PROGRESS, PROCESSING, COMPLETED, CANCELLED }

    public enum Mode { TEXT, VOICE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_interview_sessions_user"))
    private User user;

    /** TECHNICAL, HR, BEHAVIORAL, RESUME, JOB_SPECIFIC or MIXED. */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "target_role", nullable = false, length = 120)
    private String targetRole;

    /** BEGINNER, INTERMEDIATE or ADVANCED. */
    @Column(nullable = false, length = 20)
    private String difficulty;

    @Column(name = "resume_id")
    private Long resumeId;

    @Column(name = "resume_title", length = 120)
    private String resumeTitle;

    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    /** Focus topics passed from Resume Interview Preparation (JSON list), optional. */
    @Column(name = "focus_topics_json", columnDefinition = "TEXT")
    private String focusTopicsJson;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    /** TEXT or VOICE; null on rows created before voice interviews existed (treated as TEXT). */
    @Column(length = 10)
    private String mode = Mode.TEXT.name();

    /** Voice provider (VAPI) for voice interviews. */
    @Column(length = 20)
    private String provider;

    /** The provider's call id, saved when the transcript is ingested (idempotency key). */
    @Column(name = "provider_call_id", length = 120)
    private String providerCallId;

    /** Normalised final voice transcript [{role, text, timestamp}] as JSON. */
    @Column(name = "voice_transcript_json", columnDefinition = "LONGTEXT")
    private String voiceTranscriptJson;

    /** Voice interviews: the usage day the interview was counted in (given back if the call never connected). */
    @Column(name = "usage_period_start")
    private LocalDate usagePeriodStart;

    @Column(nullable = false, length = 20)
    private String status = Status.IN_PROGRESS.name();

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "report_json", columnDefinition = "LONGTEXT")
    private String reportJson;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    public boolean isCompleted() {
        return Status.COMPLETED.name().equals(status);
    }

    public boolean isVoice() {
        return Mode.VOICE.name().equals(mode);
    }

    /** TEXT for rows without a mode. */
    public String modeOrText() {
        return mode == null || mode.isBlank() ? Mode.TEXT.name() : mode;
    }
}
