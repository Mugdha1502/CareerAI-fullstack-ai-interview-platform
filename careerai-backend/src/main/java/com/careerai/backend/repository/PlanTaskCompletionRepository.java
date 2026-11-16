package com.careerai.backend.repository;

import com.careerai.backend.entity.PlanTaskCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PlanTaskCompletionRepository extends JpaRepository<PlanTaskCompletion, Long> {

    Optional<PlanTaskCompletion> findByUserIdAndTaskKeyAndTaskDate(Long userId, String taskKey, LocalDate taskDate);

    /** Completed tasks in a date range (e.g. one week) for one user. */
    List<PlanTaskCompletion> findByUserIdAndCompletedTrueAndTaskDateBetween(Long userId, LocalDate from, LocalDate to);
}
