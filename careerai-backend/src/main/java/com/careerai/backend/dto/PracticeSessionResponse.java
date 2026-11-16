package com.careerai.backend.dto;

import com.careerai.backend.entity.PracticeSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PracticeSessionResponse {

    private Long id;
    private String module;
    private String topic;
    private String difficulty;
    private String questionType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public static PracticeSessionResponse from(PracticeSession session) {
        return new PracticeSessionResponse(
            session.getId(),
            session.getModule(),
            session.getTopic(),
            session.getDifficulty(),
            session.getQuestionType(),
            session.getStatus().name(),
            session.getStartedAt(),
            session.getCompletedAt()
        );
    }
}
