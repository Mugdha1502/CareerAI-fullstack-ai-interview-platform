package com.careerai.backend.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Career profile collected during onboarding and edited on the Profile page. One per user. */
@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "user")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_profiles_user")
    )
    private User user;

    // "CURRENT_ROLE" is a reserved word in MySQL 8, so the column uses another name.
    @Column(name = "current_status", length = 50)
    private String currentRole;

    @Column(length = 150)
    private String education;

    @Column(name = "experience_level", length = 50)
    private String experienceLevel;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "profile_skills",
        joinColumns = @JoinColumn(name = "profile_id"),
        foreignKey = @ForeignKey(name = "fk_profile_skills_profile")
    )
    @OrderColumn(name = "position")
    @Column(name = "skill", nullable = false, length = 60)
    private List<String> skills = new ArrayList<>();

    @Column(name = "target_role", length = 100)
    private String targetRole;

    @Column(name = "career_goal", length = 100)
    private String careerGoal;

    @Column(name = "daily_preparation_minutes")
    private Integer dailyPreparationMinutes;

    @Column(name = "preferred_difficulty", length = 20)
    private String preferredDifficulty;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Profile(User user) {
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Replaces the skill list in place so Hibernate tracks the collection change. */
    public void replaceSkills(List<String> newSkills) {
        skills.clear();
        skills.addAll(newSkills);
    }
}
