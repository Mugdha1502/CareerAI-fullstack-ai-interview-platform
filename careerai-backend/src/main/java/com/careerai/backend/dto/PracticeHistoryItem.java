package com.careerai.backend.dto;

import com.careerai.backend.entity.PracticeResult;
import com.careerai.backend.entity.PracticeSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** One completed practice session with its result. Returned by results, history and recent. */
@Getter
@AllArgsConstructor
public class PracticeHistoryItem {

    private Long resultId;
    private Long sessionId;
    private String module;
    private String topic;
    private String difficulty;
    private String questionType;
    private int score;
    private int totalQuestions;
    private int correctAnswers;
    private int incorrectAnswers;
    private int accuracy;
    private long durationSeconds;
    private int xpEarned;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    /** Coding/SQL submissions only (null otherwise). */
    private String questionId;
    private String language;
    private String executionStatus;

    public static PracticeHistoryItem from(PracticeResult result) {
        PracticeSession session = result.getSession();

        return new PracticeHistoryItem(
            result.getId(),
            session.getId(),
            session.getModule(),
            session.getTopic(),
            session.getDifficulty(),
            session.getQuestionType(),
            result.getScore(),
            result.getTotalQuestions(),
            result.getCorrectAnswers(),
            result.getIncorrectAnswers(),
            result.getAccuracy(),
            result.getDurationSeconds(),
            result.getXpEarned(),
            session.getStartedAt(),
            session.getCompletedAt(),
            result.getQuestionId(),
            result.getLanguage(),
            result.getExecutionStatus()
        );
    }
}
