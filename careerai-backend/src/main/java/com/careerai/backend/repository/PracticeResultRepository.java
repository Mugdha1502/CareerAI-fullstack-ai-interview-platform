package com.careerai.backend.repository;

import com.careerai.backend.entity.PracticeResult;
import com.careerai.backend.repository.projection.DailyActivityRow;
import com.careerai.backend.repository.projection.DailyScoreRow;
import com.careerai.backend.repository.projection.ModuleScoreRow;
import com.careerai.backend.repository.projection.ModuleTopicRow;
import com.careerai.backend.repository.projection.PracticeTotalsRow;
import com.careerai.backend.repository.projection.TopicScoreRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PracticeResultRepository extends JpaRepository<PracticeResult, Long> {

    boolean existsBySessionId(Long sessionId);

    /** Used to award Daily Challenge XP only for the first completion of the day. */
    boolean existsBySessionUserIdAndSessionModuleAndCreatedAtGreaterThanEqual(
        Long userId,
        String module,
        LocalDateTime from
    );

    /** A user's results, newest first, with each session fetched in the same query. */
    @Query(
        value = """
            select r from PracticeResult r
            join fetch r.session s
            where s.user.id = :userId
              and (:module is null or s.module = :module)
            order by r.createdAt desc, r.id desc
            """,
        countQuery = """
            select count(r) from PracticeResult r
            join r.session s
            where s.user.id = :userId
              and (:module is null or s.module = :module)
            """
    )
    Page<PracticeResult> findHistory(
        @Param("userId") Long userId,
        @Param("module") String module,
        Pageable pageable
    );


    /* =====================================================
       AGGREGATES (Performance / Skill Gap / Plan)
       "Scored" results exclude technical notes sessions, whose
       score is subtopics reviewed rather than correct answers.
    ===================================================== */

    // Padded with spaces because it is concatenated between text blocks.
    String SCORED = " (s.questionType is null or s.questionType <> 'notes') ";

    @Query("""
        select new com.careerai.backend.repository.projection.PracticeTotalsRow(
            count(r), sum(r.totalQuestions), avg(r.accuracy))
        from PracticeResult r join r.session s
        where s.user.id = :userId and """ + SCORED)
    PracticeTotalsRow findScoredTotals(@Param("userId") Long userId);

    long countBySessionUserId(Long userId);

    @Query("""
        select new com.careerai.backend.repository.projection.ModuleScoreRow(
            s.module, avg(r.accuracy), count(r), sum(r.totalQuestions), max(r.createdAt))
        from PracticeResult r join r.session s
        where s.user.id = :userId and """ + SCORED + """
        group by s.module
        """)
    List<ModuleScoreRow> findModuleScores(@Param("userId") Long userId);

    @Query("""
        select new com.careerai.backend.repository.projection.TopicScoreRow(
            s.module, s.topic, avg(r.accuracy), count(r), max(r.createdAt))
        from PracticeResult r join r.session s
        where s.user.id = :userId and s.topic is not null and """ + SCORED + """
        group by s.module, s.topic
        """)
    List<TopicScoreRow> findTopicScores(@Param("userId") Long userId);

    @Query("""
        select new com.careerai.backend.repository.projection.DailyScoreRow(
            extract(date from r.createdAt), avg(r.accuracy), count(r))
        from PracticeResult r join r.session s
        where s.user.id = :userId and r.createdAt >= :from and """ + SCORED + """
        group by extract(date from r.createdAt)
        order by extract(date from r.createdAt)
        """)
    List<DailyScoreRow> findDailyScores(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    /** All saved results per day (scored or not); used for streaks and weekly progress. */
    @Query("""
        select new com.careerai.backend.repository.projection.DailyActivityRow(
            extract(date from r.createdAt), count(r))
        from PracticeResult r join r.session s
        where s.user.id = :userId and r.createdAt >= :from
        group by extract(date from r.createdAt)
        order by extract(date from r.createdAt)
        """)
    List<DailyActivityRow> findDailyActivity(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    /**
     * Distinct (module, topic) pairs with a scored result since a time; used to
     * auto-complete plan tasks (reading notes does not count as practising).
     */
    @Query("""
        select distinct new com.careerai.backend.repository.projection.ModuleTopicRow(s.module, s.topic)
        from PracticeResult r join r.session s
        where s.user.id = :userId and r.createdAt >= :from and """ + SCORED)
    List<ModuleTopicRow> findPractisedSince(@Param("userId") Long userId, @Param("from") LocalDateTime from);
}
