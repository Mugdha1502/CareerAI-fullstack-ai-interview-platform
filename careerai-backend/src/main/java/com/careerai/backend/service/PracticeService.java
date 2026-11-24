package com.careerai.backend.service;

import com.careerai.backend.dto.PracticeHistoryItem;
import com.careerai.backend.dto.PracticeHistoryResponse;
import com.careerai.backend.dto.PracticeResultRequest;
import com.careerai.backend.dto.PracticeSessionRequest;
import com.careerai.backend.dto.PracticeSessionResponse;
import com.careerai.backend.entity.PracticeResult;
import com.careerai.backend.entity.PracticeSession;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.InvalidPracticeResultException;
import com.careerai.backend.exception.PracticeResultAlreadySavedException;
import com.careerai.backend.exception.PracticeSessionNotFoundException;
import com.careerai.backend.exception.UserNotFoundException;
import com.careerai.backend.repository.PracticeResultRepository;
import com.careerai.backend.repository.PracticeSessionRepository;
import com.careerai.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Practice sessions and results for the authenticated user.
 * Every method takes the email from the verified JWT; session ids are always
 * checked against that user, so one user can never read or complete another's session.
 */
@Service
@RequiredArgsConstructor
public class PracticeService {

    public static final int RECENT_LIMIT = 5;
    private static final String DAILY_CHALLENGE = "daily-challenge";
    public static final int MAX_PAGE_SIZE = 50;
    private static final long MAX_DURATION_SECONDS = Duration.ofHours(12).toSeconds();

    /**
     * XP per completed module, mirroring the frontend's XP_REWARDS table.
     * Recorded on the result; not yet credited to an XP balance.
     */
    private static final Map<String, Integer> XP_BY_MODULE = Map.of(
        "technical", 25,
        "aptitude", 20,
        "coding", 30,
        "sql", 25,
        "mock-assessment", 50,
        "daily-challenge", 20
    );

    private final UserRepository userRepository;
    private final PracticeSessionRepository sessionRepository;
    private final PracticeResultRepository resultRepository;

    @Transactional
    public PracticeSessionResponse startSession(String email, PracticeSessionRequest request) {
        PracticeSession session = new PracticeSession();
        session.setUser(findUser(email));
        session.setModule(request.getModule());
        session.setTopic(clean(request.getTopic()));
        session.setDifficulty(clean(request.getDifficulty()));
        session.setQuestionType(clean(request.getQuestionType()));

        return PracticeSessionResponse.from(sessionRepository.save(session));
    }

    /** Marks a session completed without a result (e.g. a study session). Idempotent. */
    @Transactional
    public PracticeSessionResponse completeSession(String email, Long sessionId) {
        PracticeSession session = findOwnedSession(email, sessionId);
        session.complete();
        return PracticeSessionResponse.from(session);
    }

    /** Saves the result and completes the session. One result per session. */
    @Transactional
    public PracticeHistoryItem saveResult(String email, PracticeResultRequest request) {
        PracticeSession session = lockOwnedSession(email, request.getSessionId());

        if (resultRepository.existsBySessionId(session.getId())) {
            throw new PracticeResultAlreadySavedException();
        }

        int total = request.getTotalQuestions();
        int correct = request.getCorrectAnswers();
        int incorrect = request.getIncorrectAnswers() != null
            ? request.getIncorrectAnswers()
            : total - correct;

        if (correct > total) {
            throw new InvalidPracticeResultException("Correct answers cannot exceed total questions");
        }

        if (request.getScore() > total) {
            throw new InvalidPracticeResultException("Score cannot exceed total questions");
        }

        if (correct + incorrect > total) {
            throw new InvalidPracticeResultException("Correct and incorrect answers cannot exceed total questions");
        }

        return PracticeHistoryItem.from(resultRepository.save(completeWithResult(session, request.getScore(), total, correct, incorrect)));
    }

    /**
     * A coding or SQL submission scored by the server (hidden tests / result comparison).
     * Attaches the result to the caller's own open session for that module when sessionId is
     * given (duration from the session start), otherwise to a new session. Run/Execute calls never
     * come here: only submissions count towards history, performance, skill gap and the plan.
     */
    public record ScoredSubmission(
        String module,
        String topic,
        String difficulty,
        String questionId,
        String language,
        String executionStatus,
        int passed,
        int total,
        Long sessionId
    ) {
    }

    @Transactional
    public PracticeHistoryItem saveScoredSubmission(String email, ScoredSubmission submission) {
        PracticeSession session;
        if (submission.sessionId() != null) {
            session = lockOwnedSession(email, submission.sessionId());
            if (!submission.module().equals(session.getModule())) {
                throw new InvalidPracticeResultException("This practice session belongs to a different module");
            }
            if (resultRepository.existsBySessionId(session.getId())) {
                throw new PracticeResultAlreadySavedException();
            }
        } else {
            session = new PracticeSession();
            session.setUser(findUser(email));
            session.setModule(submission.module());
            session.setTopic(clean(submission.topic()));
            session.setDifficulty(clean(submission.difficulty()));
            session.setQuestionType(clean(submission.questionId()));
            session = sessionRepository.save(session);
        }

        int total = Math.max(submission.total(), 1);
        int passed = Math.min(Math.max(submission.passed(), 0), total);
        PracticeResult result = completeWithResult(session, passed, total, passed, total - passed);
        result.setQuestionId(submission.questionId());
        result.setLanguage(submission.language());
        result.setExecutionStatus(submission.executionStatus());
        return PracticeHistoryItem.from(resultRepository.save(result));
    }

    /** Completes the session and builds its result; accuracy, duration and XP come from the server. */
    private PracticeResult completeWithResult(PracticeSession session, int score, int total, int correct, int incorrect) {
        session.complete();

        long durationSeconds = Math.min(
            Math.max(Duration.between(session.getStartedAt(), session.getCompletedAt()).toSeconds(), 0),
            MAX_DURATION_SECONDS
        );

        PracticeResult result = new PracticeResult();
        result.setSession(session);
        result.setScore(score);
        result.setTotalQuestions(total);
        result.setCorrectAnswers(correct);
        result.setIncorrectAnswers(incorrect);
        result.setAccuracy((int) Math.round(correct * 100.0 / total));
        result.setDurationSeconds(durationSeconds);
        result.setXpEarned(xpFor(session));
        return result;
    }

    /** Module reward; the Daily Challenge only rewards its first completion each day. */
    private int xpFor(PracticeSession session) {
        int xp = XP_BY_MODULE.getOrDefault(session.getModule(), 0);

        if (DAILY_CHALLENGE.equals(session.getModule())
            && resultRepository.existsBySessionUserIdAndSessionModuleAndCreatedAtGreaterThanEqual(
                session.getUser().getId(), DAILY_CHALLENGE, LocalDate.now().atStartOfDay())) {
            return 0;
        }

        return xp;
    }

    @Transactional(readOnly = true)
    public PracticeHistoryResponse getHistory(String email, String module, int page, int size) {
        User user = findUser(email);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<PracticeResult> results = resultRepository.findHistory(
            user.getId(),
            clean(module),
            PageRequest.of(safePage, safeSize)
        );

        return new PracticeHistoryResponse(
            results.getContent().stream().map(PracticeHistoryItem::from).toList(),
            safePage,
            safeSize,
            results.getTotalElements(),
            results.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<PracticeHistoryItem> getRecent(String email) {
        User user = findUser(email);

        return resultRepository.findHistory(user.getId(), null, PageRequest.of(0, RECENT_LIMIT))
            .getContent()
            .stream()
            .map(PracticeHistoryItem::from)
            .toList();
    }


    private User findUser(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(UserNotFoundException::new);
    }

    private PracticeSession findOwnedSession(String email, Long sessionId) {
        User user = findUser(email);
        return sessionRepository.findByIdAndUserId(sessionId, user.getId())
            .orElseThrow(PracticeSessionNotFoundException::new);
    }

    /** Locked ownership lookup: a double-submitted result waits, then sees the first one (409, not a deadlock). */
    private PracticeSession lockOwnedSession(String email, Long sessionId) {
        User user = findUser(email);
        return sessionRepository.lockByIdAndUserId(sessionId, user.getId())
            .orElseThrow(PracticeSessionNotFoundException::new);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
