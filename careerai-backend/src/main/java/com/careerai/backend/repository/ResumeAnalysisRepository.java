package com.careerai.backend.repository;

import com.careerai.backend.entity.ResumeAnalysis;
import com.careerai.backend.entity.ResumeAnalysisType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, Long> {

    Optional<ResumeAnalysis> findTopByResumeIdAndAnalysisTypeOrderByAnalyzedAtDescIdDesc(Long resumeId, ResumeAnalysisType analysisType);

    @Modifying
    @Query("delete from ResumeAnalysis a where a.resume.id = :resumeId")
    int deleteByResumeId(@Param("resumeId") Long resumeId);
}
