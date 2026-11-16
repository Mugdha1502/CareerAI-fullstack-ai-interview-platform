package com.careerai.backend.repository;

import com.careerai.backend.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Every lookup is scoped by user id, so one user can never load another user's resume. */
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Resume> findByIdAndUserId(Long id, Long userId);

    Optional<Resume> findTopByUserIdAndPrimaryTrue(Long userId);

    Optional<Resume> findTopByUserIdAndIdNotOrderByUpdatedAtDesc(Long userId, Long excludedId);

    long countByUserId(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Resume r set r.primary = false where r.user.id = :userId and r.primary = true")
    int clearPrimary(@Param("userId") Long userId);
}
