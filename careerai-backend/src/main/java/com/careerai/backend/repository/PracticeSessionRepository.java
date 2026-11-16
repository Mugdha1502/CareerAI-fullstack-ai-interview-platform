package com.careerai.backend.repository;

import com.careerai.backend.entity.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    /** Ownership-checked lookup: another user's session is simply "not found". */
    Optional<PracticeSession> findByIdAndUserId(Long id, Long userId);
}
