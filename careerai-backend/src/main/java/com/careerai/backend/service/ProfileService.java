package com.careerai.backend.service;

import com.careerai.backend.dto.OnboardingRequest;
import com.careerai.backend.dto.ProfileRequest;
import com.careerai.backend.dto.ProfileResponse;
import com.careerai.backend.entity.Profile;
import com.careerai.backend.entity.User;
import com.careerai.backend.exception.UserNotFoundException;
import com.careerai.backend.repository.ProfileRepository;
import com.careerai.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Profile and onboarding for the authenticated user.
 * Every method takes the email from the verified JWT — never a user id from the client.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public ProfileResponse getCurrentProfile(String email) {
        User user = findUser(email);

        return profileRepository.findByUserId(user.getId())
            .map(profile -> ProfileResponse.from(profile, user))
            .orElseGet(() -> ProfileResponse.empty(user));
    }

    @Transactional
    public ProfileResponse updateCurrentProfile(String email, ProfileRequest request) {
        User user = findUser(email);
        Profile profile = findOrCreateProfile(user);

        applyFields(
            profile,
            request.getCurrentRole(),
            request.getEducation(),
            request.getExperienceLevel(),
            request.getSkills(),
            request.getTargetRole(),
            request.getCareerGoal(),
            request.getDailyPreparationMinutes(),
            request.getPreferredDifficulty()
        );

        Profile saved = profileRepository.save(profile);
        return ProfileResponse.from(saved, user);
    }

    @Transactional
    public ProfileResponse completeOnboarding(String email, OnboardingRequest request) {
        User user = findUser(email);
        Profile profile = findOrCreateProfile(user);

        applyFields(
            profile,
            request.getCurrentRole(),
            request.getEducation(),
            request.getExperienceLevel(),
            request.getSkills(),
            request.getTargetRole(),
            request.getCareerGoal(),
            request.getDailyPreparationMinutes(),
            request.getPreferredDifficulty()
        );

        Profile saved = profileRepository.save(profile);

        user.setOnboardingCompleted(true);
        userRepository.save(user);

        return ProfileResponse.from(saved, user);
    }


    private User findUser(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(UserNotFoundException::new);
    }

    private Profile findOrCreateProfile(User user) {
        return profileRepository.findByUserId(user.getId())
            .orElseGet(() -> new Profile(user));
    }

    private void applyFields(
        Profile profile,
        String currentRole,
        String education,
        String experienceLevel,
        List<String> skills,
        String targetRole,
        String careerGoal,
        Integer dailyPreparationMinutes,
        String preferredDifficulty
    ) {
        profile.setCurrentRole(clean(currentRole));
        profile.setEducation(clean(education));
        profile.setExperienceLevel(clean(experienceLevel));
        profile.replaceSkills(cleanSkills(skills));
        profile.setTargetRole(clean(targetRole));
        profile.setCareerGoal(clean(careerGoal));
        profile.setDailyPreparationMinutes(dailyPreparationMinutes);
        profile.setPreferredDifficulty(clean(preferredDifficulty));

        // A change to only the skill collection does not fire @PreUpdate, so stamp it here.
        profile.setUpdatedAt(LocalDateTime.now());
    }

    /** Trimmed value, or null when blank. */
    private static String clean(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Trims, drops blanks and removes case-insensitive duplicates, keeping the first spelling and order. */
    private static List<String> cleanSkills(List<String> skills) {
        Map<String, String> unique = new LinkedHashMap<>();

        if (skills != null) {
            for (String skill : skills) {
                String cleaned = clean(skill);

                if (cleaned != null) {
                    unique.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), cleaned);
                }
            }
        }

        return new ArrayList<>(unique.values());
    }
}
