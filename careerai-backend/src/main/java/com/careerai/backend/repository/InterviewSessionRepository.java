package com.careerai.backend.repository;

import com.careerai.backend.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Always scoped by user id. */
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    List<InterviewSession> findTop50ByUserIdOrderByStartedAtDesc(Long userId);

    List<InterviewSession> findByUserIdAndStatusOrderByCompletedAtDesc(Long userId, String status);

    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    /**
     * Atomically moves a session from one status to another (e.g. IN_PROGRESS -> PROCESSING).
     * Returns 1 for the single caller that wins, 0 for duplicates: the guard against
     * processing the same voice transcript twice.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update InterviewSession s set s.status = :to where s.id = :id and s.user.id = :userId and s.status = :from")
    int transitionStatus(@Param("id") Long id, @Param("userId") Long userId, @Param("from") String from, @Param("to") String to);
}
