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

/** One stored analysis run of a resume. Headline scores are columns; details stay in resultJson. */
@Entity
@Table(
    name = "resume_analyses",
    indexes = @Index(name = "idx_resume_analyses_resume_type", columnList = "resume_id, analysis_type, analyzed_at")
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = { "resume", "resultJson" })
public class ResumeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false, foreignKey = @ForeignKey(name = "fk_resume_analyses_resume"))
    private Resume resume;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 20)
    private ResumeAnalysisType analysisType;

    /** RULE_BASED now; an AI engine identifier later. */
    @Column(nullable = false, length = 30)
    private String engine;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(name = "keyword_score")
    private Integer keywordScore;

    @Column(name = "formatting_score")
    private Integer formattingScore;

    @Column(name = "content_score")
    private Integer contentScore;

    @Column(name = "experience_score")
    private Integer experienceScore;

    @Column(name = "result_json", nullable = false, columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "analyzed_at", nullable = false, updatable = false)
    private LocalDateTime analyzedAt;

    @PrePersist
    void onCreate() {
        if (analyzedAt == null) {
            analyzedAt = LocalDateTime.now();
        }
    }
}
