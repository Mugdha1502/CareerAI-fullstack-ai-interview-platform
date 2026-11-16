package com.careerai.backend.controller;

import com.careerai.backend.dto.OnboardingRequest;
import com.careerai.backend.dto.ProfileRequest;
import com.careerai.backend.dto.ProfileResponse;
import com.careerai.backend.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Current user's profile. All endpoints require a valid JWT (SecurityConfig:
 * anyRequest().authenticated()); the user is always taken from the token.
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(profileService.getCurrentProfile(authentication.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<ProfileResponse> updateMyProfile(
        Authentication authentication,
        @Valid @RequestBody ProfileRequest request
    ) {
        return ResponseEntity.ok(profileService.updateCurrentProfile(authentication.getName(), request));
    }

    @PostMapping("/onboarding")
    public ResponseEntity<ProfileResponse> completeOnboarding(
        Authentication authentication,
        @Valid @RequestBody OnboardingRequest request
    ) {
        return ResponseEntity.ok(profileService.completeOnboarding(authentication.getName(), request));
    }
}
