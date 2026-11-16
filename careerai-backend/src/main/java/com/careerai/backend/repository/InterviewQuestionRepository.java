package com.careerai.backend.repository;

import com.careerai.backend.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Callers resolve the session by (id, userId) first. */
public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {

    List<InterviewQuestion> findBySessionIdOrderBySequenceNumberAsc(Long sessionId);

    Optional<InterviewQuestion> findByIdAndSessionId(Long id, Long sessionId);
}
