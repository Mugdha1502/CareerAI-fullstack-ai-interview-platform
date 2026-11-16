package com.careerai.backend.service;

import com.careerai.backend.dto.SkillGapItemResponse;
import com.careerai.backend.dto.SkillGapResponse;
import com.careerai.backend.entity.Profile;
import com.careerai.backend.entity.User;
import com.careerai.backend.repository.ProfileRepository;
import com.careerai.backend.service.CareerRequirementService.CareerRole;
import com.careerai.backend.service.CareerRequirementService.SkillDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Compares a user's target-role requirements with their measured practice
 * performance and profile. Depends on PerformanceService and
 * CareerRequirementService; PlanService depends on this service (never the reverse).
 */
@Service
@RequiredArgsConstructor
public class SkillGapService {

    public static final String SOURCE_PRACTICE = "PRACTICE";
    public static final String SOURCE_PROFILE = "PROFILE";
    public static final String SOURCE_NONE = "NONE";

    private final PerformanceService performanceService;
    private final CareerRequirementService careerRequirementService;
    private final ProfileRepository profileRepository;

    /**
     * Everything PlanService needs, computed once: the role, each skill's state,
     * readiness and the profile preferences. (Internal; not an API response.)
     */
    public record SkillGapAnalysis(
        CareerRole role,
        boolean targetRoleSelected,
        List<SkillGapItemResponse> items,
        Integer readiness,
        int assessedCount,
        Integer dailyPreparationMinutes,
        String preferredDifficulty,
        PerformanceSnapshot snapshot
    ) {

        public List<SkillGapItemResponse> byStatus(SkillStatus status) {
            return items.stream().filter(item -> item.status().equals(status.name())).toList();
        }

        /** Assessed skills below GOOD, weakest first. */
        public List<SkillGapItemResponse> weakSkills() {
            return items.stream()
                .filter(item -> SkillStatus.valueOf(item.status()).needsWork())
                .sorted(Comparator.comparing(SkillGapItemResponse::score))
                .toList();
        }
    }

    @Transactional(readOnly = true)
    public SkillGapResponse getSkillGap(User user, String trackIdOverride) {
        return toResponse(analyze(user, trackIdOverride));
    }

    /**
     * Role selection: an explicit trackId (preview another track), else the profile's
     * target role, else the default track (flagged as not selected).
     */
    @Transactional(readOnly = true)
    public SkillGapAnalysis analyze(User user, String trackIdOverride) {
        Optional<Profile> profile = profileRepository.findByUserId(user.getId());

        Optional<CareerRole> chosen = careerRequirementService.findByTrackId(trackIdOverride)
            .or(() -> profile.map(Profile::getTargetRole).flatMap(careerRequirementService::findByTitle));

        CareerRole role = chosen.orElseGet(careerRequirementService::defaultRole);
        List<String> profileSkills = profile.map(Profile::getSkills).map(List::copyOf).orElse(List.of());

        PerformanceSnapshot snapshot = performanceService.loadSnapshot(user.getId());

        List<SkillGapItemResponse> items = role.requiredSkills().stream()
            .map(skill -> assess(skill, role, snapshot, profileSkills))
            .toList();

        List<Integer> assessedScores = items.stream()
            .map(SkillGapItemResponse::score)
            .filter(score -> score != null)
            .toList();

        return new SkillGapAnalysis(
            role,
            chosen.isPresent(),
            items,
            PerformanceService.overallReadiness(assessedScores),
            assessedScores.size(),
            profile.map(Profile::getDailyPreparationMinutes).orElse(null),
            profile.map(Profile::getPreferredDifficulty).orElse(null),
            snapshot
        );
    }


    /** Practice score if measured → profile self-report → unassessed. Never invents a score. */
    private SkillGapItemResponse assess(
        SkillDefinition skill,
        CareerRole role,
        PerformanceSnapshot snapshot,
        List<String> profileSkills
    ) {
        Optional<ScoreStat> measured = !skill.isPracticable()
            ? Optional.empty()
            : skill.isModuleLevel()
                ? snapshot.module(skill.module())
                : snapshot.topic(skill.module(), skill.topic());

        String route = careerRequirementService.practiceRoute(skill, role.trackId());

        if (measured.isPresent()) {
            ScoreStat stat = measured.get();
            return new SkillGapItemResponse(
                skill.name(), stat.score(), SkillStatus.fromScore(stat.score()).name(), SOURCE_PRACTICE,
                stat.attempts(), skill.module(), skill.topic(), skill.isPracticable(), route, stat.lastPracticedAt()
            );
        }

        boolean selfReported = careerRequirementService.profileHasSkill(profileSkills, skill);

        return new SkillGapItemResponse(
            skill.name(), null,
            (selfReported ? SkillStatus.SELF_REPORTED : SkillStatus.UNASSESSED).name(),
            selfReported ? SOURCE_PROFILE : SOURCE_NONE,
            0, skill.module(), skill.topic(), skill.isPracticable(), route, null
        );
    }

    private SkillGapResponse toResponse(SkillGapAnalysis analysis) {
        List<SkillGapItemResponse> items = analysis.items();

        List<String> strong = analysis.byStatus(SkillStatus.STRONG).stream().map(SkillGapItemResponse::name).toList();
        List<String> priority = analysis.weakSkills().stream().map(SkillGapItemResponse::name).toList();
        List<String> unassessed = items.stream()
            .filter(item -> !SkillStatus.valueOf(item.status()).isAssessed())
            .map(SkillGapItemResponse::name)
            .toList();

        Recommendation next = recommendNext(analysis);

        return new SkillGapResponse(
            analysis.role().title(),
            analysis.role().trackId(),
            analysis.targetRoleSelected(),
            analysis.readiness(),
            analysis.assessedCount(),
            items.size(),
            strong,
            priority,
            unassessed,
            items,
            next == null ? null : next.item().name(),
            next == null ? null : next.item().practiceRoute(),
            next == null ? null : next.reason()
        );
    }

    record Recommendation(SkillGapItemResponse item, String reason) {
    }

    /**
     * Next skill to work on:
     * 1. weakest assessed skill below GOOD;
     * 2. first practicable skill not yet assessed (self-reported or unassessed);
     * 3. weakest assessed skill overall (everything is GOOD or STRONG);
     * 4. first skill that can only be studied.
     */
    Recommendation recommendNext(SkillGapAnalysis analysis) {
        List<SkillGapItemResponse> weak = analysis.weakSkills();
        if (!weak.isEmpty()) {
            return new Recommendation(weak.get(0), "Your lowest-scoring required skill.");
        }

        Optional<SkillGapItemResponse> unassessedPracticable = analysis.items().stream()
            .filter(item -> item.practicable() && !SkillStatus.valueOf(item.status()).isAssessed())
            .findFirst();
        if (unassessedPracticable.isPresent()) {
            return new Recommendation(unassessedPracticable.get(), "Not assessed yet — practise it to measure your level.");
        }

        Optional<SkillGapItemResponse> weakestAssessed = analysis.items().stream()
            .filter(item -> item.score() != null)
            .min(Comparator.comparing(SkillGapItemResponse::score));
        if (weakestAssessed.isPresent()) {
            return new Recommendation(weakestAssessed.get(), "All assessed skills are on track — keep your weakest one sharp.");
        }

        return analysis.items().stream()
            .findFirst()
            .map(item -> new Recommendation(item, "Start with this skill from your career track."))
            .orElse(null);
    }

    /** Helper for PlanService: practicable skills that still need a first assessment, in role order. */
    public List<SkillGapItemResponse> assessmentCandidates(SkillGapAnalysis analysis) {
        List<SkillGapItemResponse> result = new ArrayList<>();
        for (SkillGapItemResponse item : analysis.items()) {
            if (item.practicable() && !SkillStatus.valueOf(item.status()).isAssessed()) {
                result.add(item);
            }
        }
        return result;
    }
}
