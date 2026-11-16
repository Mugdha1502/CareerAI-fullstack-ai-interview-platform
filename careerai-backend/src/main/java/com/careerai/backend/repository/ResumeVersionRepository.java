package com.careerai.backend.repository;

import com.careerai.backend.entity.ResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Callers resolve the parent resume by (id, userId) first; versions are then scoped by resume id. */
public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, Long> {

    List<ResumeVersion> findByResumeIdOrderByVersionNumberDesc(Long resumeId);

    Optional<ResumeVersion> findByIdAndResumeId(Long id, Long resumeId);

    long countByResumeId(Long resumeId);

    @Query("select coalesce(max(v.versionNumber), 0) from ResumeVersion v where v.resume.id = :resumeId")
    int findMaxVersionNumber(@Param("resumeId") Long resumeId);

    @Modifying
    @Query("delete from ResumeVersion v where v.resume.id = :resumeId")
    int deleteByResumeId(@Param("resumeId") Long resumeId);
}
