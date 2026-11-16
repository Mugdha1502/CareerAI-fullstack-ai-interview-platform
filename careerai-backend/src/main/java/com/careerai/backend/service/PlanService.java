package com.careerai.backend.service;

import com.careerai.backend.dto.PlanDayResponse;
import com.careerai.backend.dto.PlanResponse;
import com.careerai.backend.dto.PlanTaskResponse;
import com.careerai.backend.dto.SkillGapItemResponse;
import com.careerai.backend.entity.PlanTaskCompletion;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.PlanTaskNotFoundException;
import com.careerai.backend.repository.PlanTaskCompletionRepository;
import com.careerai.backend.repository.projection.DailyActivityRow;
import com.careerai.backend.service.SkillGapService.SkillGapAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generates today's plan from the skill gap, recent practice and profile preferences
 * using deterministic rules. Plans are not stored; only per-day task completion is.
 *
 * Dependency chain: PlanService → SkillGapService → PerformanceService → repositories.
 * TODO (AI later): an AI recommendation service can re-rank or reword these tasks;
 * keep the rule engine as the fallback.
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    public static final int DEFAULT_DAILY_MINUTES = 60;
    public static final String DEFAULT_DIFFICULTY = "Beginner";

    // Task sizes (minutes)
    private static final int PRACTICE_MINUTES = 30;
    private static final int SHORT_PRACTICE_MINUTES = 20;
    private static final int ASSESSMENT_MINUTES = 15;
    private static final int REVIEW_MINUTES = 20;
    private static final int STUDY_MINUTES = 20;
    private static final int DAILY_CHALLENGE_MINUTES = 10;
    private static final int INTERVIEW_MINUTES = 20;

    // Variety limits per rule
    private static final int MAX_WEAK_SKILL_TASKS = 2;
    private static final int MAX_ASSESSMENT_TASKS = 2;
    private static final int MAX_ASSESSMENT_TASKS_NEW_USER = 3;

    private static final Map<String, String> TASK_TYPE_BY_MODULE = Map.of(
        "technical", "TECHNICAL",
        "coding", "CODING",
        "sql", "SQL",
        "aptitude", "APTITUDE",
        "system-design", "SYSTEM_DESIGN",
        "voice", "VOICE"
    );

    private final SkillGapService skillGapService;
    private final PerformanceService performanceService;
    private final PlanTaskCompletionRepository completionRepository;


    @Transactional(readOnly = true)
    public PlanResponse getPlan(User user) {
        return buildPlan(user, LocalDate.now());
    }

    /** Marks a task in today's plan as completed (idempotent). */
    @Transactional
    public PlanResponse setTaskCompleted(User user, String taskKey, boolean completed) {
        LocalDate today = LocalDate.now();

        // Only keys from today's generated plan can be completed.
        boolean inPlan = buildPlan(user, today).tasks().stream().anyMatch(task -> task.id().equals(taskKey));
        if (!inPlan) {
            throw new PlanTaskNotFoundException();
        }

        PlanTaskCompletion completion = completionRepository
            .findByUserIdAndTaskKeyAndTaskDate(user.getId(), taskKey, today)
            .orElseGet(() -> {
                PlanTaskCompletion created = new PlanTaskCompletion();
                created.setUser(user);
                created.setTaskKey(taskKey);
                created.setTaskDate(today);
                return created;
            });

        completion.setCompleted(completed);
        completion.setCompletedAt(completed ? LocalDateTime.now() : null);
        completionRepository.save(completion);

        return buildPlan(user, today);
    }


    /* =====================================================
       PLAN GENERATION
    ===================================================== */

    private PlanResponse buildPlan(User user, LocalDate today) {
        SkillGapAnalysis gap = skillGapService.analyze(user, null);

        int dailyMinutes = gap.dailyPreparationMinutes() != null ? gap.dailyPreparationMinutes() : DEFAULT_DAILY_MINUTES;
        String difficulty = gap.preferredDifficulty() != null ? gap.preferredDifficulty() : DEFAULT_DIFFICULTY;

        List<PlanTaskResponse> candidates = generateCandidates(gap, dailyMinutes);
        List<PlanTaskResponse> selected = fitToBudget(candidates, dailyMinutes);

        // Completion: a manual tick for today, or matching practice saved today.
        Set<String> practisedToday = performanceService.practisedKeysSince(user.getId(), today.atStartOfDay());
        Map<String, Boolean> manual = manualCompletions(user, today);

        List<PlanTaskResponse> tasks = selected.stream()
            .map(task -> withCompletion(task, manual, practisedToday))
            .toList();

        int planned = tasks.stream().mapToInt(PlanTaskResponse::durationMinutes).sum();
        int completed = (int) tasks.stream().filter(PlanTaskResponse::completed).count();

        PlanTaskResponse next = tasks.stream().filter(task -> !task.completed()).findFirst().orElse(null);

        return new PlanResponse(
            today,
            gap.role().title(),
            gap.role().trackId(),
            gap.targetRoleSelected(),
            gap.readiness(),
            gap.assessedCount(),
            gap.items().size(),
            dailyMinutes,
            planned,
            difficulty,
            tasks,
            completed,
            tasks.size(),
            prioritySkills(gap),
            next,
            weeklyProgress(user, today),
            performanceService.currentStreak(user.getId())
        );
    }

    /**
     * Candidate tasks in priority order. Rules:
     * 1. weakest assessed required skills (below GOOD) → HIGH practice tasks;
     * 2. practicable skills with no score yet → assessment tasks (HIGH for brand-new users);
     * 3. coding practice for roles that need it, unless coding is already strong or covered above;
     * 4. one review task for the weakest GOOD skill (STRONG skills never get tasks);
     * 5. a short daily challenge for variety;
     * 6. one study task for a required skill that has no practice module yet;
     * 7. a role-based mock interview once readiness reaches GOOD with at least half the skills assessed.
     */
    private List<PlanTaskResponse> generateCandidates(SkillGapAnalysis gap, int dailyMinutes) {
        Map<String, PlanTaskResponse> tasks = new LinkedHashMap<>();
        boolean newUser = gap.assessedCount() == 0;
        int practiceMinutes = dailyMinutes <= 30 ? SHORT_PRACTICE_MINUTES : PRACTICE_MINUTES;

        // 1. Weak skills
        gap.weakSkills().stream().limit(MAX_WEAK_SKILL_TASKS).forEach(skill -> add(tasks, new PlanTaskResponse(
            "practice-" + slug(skill.name()),
            "Practice " + skill.name(),
            skill.name() + " is at " + skill.score() + "% — your " + (skill.status().equals("PRIORITY") ? "highest priority" : "next focus") + ".",
            taskType(skill), skill.topic(), practiceMinutes, "HIGH", skill.practiceRoute(), false, null
        )));

        // 2. First assessments
        int assessmentLimit = newUser ? MAX_ASSESSMENT_TASKS_NEW_USER : MAX_ASSESSMENT_TASKS;
        skillGapService.assessmentCandidates(gap).stream().limit(assessmentLimit).forEach(skill -> {
            boolean selfReported = SkillStatus.SELF_REPORTED.name().equals(skill.status());
            add(tasks, new PlanTaskResponse(
                "assess-" + slug(skill.name()),
                (selfReported ? "Verify " : "Assess ") + skill.name(),
                selfReported
                    ? "Listed in your profile — a short practice set measures your actual level."
                    : "Not assessed yet — a short practice set gives you a first score.",
                taskType(skill), skill.topic(), ASSESSMENT_MINUTES, newUser ? "HIGH" : "MEDIUM", skill.practiceRoute(), false, null
            ));
        });

        // 3. Coding practice
        if (gap.role().requiresCoding()) {
            SkillGapItemResponse coding = gap.items().stream()
                .filter(item -> "coding".equals(item.module()))
                .findFirst()
                .orElse(null);

            boolean alreadyPlanned = coding != null
                && (tasks.containsKey("practice-" + slug(coding.name())) || tasks.containsKey("assess-" + slug(coding.name())));
            boolean strong = coding != null && SkillStatus.STRONG.name().equals(coding.status());

            if (!alreadyPlanned && !strong) {
                add(tasks, new PlanTaskResponse(
                    "coding-practice", "Coding Practice",
                    "Solve one problem to keep problem-solving sharp for " + gap.role().title() + " interviews.",
                    "CODING", null, practiceMinutes, "MEDIUM", "/practice/coding", false, null
                ));
            }
        }

        // 4. Review the weakest GOOD skill
        gap.byStatus(SkillStatus.GOOD).stream()
            .min((a, b) -> Integer.compare(a.score(), b.score()))
            .ifPresent(skill -> add(tasks, new PlanTaskResponse(
                "review-" + slug(skill.name()),
                "Review " + skill.name(),
                skill.name() + " is at " + skill.score() + "% — a review set can push it to strong.",
                taskType(skill), skill.topic(), REVIEW_MINUTES, "LOW", skill.practiceRoute(), false, null
            )));

        // 5. Daily challenge
        add(tasks, new PlanTaskResponse(
            "daily-challenge", "Daily Challenge",
            "Four mixed questions to keep your streak going.",
            "DAILY_CHALLENGE", null, DAILY_CHALLENGE_MINUTES, "LOW", "/practice/daily-challenge", false, null
        ));

        // 6. Study a skill that cannot be practised yet
        gap.items().stream()
            .filter(item -> !item.practicable() && !SkillStatus.valueOf(item.status()).isAssessed())
            .findFirst()
            .ifPresent(skill -> add(tasks, new PlanTaskResponse(
                "study-" + slug(skill.name()),
                "Study " + skill.name(),
                "Required for " + gap.role().title() + ". Review it on your career track roadmap.",
                "STUDY", null, STUDY_MINUTES, "LOW", skill.practiceRoute(), false, null
            )));

        // 7. Interview practice when ready — and readiness covers at least half the required skills,
        //    so one good quiz does not trigger interview practice on its own.
        boolean enoughCoverage = gap.assessedCount() * 2 >= gap.items().size();
        if (gap.readiness() != null && gap.readiness() >= SkillStatus.GOOD_THRESHOLD && enoughCoverage) {
            add(tasks, new PlanTaskResponse(
                "ai-interview", "AI Interview",
                "Your readiness is " + gap.readiness() + "% — practise a role-based interview.",
                "INTERVIEW", null, INTERVIEW_MINUTES, "LOW", "/ai-interview/setup?type=role", false, null
            ));
        }

        return new ArrayList<>(tasks.values());
    }

    /** Keeps tasks in priority order while they fit the daily minutes; always keeps at least one. */
    private List<PlanTaskResponse> fitToBudget(List<PlanTaskResponse> candidates, int dailyMinutes) {
        List<PlanTaskResponse> selected = new ArrayList<>();
        int used = 0;

        for (PlanTaskResponse task : candidates) {
            if (selected.isEmpty() || used + task.durationMinutes() <= dailyMinutes) {
                selected.add(task);
                used += task.durationMinutes();
            }
        }

        return selected;
    }

    private PlanTaskResponse withCompletion(PlanTaskResponse task, Map<String, Boolean> manual, Set<String> practisedToday) {
        if (Boolean.TRUE.equals(manual.get(task.id()))) {
            return copy(task, true, "MANUAL");
        }

        if (practisedToday.contains(practiceKey(task))) {
            return copy(task, true, "PRACTICE");
        }

        return task;
    }

    /** Key in PerformanceService#practisedKeysSince that would satisfy this task. */
    private String practiceKey(PlanTaskResponse task) {
        String module = switch (task.type()) {
            case "TECHNICAL" -> "technical";
            case "CODING" -> "coding";
            case "SQL" -> "sql";
            case "APTITUDE" -> "aptitude";
            case "SYSTEM_DESIGN" -> "system-design";
            case "VOICE" -> "voice";
            case "DAILY_CHALLENGE" -> "daily-challenge";
            default -> null;
        };

        if (module == null) return "";
        return PerformanceService.key(module, task.topic() != null ? task.topic() : "*");
    }

    private Map<String, Boolean> manualCompletions(User user, LocalDate today) {
        Map<String, Boolean> manual = new HashMap<>();
        for (PlanTaskCompletion completion : completionRepository
            .findByUserIdAndCompletedTrueAndTaskDateBetween(user.getId(), today, today)) {
            manual.put(completion.getTaskKey(), true);
        }
        return manual;
    }

    /** Up to three skills to focus on: weakest assessed first, then the first unassessed ones. */
    private List<SkillGapItemResponse> prioritySkills(SkillGapAnalysis gap) {
        List<SkillGapItemResponse> result = new ArrayList<>(gap.weakSkills());
        for (SkillGapItemResponse skill : skillGapService.assessmentCandidates(gap)) {
            if (result.size() >= 3) break;
            result.add(skill);
        }
        return result.stream().limit(3).toList();
    }

    /** Monday–Sunday of the current week: saved practice results and ticked tasks per day. */
    private List<PlanDayResponse> weeklyProgress(User user, LocalDate today) {
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        Map<LocalDate, Long> sessions = new HashMap<>();
        for (DailyActivityRow row : performanceService.dailyActivity(user.getId(), monday)) {
            sessions.put(row.date(), row.results());
        }

        Map<LocalDate, Long> ticks = new HashMap<>();
        for (PlanTaskCompletion completion : completionRepository
            .findByUserIdAndCompletedTrueAndTaskDateBetween(user.getId(), monday, sunday)) {
            ticks.merge(completion.getTaskDate(), 1L, Long::sum);
        }

        List<PlanDayResponse> days = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = monday.plusDays(offset);
            days.add(new PlanDayResponse(
                date,
                date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                sessions.getOrDefault(date, 0L),
                ticks.getOrDefault(date, 0L),
                date.equals(today)
            ));
        }
        return days;
    }


    /* ---------- helpers ---------- */

    private static void add(Map<String, PlanTaskResponse> tasks, PlanTaskResponse task) {
        tasks.putIfAbsent(task.id(), task);
    }

    private static PlanTaskResponse copy(PlanTaskResponse task, boolean completed, String source) {
        return new PlanTaskResponse(
            task.id(), task.title(), task.description(), task.type(), task.topic(),
            task.durationMinutes(), task.priority(), task.route(), completed, source
        );
    }

    private static String taskType(SkillGapItemResponse skill) {
        return skill.module() == null ? "STUDY" : TASK_TYPE_BY_MODULE.getOrDefault(skill.module(), "TECHNICAL");
    }

    /** "Spring Security" → "spring-security", "C# / .NET" → "c-net". */
    static String slug(String value) {
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    }
}
