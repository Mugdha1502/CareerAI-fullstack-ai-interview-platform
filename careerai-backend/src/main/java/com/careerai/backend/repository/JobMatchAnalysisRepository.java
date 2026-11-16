package com.careerai.backend.repository;

import com.careerai.backend.entity.JobMatchAnalysis;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Scoped by user id: a user only ever sees their own job matches. */
public interface JobMatchAnalysisRepository extends JpaRepository<JobMatchAnalysis, Long> {

    @EntityGraph(attributePaths = "resume")
    List<JobMatchAnalysis> findTop50ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @EntityGraph(attributePaths = "resume")
    Optional<JobMatchAnalysis> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("delete from JobMatchAnalysis m where m.resume.id = :resumeId")
    int deleteByResumeId(@Param("resumeId") Long resumeId);
}
