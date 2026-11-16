package com.careerai.backend.controller;

import com.careerai.backend.dto.CoachConversationRequest;
import com.careerai.backend.dto.CoachConversationResponse;
import com.careerai.backend.dto.CoachHistoryResponse;
import com.careerai.backend.dto.CoachMessageRequest;
import com.careerai.backend.dto.CoachReplyResponse;
import com.careerai.backend.entity.User;
import com.careerai.backend.service.CoachService;
import com.careerai.backend.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** AI Coach (JWT required). The user always comes from the token; another user's ids give 404. */
@RestController
@RequestMapping("/api/coach/conversations")
@RequiredArgsConstructor
public class CoachController {

    private final CurrentUserService currentUserService;
    private final CoachService coachService;

    @PostMapping
    public ResponseEntity<CoachConversationResponse> create(
        Authentication authentication,
        @Valid @RequestBody(required = false) CoachConversationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(coachService.create(user(authentication), request == null ? null : request.title()));
    }

    @GetMapping
    public ResponseEntity<List<CoachConversationResponse>> list(Authentication authentication) {
        return ResponseEntity.ok(coachService.list(user(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CoachHistoryResponse> history(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(coachService.history(user(authentication), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        coachService.delete(user(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<CoachReplyResponse> send(
        Authentication authentication,
        @PathVariable Long id,
        @Valid @RequestBody CoachMessageRequest request
    ) {
        return ResponseEntity.ok(coachService.sendMessage(user(authentication), id, request.message()));
    }

    private User user(Authentication authentication) {
        return currentUserService.require(authentication.getName());
    }
}
