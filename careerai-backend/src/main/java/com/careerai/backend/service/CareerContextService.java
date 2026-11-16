package com.careerai.backend.service;

import com.careerai.backend.dto.PerformanceHistoryResponse;
import com.careerai.backend.dto.PlanResponse;
import com.careerai.backend.dto.PlanTaskResponse;
import com.careerai.backend.dto.PracticeHistoryItem;
import com.careerai.backend.dto.SkillGapItemResponse;
import com.careerai.backend.entity.InterviewSession;
import com.careerai.backend.entity.Profile;
import com.careerai.backend.entity.User;
import com.careerai.backend.repository.InterviewSessionRepository;
import com.careerai.backend.repository.ProfileRepository;
import com.careerai.backend.service.SkillGapService.SkillGapAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Compact, structured view of a user's CareerAI progress (profile, skill gap, plan, practice,
 * interviews) for AI prompts and deterministic fallbacks. It summarises; it never sends raw
 * histories or unrelated personal data.
 */
@Service
@RequiredArgsConstructor
public class CareerContextService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM");

    private final ProfileRepository profileRepository;
    private final SkillGapService skillGapService;
    private final PlanService planService;
    private final PerformanceService performanceService;
    private final InterviewSessionRepository interviewSessionRepository;

    public record CareerContext(
        String name,
        String targetRole,
        String trackId,
        boolean targetRoleSelected,
        String careerGoal,
        String experienceLevel,
        List<String> profileSkills,
        Integer readiness,
        int assessedSkills,
        int requiredSkills,
        List<SkillGapItemResponse> strongSkills,
        List<SkillGapItemResponse> weakSkills,
        List<String> unassessedSkills,
        List<String> recentPractice,
        List<PlanTaskResponse> todayTasks,
        String practiceTrend,
        int interviewsCompleted,
        Integer averageInterviewScore,
        Integer lastInterviewScore
    ) {

        /** True when there is real data beyond defaults to personalise with. */
        public boolean hasPersonalData() {
            return targetRoleSelected || assessedSkills > 0 || !recentPractice.isEmpty() || interviewsCompleted > 0;
        }

        public String toPromptText() {
            StringBuilder text = new StringBuilder();
            line(text, "Name", name);
            line(text, "Target role", targetRole + (targetRoleSelected ? "" : " (default; not chosen by the learner)"));
            line(text, "Career goal", careerGoal);
            line(text, "Experience level", experienceLevel);
            line(text, "Profile skills (self-reported)", profileSkills.isEmpty() ? null : String.join(", ", profileSkills));
            line(text, "Readiness", readiness == null ? "not measured yet" : readiness + "% (" + assessedSkills + " of " + requiredSkills + " required skills assessed)");
            line(text, "Strong skills", skillList(strongSkills));
            line(text, "Needs improvement", skillList(weakSkills));
            line(text, "Not assessed yet", unassessedSkills.isEmpty() ? null : String.join(", ", unassessedSkills));
            line(text, "Recent practice", recentPractice.isEmpty() ? "none yet" : String.join("; ", recentPractice));
            line(text, "Practice trend", practiceTrend);
            line(text, "Today's plan", todayTasks.isEmpty() ? null : String.join("; ",
                todayTasks.stream().map(task -> task.title() + (task.completed() ? " (done)" : "")).toList()));
            line(text, "Mock interviews", interviewsCompleted == 0 ? "none completed"
                : interviewsCompleted + " completed, average " + averageInterviewScore + "%, last " + lastInterviewScore + "%");
            return text.toString().strip();
        }

        private static String skillList(List<SkillGapItemResponse> skills) {
            if (skills.isEmpty()) return null;
            return String.join(", ", skills.stream()
                .map(skill -> skill.score() == null ? skill.name() : skill.name() + " " + skill.score() + "%")
                .toList());
        }

        private static void line(StringBuilder text, String label, String value) {
            if (value != null && !value.isBlank()) {
                text.append(label).append(": ").append(value).append('\n');
            }
        }
    }

    @Transactional(readOnly = true)
    public CareerContext build(User user) {
        Profile profile = profileRepository.findByUserId(user.getId()).orElse(null);
        SkillGapAnalysis gap = skillGapService.analyze(user, null);
        PlanResponse plan = planService.getPlan(user);

        List<SkillGapItemResponse> strong = gap.items().stream()
            .filter(item -> "STRONG".equals(item.status()) || "GOOD".equals(item.status()))
            .limit(5)
            .toList();
        List<SkillGapItemResponse> weak = gap.items().stream()
            .filter(item -> "PRIORITY".equals(item.status()) || "IMPROVE".equals(item.status()))
            .sorted(Comparator.comparing(SkillGapItemResponse::score, Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(5)
            .toList();
        List<String> unassessed = gap.items().stream()
            .filter(item -> "UNASSESSED".equals(item.status()) || "SELF_REPORTED".equals(item.status()))
            .map(SkillGapItemResponse::name)
            .limit(6)
            .toList();

        List<String> recent = performanceService.getRecentActivity(user).stream()
            .limit(5)
            .map(CareerContextService::describePractice)
            .toList();

        List<InterviewSession> interviews = interviewSessionRepository
            .findByUserIdAndStatusOrderByCompletedAtDesc(user.getId(), InterviewSession.Status.COMPLETED.name());
        List<Integer> interviewScores = interviews.stream().map(InterviewSession::getOverallScore).filter(Objects::nonNull).toList();

        return new CareerContext(
            user.getName(),
            gap.role().title(),
            gap.role().trackId(),
            gap.targetRoleSelected(),
            profile == null ? null : profile.getCareerGoal(),
            profile == null ? null : profile.getExperienceLevel(),
            profile == null || profile.getSkills() == null ? List.of() : List.copyOf(profile.getSkills()).stream().limit(15).toList(),
            gap.readiness(),
            gap.assessedCount(),
            gap.items().size(),
            strong,
            weak,
            unassessed,
            recent,
            plan.tasks().stream().limit(5).toList(),
            trend(performanceService.getHistory(user, 14)),
            interviewScores.size(),
            interviewScores.isEmpty() ? null : (int) Math.round(interviewScores.stream().mapToInt(Integer::intValue).average().orElse(0)),
            interviewScores.isEmpty() ? null : interviewScores.get(0)
        );
    }

    private static String describePractice(PracticeHistoryItem item) {
        String what = item.getTopic() != null && !item.getTopic().isBlank() ? item.getTopic() : item.getModule();
        String when = item.getCompletedAt() == null ? "" : " (" + item.getCompletedAt().format(DAY) + ")";
        return what + " " + item.getModule() + " " + item.getAccuracy() + "%" + when;
    }

    /** Compares the average score of the last 7 days with the 7 days before. */
    private static String trend(List<PerformanceHistoryResponse> history) {
        List<PerformanceHistoryResponse> scored = history.stream().filter(day -> day.averageScore() != null).toList();
        if (scored.size() < 2) return "not enough recent practice to show a trend";

        int half = scored.size() / 2;
        double earlier = scored.subList(0, half).stream().mapToInt(PerformanceHistoryResponse::averageScore).average().orElse(0);
        double later = scored.subList(half, scored.size()).stream().mapToInt(PerformanceHistoryResponse::averageScore).average().orElse(0);
        double change = later - earlier;

        if (change >= 5) return "improving (recent average " + Math.round(later) + "% vs " + Math.round(earlier) + "% earlier)";
        if (change <= -5) return "declining (recent average " + Math.round(later) + "% vs " + Math.round(earlier) + "% earlier)";
        return "steady around " + Math.round(later) + "%";
    }
}
