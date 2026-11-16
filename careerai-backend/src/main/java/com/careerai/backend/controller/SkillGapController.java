package com.careerai.backend.controller;

import com.careerai.backend.dto.SkillGapResponse;
import com.careerai.backend.service.CurrentUserService;
import com.careerai.backend.service.SkillGapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill gap of the authenticated user against their target role.
 * Optional trackId previews another career track for the same user (never another user).
 */
@RestController
@RequestMapping("/api/skill-gap")
@RequiredArgsConstructor
public class SkillGapController {

    private final CurrentUserService currentUserService;
    private final SkillGapService skillGapService;

    @GetMapping
    public ResponseEntity<SkillGapResponse> skillGap(
        Authentication authentication,
        @RequestParam(required = false) String trackId
    ) {
        return ResponseEntity.ok(skillGapService.getSkillGap(currentUserService.require(authentication.getName()), trackId));
    }
}
