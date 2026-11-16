package com.careerai.backend.controller;

import com.careerai.backend.dto.SimliSessionRequest;
import com.careerai.backend.dto.SimliSessionResponse;
import com.careerai.backend.service.CurrentUserService;
import com.careerai.backend.service.SimliAvatarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Talking-avatar sessions for voice interviews (JWT required). Another user's interview ids give 404. */
@RestController
@RequestMapping("/api/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final CurrentUserService currentUserService;
    private final SimliAvatarService simliAvatarService;

    @PostMapping("/simli/session")
    public ResponseEntity<SimliSessionResponse> simliSession(Authentication authentication, @Valid @RequestBody SimliSessionRequest request) {
        return ResponseEntity.ok(simliAvatarService.createSession(currentUserService.require(authentication.getName()), request));
    }
}
