package com.careerai.backend.repository;

import com.careerai.backend.entity.InterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Long> {

    @Query("select a from InterviewAnswer a join fetch a.question q where q.session.id = :sessionId order by q.sequenceNumber")
    List<InterviewAnswer> findBySessionId(@Param("sessionId") Long sessionId);

    boolean existsByQuestionId(Long questionId);
}
