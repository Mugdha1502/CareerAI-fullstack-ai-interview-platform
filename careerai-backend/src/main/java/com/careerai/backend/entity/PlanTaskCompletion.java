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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Completion state of one generated plan task on one day. Plans themselves are
 * generated on request and never stored; only this completion flag is persisted.
 */
@Entity
@Table(
    name = "plan_task_completions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_plan_task_completion",
        columnNames = { "user_id", "task_key", "task_date" }
    )
)
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "user")
public class PlanTaskCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_plan_task_completions_user"))
    private User user;

    @Column(name = "task_key", nullable = false, length = 80)
    private String taskKey;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
