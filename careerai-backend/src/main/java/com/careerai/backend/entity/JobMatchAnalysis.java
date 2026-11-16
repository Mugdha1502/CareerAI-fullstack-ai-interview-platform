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

/** A resume compared with a job description the user pasted in. */
@Entity
@Table(
    name = "job_match_analyses",
    indexes = @Index(name = "idx_job_match_user_created", columnList = "user_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = { "user", "resume", "jobDescription" })
public class JobMatchAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_job_match_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false, foreignKey = @ForeignKey(name = "fk_job_match_resume"))
    private Resume resume;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(length = 120)
    private String company;

    @Column(name = "job_description", nullable = false, columnDefinition = "LONGTEXT")
    private String jobDescription;

    /** RULE_BASED now; an AI engine identifier later. */
    @Column(nullable = false, length = 30)
    private String engine;

    @Column(name = "overall_match_score", nullable = false)
    private int overallMatchScore;

    @Column(name = "keyword_coverage", nullable = false)
    private int keywordCoverage;

    @Column(name = "experience_alignment", nullable = false, length = 20)
    private String experienceAlignment;

    @Column(name = "education_alignment", nullable = false, length = 20)
    private String educationAlignment;

    @Column(name = "matched_skills_json", nullable = false, columnDefinition = "TEXT")
    private String matchedSkillsJson;

    @Column(name = "missing_skills_json", nullable = false, columnDefinition = "TEXT")
    private String missingSkillsJson;

    @Column(name = "recommendations_json", nullable = false, columnDefinition = "TEXT")
    private String recommendationsJson;

    /** Remaining rule-engine details (keywords, notes) shown in the detail view. */
    @Column(name = "details_json", columnDefinition = "LONGTEXT")
    private String detailsJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
