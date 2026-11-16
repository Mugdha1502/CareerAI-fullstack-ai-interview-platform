package com.careerai.backend.service;

import com.careerai.backend.dto.PerformanceHistoryResponse;
import com.careerai.backend.dto.PerformanceSummaryResponse;
import com.careerai.backend.dto.PracticeHistoryItem;
import com.careerai.backend.dto.SkillPerformanceResponse;
import com.careerai.backend.entity.User;
import com.careerai.backend.repository.PracticeResultRepository;
import com.careerai.backend.repository.projection.DailyActivityRow;
import com.careerai.backend.repository.projection.ModuleScoreRow;
import com.careerai.backend.repository.projection.ModuleTopicRow;
import com.careerai.backend.repository.projection.PracticeTotalsRow;
import com.careerai.backend.repository.projection.TopicScoreRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Performance calculated on request from stored PracticeResults (no snapshot table).
 * Lowest layer of the chain PerformanceService ← SkillGapService ← PlanService;
 * it depends only on repositories.
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {

    /** Modules reported as categories. Daily Challenge and Mock Assessment mix topics, so they only count in totals. */
    public static final List<String> CATEGORY_MODULES =
        List.of("technical", "aptitude", "coding", "sql", "system-design", "voice");

    public static final int DEFAULT_HISTORY_DAYS = 30;
    public static final int MAX_HISTORY_DAYS = 365;
    public static final int RECENT_ACTIVITY_LIMIT = 10;
    private static final int STREAK_LOOKBACK_DAYS = 400;

    private final PracticeResultRepository resultRepository;


    /* =====================================================
       ENDPOINT DATA
    ===================================================== */

    @Transactional(readOnly = true)
    public PerformanceSummaryResponse getSummary(User user) {
        Long userId = user.getId();
        PerformanceSnapshot snapshot = loadSnapshot(userId);
        PracticeTotalsRow totals = resultRepository.findScoredTotals(userId);
        long totalSessions = resultRepository.countBySessionUserId(userId);

        Map<String, Integer> categories = categoryScores(snapshot);

        LocalDateTime lastPracticedAt = snapshot.moduleScores().values().stream()
            .map(ScoreStat::lastPracticedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);

        return new PerformanceSummaryResponse(
            totalSessions > 0,
            overallReadiness(categories.values()),
            categories.get("technical"),
            categories.get("aptitude"),
            categories.get("coding"),
            categories.get("sql"),
            categories.get("system-design"),
            categories.get("voice"),
            (int) categories.values().stream().filter(Objects::nonNull).count(),
            totalSessions,
            totals.questions() == null ? 0 : totals.questions(),
            toScore(totals.averageAccuracy()),
            currentStreak(userId),
            lastPracticedAt
        );
    }

    /** Average score per day over the last {@code days} days, oldest first. Days without practice are omitted. */
    @Transactional(readOnly = true)
    public List<PerformanceHistoryResponse> getHistory(User user, int days) {
        int window = Math.min(Math.max(days, 1), MAX_HISTORY_DAYS);
        LocalDateTime from = LocalDate.now().minusDays(window - 1L).atStartOfDay();

        return resultRepository.findDailyScores(user.getId(), from).stream()
            .map(row -> new PerformanceHistoryResponse(row.date(), toScore(row.averageAccuracy()), row.results()))
            .toList();
    }

    /** Average score per practised topic, strongest first. */
    @Transactional(readOnly = true)
    public List<SkillPerformanceResponse> getSkillPerformance(User user) {
        return resultRepository.findTopicScores(user.getId()).stream()
            .filter(row -> CATEGORY_MODULES.contains(row.module()))
            .map(row -> {
                Integer score = toScore(row.averageAccuracy());
                return new SkillPerformanceResponse(
                    row.topic(),
                    row.module(),
                    score,
                    row.results(),
                    SkillStatus.fromScore(score).name(),
                    row.lastPracticedAt()
                );
            })
            .sorted(Comparator.comparing(SkillPerformanceResponse::score, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SkillPerformanceResponse::skill))
            .toList();
    }

    /** Latest completed sessions with results (reuses the practice history item shape). */
    @Transactional(readOnly = true)
    public List<PracticeHistoryItem> getRecentActivity(User user) {
        return resultRepository.findHistory(user.getId(), null, PageRequest.of(0, RECENT_ACTIVITY_LIMIT))
            .getContent()
            .stream()
            .map(PracticeHistoryItem::from)
            .toList();
    }


    /* =====================================================
       SHARED WITH SkillGapService / PlanService
    ===================================================== */

    /** All measured module and topic scores for one user, in two queries. */
    @Transactional(readOnly = true)
    public PerformanceSnapshot loadSnapshot(Long userId) {
        Map<String, ScoreStat> modules = new LinkedHashMap<>();
        for (ModuleScoreRow row : resultRepository.findModuleScores(userId)) {
            modules.put(key(row.module()), new ScoreStat(toScore(row.averageAccuracy()), row.results(), row.lastPracticedAt()));
        }

        Map<String, ScoreStat> topics = new LinkedHashMap<>();
        for (TopicScoreRow row : resultRepository.findTopicScores(userId)) {
            topics.put(key(row.module(), row.topic()), new ScoreStat(toScore(row.averageAccuracy()), row.results(), row.lastPracticedAt()));
        }

        return new PerformanceSnapshot(modules, topics);
    }

    /** Consecutive days with at least one saved result, ending today (or yesterday if not yet practised today). */
    @Transactional(readOnly = true)
    public int currentStreak(Long userId) {
        Set<LocalDate> activeDays = new HashSet<>();
        for (DailyActivityRow row : dailyActivity(userId, LocalDate.now().minusDays(STREAK_LOOKBACK_DAYS))) {
            activeDays.add(row.date());
        }

        LocalDate day = LocalDate.now();
        if (!activeDays.contains(day)) {
            day = day.minusDays(1);
        }

        int streak = 0;
        while (activeDays.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    @Transactional(readOnly = true)
    public List<DailyActivityRow> dailyActivity(Long userId, LocalDate from) {
        return resultRepository.findDailyActivity(userId, from.atStartOfDay());
    }

    /**
     * Keys of what was practised since a time: "module|*" for every module and
     * "module|topic" for topics. Used to auto-complete plan tasks.
     */
    @Transactional(readOnly = true)
    public Set<String> practisedKeysSince(Long userId, LocalDateTime from) {
        Set<String> keys = new HashSet<>();
        for (ModuleTopicRow row : resultRepository.findPractisedSince(userId, from)) {
            keys.add(key(row.module(), "*"));
            if (row.topic() != null) {
                keys.add(key(row.module(), row.topic()));
            }
        }
        return keys;
    }


    /* =====================================================
       FORMULAS — replaceable later (e.g. weighted readiness)
    ===================================================== */

    /** Category score per module; null when the category has not been attempted. */
    public Map<String, Integer> categoryScores(PerformanceSnapshot snapshot) {
        Map<String, Integer> categories = new LinkedHashMap<>();
        for (String module : CATEGORY_MODULES) {
            categories.put(module, snapshot.module(module).map(ScoreStat::score).orElse(null));
        }
        return categories;
    }

    /**
     * Readiness = plain average of the scores that exist. Missing (null) scores are
     * ignored, not counted as zero. Returns null when nothing has been measured.
     * TODO: replace with a weighted model (per-role weights, recency, difficulty).
     */
    public static Integer overallReadiness(Collection<Integer> scores) {
        return scores.stream()
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .average()
            .stream()
            .mapToObj(average -> (int) Math.round(average))
            .findFirst()
            .orElse(null);
    }

    /** Rounds an average accuracy to a 0–100 score; null stays null. */
    public static Integer toScore(Double averageAccuracy) {
        return averageAccuracy == null ? null : (int) Math.round(averageAccuracy);
    }

    static String key(String module) {
        return module == null ? "" : module.trim().toLowerCase(Locale.ROOT);
    }

    static String key(String module, String topic) {
        return key(module) + "|" + (topic == null ? "" : topic.trim().toLowerCase(Locale.ROOT));
    }
}
