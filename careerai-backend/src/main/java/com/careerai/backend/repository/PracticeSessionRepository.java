package com.careerai.backend.repository;

import com.careerai.backend.entity.PracticeSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    /** Ownership-checked lookup: another user's session is simply "not found". */
    Optional<PracticeSession> findByIdAndUserId(Long id, Long userId);

    /** Same ownership check, holding a row lock so concurrent result saves for one session queue up. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PracticeSession s where s.id = :id and s.user.id = :userId")
    Optional<PracticeSession> lockByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
